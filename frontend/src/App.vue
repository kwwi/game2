<template>
  <div class="game-container">
    <div class="header">
      <div class="title">夹 / 挑 棋（房间版）</div>
      <div class="controls" v-if="inRoom">
        <button class="btn" @click="leaveRoom">退出房间</button>
      </div>
    </div>

    <div v-if="!inRoom">
      <div style="display:flex; gap:10px; align-items:center; margin-bottom:12px;">
        <span style="font-size:13px; color:#374151;">昵称：</span>
        <input v-model="myName" style="padding:6px 10px; border:1px solid #d1d5db; border-radius:10px;" />
        <span style="font-size:12px; color:#6b7280;">（本地保存 userId：{{ myUserId.slice(0, 8) }}…）</span>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
        <div style="font-weight:600; color:#111827;">房间列表（自动刷新）</div>
        <button class="btn" @click="fetchRooms">手动刷新</button>
      </div>

      <div style="display:flex; flex-direction:column; gap:8px;">
        <div
          v-for="r in rooms"
          :key="r.roomId"
          style="display:flex; justify-content:space-between; align-items:center; padding:10px 12px; border:1px solid #e5e7eb; border-radius:12px; background:#fff;"
        >
          <div style="display:flex; flex-direction:column;">
            <div style="font-weight:600; color:#111827;">{{ r.name }}</div>
            <div style="font-size:12px; color:#6b7280;">
              黑棋：{{ r.blackPlayerUserId ? r.blackPlayerUserId.slice(0, 6) + '…' : '空' }}
              ｜白棋：{{ r.whitePlayerUserId ? r.whitePlayerUserId.slice(0, 6) + '…' : '空' }}
              ｜在线：{{ (r.participants || []).length }}
            </div>
          </div>
          <div style="display:flex; gap:8px;">
            <button
              class="btn btn-primary"
              @click="joinRoom(r.roomId, r.canJoinAsPlayer ? 'PLAYER' : 'SPECTATOR')"
            >
              {{ r.canJoinAsPlayer ? '加入' : '观战' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="board-wrapper">
      <div class="board">
        <svg class="board-lines" viewBox="0 0 9 11" preserveAspectRatio="none">
          <line
            v-for="(e, idx) in edgeLines"
            :key="idx"
            :x1="e.x1"
            :y1="e.y1"
            :x2="e.x2"
            :y2="e.y2"
          />
        </svg>
        <div
          v-for="cell in cells"
          :key="cell.key"
          class="cell"
          :class="{ usable: cell.usable, blocked: !cell.usable }"
          @click="onCellClick(cell)"
        >
          <div
            v-if="cell.piece"
            class="piece"
            :class="[
              cell.piece === 'BLACK' ? 'black' : 'white',
              selected && selected.x === cell.x && selected.y === cell.y ? 'selected' : ''
            ]"
            @click.stop="onPieceClick(cell)"
          ></div>
        </div>
      </div>

      <div class="info-panel">
        <div style="margin-bottom:10px;">
          <div style="font-weight:600; color:#111827;">{{ currentRoom?.name }}</div>
          <div style="font-size:12px; color:#6b7280;">
            你：{{ me.role }}{{ me.seat ? ' / ' + me.seat : '' }}
          </div>
        </div>

        <div class="turn-indicator">
          <span class="badge" v-if="!winner">
            <span class="badge-dot" :class="currentTurn === 'BLACK' ? 'black' : 'white'"></span>
            <span>{{ currentTurn === 'BLACK' ? '黑棋回合' : '白棋回合' }}</span>
          </span>
          <div v-else class="winner">
            {{ winner === 'BLACK' ? '黑棋胜利 🎉' : '白棋胜利 🎉' }}
          </div>
        </div>

        <div class="hint">
          只有棋手能下棋；观众可聊天与被邀请替换棋手。
        </div>

        <div style="margin-top:10px; display:flex; gap:8px; flex-wrap:wrap;">
          <button class="btn" @click="fetchRoomState">刷新房间</button>
          <button class="btn" @click="openRecords">查看对局记录</button>
        </div>

        <div style="margin-top:12px;">
          <div style="font-weight:600; margin-bottom:6px;">房间用户</div>
          <div style="display:flex; flex-direction:column; gap:6px; max-height:140px; overflow:auto; background:#f9fafb; border-radius:10px; padding:8px 10px;">
            <div
              v-for="u in (currentRoom?.participants || [])"
              :key="u.userId"
              style="display:flex; justify-content:space-between; align-items:center; gap:10px;"
            >
              <div style="font-size:12px; color:#111827;">
                {{ u.name }}（{{ u.role }}{{ u.seat ? '/' + u.seat : '' }}）
              </div>
              <div v-if="me.role === 'PLAYER' && u.userId !== myUserId" style="display:flex; gap:6px;">
                <button
                  class="btn"
                  v-if="me.seat"
                  @click="createInvite(u.userId, me.seat)"
                >
                  邀请替换我
                </button>
              </div>
            </div>
          </div>
        </div>

        <div style="margin-top:12px;">
          <div style="font-weight:600; margin-bottom:6px;">聊天</div>
          <div class="log" style="max-height:160px;">
            <div class="log-entry" v-for="m in chat" :key="m.id">
              <span style="color:#111827; font-weight:600;">{{ m.name }}：</span>{{ m.content }}
            </div>
          </div>
          <div style="display:flex; gap:8px; margin-top:8px;">
            <input
              v-model="chatInput"
              placeholder="输入消息…"
              style="flex:1; padding:8px 10px; border:1px solid #d1d5db; border-radius:10px;"
              @keydown.enter="sendChat"
            />
            <button class="btn btn-primary" @click="sendChat">发送</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="recordsModal" style="position:fixed; inset:0; background:rgba(0,0,0,0.35); display:flex; align-items:center; justify-content:center; padding:20px;">
      <div style="background:white; border-radius:14px; width:min(900px, 100%); max-height:80vh; overflow:auto; padding:14px 16px;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
          <div style="font-weight:600;">对局记录</div>
          <button class="btn" @click="recordsModal=false">关闭</button>
        </div>
        <div style="display:flex; flex-direction:column; gap:8px;">
          <div v-for="r in records" :key="r.id" style="display:flex; justify-content:space-between; align-items:center; border:1px solid #e5e7eb; border-radius:12px; padding:10px 12px;">
            <div style="font-size:12px; color:#111827;">{{ r.id }}</div>
            <div style="display:flex; gap:8px;">
              <button class="btn" @click="viewRecord(r.id)">查看</button>
              <button class="btn btn-primary" v-if="currentRoomId" @click="loadRecord(r.id)">加载到当前房间继续</button>
            </div>
          </div>
        </div>

        <div v-if="recordContent" style="margin-top:12px;">
          <div style="font-weight:600; margin-bottom:6px;">内容预览（JSONL）</div>
          <pre style="white-space:pre-wrap; background:#0b1020; color:#e5e7eb; padding:10px 12px; border-radius:12px; max-height:260px; overflow:auto;">{{ recordContent }}</pre>
        </div>
      </div>
    </div>

    <div v-if="inviteModal" style="position:fixed; inset:0; background:rgba(0,0,0,0.35); display:flex; align-items:center; justify-content:center; padding:20px;">
      <div style="background:white; border-radius:14px; width:min(520px, 100%); padding:14px 16px;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
          <div style="font-weight:600;">替换邀请</div>
          <button class="btn" @click="inviteModal=false">关闭</button>
        </div>
        <div style="font-size:13px; color:#111827; margin-bottom:10px;">
          {{ inviteData?.fromName || inviteData?.fromUserId }} 邀请你替换 TA 的 {{ inviteData?.seat === 'BLACK' ? '黑棋' : '白棋' }}席位下棋。
        </div>
        <div style="display:flex; gap:8px; justify-content:flex-end;">
          <button class="btn" @click="respondInvite('DECLINE')">拒绝</button>
          <button class="btn btn-primary" @click="respondInvite('ACCEPT')">同意</button>
        </div>
      </div>
    </div>
    </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, computed } from 'vue';
import axios from 'axios';

const rooms = ref([]);
const roomsTimer = ref(null);

const currentRoomId = ref(null);
const currentRoom = ref(null);
const me = ref({ role: 'SPECTATOR', seat: null });

const boardState = ref({ pieces: {}, edges: [] });
const currentTurn = ref('BLACK');
const winner = ref(null);
const selected = ref(null);

const chat = ref([]);
const chatInput = ref('');

const recordsModal = ref(false);
const records = ref([]);
const recordContent = ref('');

const myUserId = ref(localStorage.getItem('bg_userId') || crypto.randomUUID());
localStorage.setItem('bg_userId', myUserId.value);
const myName = ref(localStorage.getItem('bg_name') || '玩家');

const inRoom = computed(() => !!currentRoomId.value);

const sse = ref(null);

const inviteModal = ref(false);
const inviteData = ref(null);

function isBlocked() {
  // 新规则下 cc 文件中的所有点都是可用落子点，这里不再有不可用点
  return false;
}

// 棋盘坐标定义：左上角为 (0,0)，垂直向下为 x，水平向右为 y。
// cc 数据中同样使用 [x,y] 这一坐标系：
//   x ∈ [0,10] 表示行索引（0 在最上），y ∈ [0,8] 表示列索引（0 在最左）。
// 因此前端网格大小固定为：rows = 11, cols = 9。
const cells = computed(() => {
  const result = [];
  const pieces = (boardState.value && boardState.value.pieces) || {};
  const maxX = 10; // 行数-1
  const maxY = 8; // 列数-1
  for (let x = 0; x <= maxX; x++) {
    for (let y = 0; y <= maxY; y++) {
      const key = `${x},${y}`;
      const usable = !isBlocked();
      const piece = pieces[key] || null;
      result.push({
        // 直接使用棋盘坐标参与后端交互
        x,
        y,
        key: `${x}-${y}`,
        usable,
        piece
      });
    }
  }
  return result;
});

const edgeLines = computed(() => {
  if (!boardState.value || !Array.isArray(boardState.value.edges)) return [];
  return boardState.value.edges
    .map((s) => {
      const parts = s.split('-');
      if (parts.length !== 2) return null;
      const [x1, y1] = parts[0].split(',').map((v) => parseInt(v, 10));
      const [x2, y2] = parts[1].split(',').map((v) => parseInt(v, 10));
      if (Number.isNaN(x1) || Number.isNaN(y1) || Number.isNaN(x2) || Number.isNaN(y2)) {
        return null;
      }
      // 线段应穿过每个格子中心：中心坐标 = (y + 0.5, x + 0.5)
      return {
        x1: y1 + 0.5,
        y1: x1 + 0.5,
        x2: y2 + 0.5,
        y2: x2 + 0.5
      };
    })
    .filter(Boolean);
});

async function fetchState() {
  // old single-room API no longer used in room mode
}

function normalizeBoard(board) {
  if (!board) return { pieces: {}, edges: [] };
  const pieces = board.pieces && typeof board.pieces === 'object' ? board.pieces : {};
  const edges = Array.isArray(board.edges) ? board.edges : [];
  return { pieces, edges };
}

async function fetchRooms() {
  const res = await axios.get('/api/rooms');
  rooms.value = Array.isArray(res.data) ? res.data : [];
}

async function joinRoom(roomId, mode) {
  localStorage.setItem('bg_name', myName.value);
  const res = await axios.post(`/api/rooms/${roomId}/join`, {
    userId: myUserId.value,
    name: myName.value,
    mode
  });
  currentRoomId.value = roomId;
  me.value = res.data.me;
  currentRoom.value = res.data.room;
  applyState(res.data.state);

  connectSse();
}

async function leaveRoom() {
  if (!currentRoomId.value) return;
  try {
    await axios.post(`/api/rooms/${currentRoomId.value}/leave`, { userId: myUserId.value });
  } catch (e) {
    // ignore
  }
  disconnectSse();
  currentRoomId.value = null;
  currentRoom.value = null;
  selected.value = null;
  chat.value = [];
  await fetchRooms();
}

function applyState(state) {
  if (!state) return;
  if (state.room) currentRoom.value = state.room;
  boardState.value = normalizeBoard(state.board);
  currentTurn.value = state.currentTurn;
  winner.value = state.winner;
}

function connectSse() {
  disconnectSse();
  if (!currentRoomId.value) return;
  const url = `/api/rooms/${encodeURIComponent(currentRoomId.value)}/events?userId=${encodeURIComponent(myUserId.value)}`;
  const es = new EventSource(url);
  sse.value = es;

  es.addEventListener('hello', () => {});

  es.addEventListener('chat', (evt) => {
    try {
      const payload = JSON.parse(evt.data);
      const m = payload.message;
      if (m) {
        chat.value = [...chat.value, m].slice(-200);
      }
    } catch (e) {}
  });

  es.addEventListener('room', (evt) => {
    try {
      const payload = JSON.parse(evt.data);
      if (payload.room) currentRoom.value = payload.room;
    } catch (e) {}
  });

  es.addEventListener('state', (evt) => {
    try {
      const payload = JSON.parse(evt.data);
      applyState(payload);
    } catch (e) {}
  });

  es.addEventListener('invite', (evt) => {
    try {
      const payload = JSON.parse(evt.data);
      if (payload.invite) {
        inviteData.value = payload.invite;
        inviteModal.value = true;
      }
    } catch (e) {}
  });

  es.addEventListener('inviteResolved', () => {});

  es.onerror = () => {
    // browser will auto-reconnect; keep it simple
  };
}

function disconnectSse() {
  if (sse.value) {
    sse.value.close();
    sse.value = null;
  }
}

async function sendChat() {
  const content = chatInput.value.trim();
  if (!content || !currentRoomId.value) return;
  chatInput.value = '';
  await axios.post(`/api/rooms/${currentRoomId.value}/chat`, { userId: myUserId.value, content });
}

async function createInvite(toUserId, seat) {
  if (!currentRoomId.value) return;
  await axios.post(`/api/rooms/${currentRoomId.value}/invites`, {
    fromUserId: myUserId.value,
    toUserId,
    seat
  });
}

async function respondInvite(action) {
  if (!currentRoomId.value || !inviteData.value) return;
  const inviteId = inviteData.value.inviteId;
  await axios.post(`/api/rooms/${currentRoomId.value}/invites/${encodeURIComponent(inviteId)}/respond`, {
    userId: myUserId.value,
    action
  });
  inviteModal.value = false;
  inviteData.value = null;
}

async function openRecords() {
  recordsModal.value = true;
  recordContent.value = '';
  const res = await axios.get('/api/rooms/records');
  records.value = Array.isArray(res.data) ? res.data : [];
}

async function viewRecord(id) {
  const res = await axios.get(`/api/rooms/records/${encodeURIComponent(id)}`);
  recordContent.value = res.data.content || '';
}

async function loadRecord(id) {
  if (!currentRoomId.value) return;
  const res = await axios.post(`/api/records/${encodeURIComponent(id)}/load?roomId=${encodeURIComponent(currentRoomId.value)}`);
  // backend returns state
  if (res.data && res.data.state) {
    applyState(res.data.state);
  } else {
    // fallback: if only snapshot returned
    if (res.data && res.data.board) {
      boardState.value = normalizeBoard(res.data.board);
      currentTurn.value = res.data.currentTurn || currentTurn.value;
    }
  }
}

function onPieceClick(cell) {
  if (!cell.usable || !cell.piece) return;
  if (winner.value) return;
  // only players can move; also must match turn by backend
  if (me.value.role !== 'PLAYER') return;
  const myTurnColor = me.value.seat === 'BLACK' ? 'BLACK' : me.value.seat === 'WHITE' ? 'WHITE' : null;
  if (!myTurnColor || myTurnColor !== currentTurn.value) return;
  if (cell.piece !== currentTurn.value) return;
  selected.value = { x: cell.x, y: cell.y };
}

async function onCellClick(cell) {
  if (!cell.usable || winner.value) return;
  if (!selected.value) return;
  if (cell.x === selected.value.x && cell.y === selected.value.y) {
    selected.value = null;
    return;
  }
  try {
    const res = await axios.post(`/api/rooms/${currentRoomId.value}/move`, {
      userId: myUserId.value,
      fromX: selected.value.x,
      fromY: selected.value.y,
      toX: cell.x,
      toY: cell.y
    });
    if (!res.data.success) {
      return;
    }
    boardState.value = normalizeBoard(res.data.board);
    currentTurn.value = res.data.currentTurn;
    winner.value = res.data.winner;
    selected.value = null;
  } catch (e) {
  }
}

onMounted(() => {
  fetchRooms().catch(() => {});
  const t = setInterval(() => {
    if (!inRoom.value) fetchRooms().catch(() => {});
  }, 2000);
  roomsTimer.value = t;
});

onBeforeUnmount(() => {
  disconnectSse();
  if (roomsTimer.value) clearInterval(roomsTimer.value);
});
</script>

