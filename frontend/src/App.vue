<template>
  <div class="game-container">
    <div class="header">
      <div class="title">上老王山棋牌游戏</div>
      <div class="controls" v-if="inRoom">
        <button class="btn" @click="openSoundSettings">声音设置</button>
        <button class="btn" @click="leaveGameToSpectator">退出对弈到观众席</button>
        <button class="btn" @click="leaveRoom">退出房间</button>
      </div>
    </div>

    <div v-if="!inRoom">
      <div class="lobby-bar" style="display:flex; gap:10px; align-items:center; margin-bottom:12px;">
        <span style="font-size:13px; color:#374151;">昵称：</span>
        <input v-model="myName" style="padding:6px 10px; border:1px solid #d1d5db; border-radius:10px;" />
        <span style="font-size:12px; color:#6b7280;">（本地保存 userId：{{ myUserId.slice(0, 8) }}…）</span>
        <button class="btn" @click="openSoundSettings">声音设置</button>
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
            <button
              v-if="isMaster"
              class="btn"
              @click="adminResetRoom(r.roomId)"
            >
              重置
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="board-wrapper">
      <div class="board" ref="boardEl" :class="{ 'board-disabled': !isMyTurn || displayWinner }">
        <svg class="board-lines" :viewBox="svgViewBox" preserveAspectRatio="none">
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
          :class="{
            usable: cell.usable,
            blocked: !cell.usable,
            'cell-clickable': isCellClickable(cell)
          }"
          @click="onCellClick(cell)"
        >
          <div
            v-if="cell.piece"
            class="piece"
            :class="[
              cell.piece === 'BLACK' ? 'black' : 'white',
              selected && selected.x === cell.x && selected.y === cell.y ? 'selected' : '',
              isPieceClickable(cell) ? 'clickable' : ''
            ]"
            @click.stop="onPieceClick(cell)"
          ></div>
        </div>
      </div>

      <div class="info-panel">
        <div style="margin-bottom:10px;">
          <div style="font-weight:600; color:#111827;">
            {{ currentRoom?.name }}
            <span style="font-size:12px; color:#6b7280; font-weight:500; margin-left:8px;">
              您好，{{ myName }}
            </span>
          </div>
          <div style="font-size:12px; color:#6b7280;">
            你：{{ myRoleText }}
          </div>
        </div>

        <div class="turn-indicator">
          <span class="badge" v-if="!displayWinner">
            <span class="badge-dot" :class="displayCurrentTurn === 'BLACK' ? 'black' : 'white'"></span>
            <span>{{ displayCurrentTurn === 'BLACK' ? '黑棋回合' : '白棋回合' }}</span>
          </span>
          <div v-else class="winner">
            {{ displayWinner === 'BLACK' ? '黑棋胜利 🎉' : '白棋胜利 🎉' }}
          </div>
        </div>

        <div class="hint">
          只有棋手能下棋；观众可聊天与被邀请替换棋手。
        </div>

        <div v-if="replayActive" style="margin-top:10px; display:flex; gap:12px; align-items:center; flex-wrap:wrap;">
          <span style="font-size:12px; color:#6b7280;">回放中（自动播放）</span>
          <div style="display:flex; align-items:center; gap:8px;">
            <span style="font-size:12px; color:#6b7280;">速度</span>
            <input
              type="range"
              min="0.5"
              max="3"
              step="0.1"
              v-model.number="replaySpeed"
              style="width:140px;"
            />
            <span style="font-size:12px; color:#111827; fontWeight:700;">{{ replaySpeed.toFixed(1) }}</span>
          </div>
          <button class="btn btn-primary" @click="stopReplay">退出回放</button>
        </div>

        <div style="margin-top:10px; display:flex; gap:8px; flex-wrap:wrap;">
          <button class="btn" @click="fetchRoomState">刷新房间</button>
          <button class="btn" @click="openRecords">查看对局记录</button>
        </div>

        <div style="margin-top:12px;">
          <div style="font-weight:600; margin-bottom:6px;">房间用户</div>

          <div style="display:flex; flex-direction:column; gap:10px;">
            <div>
              <div style="font-size:12px; color:#6b7280; font-weight:600; margin-bottom:6px;">
                对弈席位
              </div>
              <div class="players-window">
                <div style="display:flex; flex-direction:column; gap:8px;">
                  <div style="display:flex; justify-content:space-between; align-items:center; gap:10px;">
                    <div style="font-size:12px; color:#111827;">
                      黑棋起手：{{ seatNameWithMe('BLACK') || '空缺' }}
                    </div>
                    <button
                      v-if="canJoinSeat('BLACK')"
                      class="btn btn-primary"
                      @click="joinSeat('BLACK')"
                    >
                      加入
                    </button>
                  </div>
                  <div style="display:flex; justify-content:space-between; align-items:center; gap:10px;">
                    <div style="font-size:12px; color:#111827;">
                      白棋：{{ seatNameWithMe('WHITE') || '空缺' }}
                    </div>
                    <button
                      v-if="canJoinSeat('WHITE')"
                      class="btn btn-primary"
                      @click="joinSeat('WHITE')"
                    >
                      加入
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div>
              <div style="font-size:12px; color:#6b7280; font-weight:600; margin-bottom:6px;">
                观众席位
              </div>
              <div class="spectators-window">
                <div
                  v-for="u in (currentRoom?.participants || []).filter((p) => !p.seat)"
                  :key="u.userId"
                  style="display:flex; justify-content:space-between; align-items:center; gap:10px;"
                >
                  <div style="font-size:12px; color:#111827;">
                    {{ participantDisplayName(u) }}（{{ participantRoleText(u) }}）
                  </div>
                </div>
                <div v-if="(currentRoom?.participants || []).filter((p) => !p.seat).length === 0" style="font-size:12px; color:#6b7280;">
                  暂无观众
                </div>
              </div>
            </div>
          </div>
        </div>

        <div
          v-if="takeoverModal"
          style="position:fixed; inset:0; background:rgba(0,0,0,0.35); display:flex; align-items:center; justify-content:center; padding:20px; z-index:1200;"
        >
          <div style="background:white; border-radius:14px; width:min(520px, 100%); padding:14px 16px;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
              <div style="font-weight:600;">空席位接手确认</div>
              <button class="btn" @click="declineTakeover">暂不加入</button>
            </div>
            <div style="font-size:13px; color:#111827; margin-bottom:14px;">
              当前房间有空的棋手席位。是否加入棋手席位接手对局？（不接受则保持观众身份）
            </div>
            <div style="display:flex; gap:8px; justify-content:flex-end;">
              <button class="btn" @click="declineTakeover">不接受</button>
              <button class="btn btn-primary" @click="acceptTakeover">接受加入</button>
            </div>
          </div>
        </div>

        <div style="margin-top:12px;">
          <div style="font-weight:600; margin-bottom:6px;">聊天</div>
          <div class="log chat-log chat-window">
            <div class="log-entry" v-for="m in chat" :key="m.id">
              <span
                class="chat-name"
                :style="{ color: userColor(m.userId), fontWeight: m.userId === myUserId ? 700 : 600 }"
              >
                {{ m.name }}：
              </span>
              <span class="chat-content">{{ m.content }}</span>
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

    <div v-if="recordsModal" class="records-panel">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
        <div style="font-weight:600;">对局记录</div>
        <button class="btn" @click="recordsModal=false">关闭</button>
      </div>

      <div style="display:flex; flex-direction:column; gap:8px;">
        <div v-for="r in records" :key="r.id" style="display:flex; justify-content:space-between; align-items:center; border:1px solid #e5e7eb; border-radius:12px; padding:10px 12px;">
          <div style="font-size:12px; color:#111827;">{{ r.id }}</div>
          <div style="display:flex; gap:8px;">
            <button class="btn" @click="viewRecord(r.id)">查看</button>
            <button class="btn btn-primary" @click="startReplay(r.id)">回放</button>
          </div>
        </div>
      </div>

      <div v-if="recordContent" style="margin-top:12px;">
        <div style="font-weight:600; margin-bottom:6px;">内容预览（JSONL）</div>
        <pre style="white-space:pre-wrap; background:#0b1020; color:#e5e7eb; padding:10px 12px; border-radius:12px; max-height:260px; overflow:auto;">{{ recordContent }}</pre>
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

    <div
      v-if="soundSettingsModal"
      style="position:fixed; inset:0; background:rgba(0,0,0,0.35); display:flex; align-items:center; justify-content:center; padding:20px; z-index:1400;"
    >
      <div style="background:white; border-radius:14px; width:min(520px, 100%); padding:14px 16px;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
          <div style="font-weight:600;">声音设置</div>
          <button class="btn" @click="soundSettingsModal=false">关闭</button>
        </div>

        <div style="display:flex; flex-direction:column; gap:12px;">
          <div>
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
              <div style="font-size:13px; color:#111827; font-weight:600;">音效音量</div>
              <div style="font-size:12px; color:#6b7280;">{{ Math.round(sfxVolume * 100) }}%</div>
            </div>
            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              v-model.number="sfxVolume"
              style="width:100%;"
            />
          </div>

          <div>
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
              <div style="font-size:13px; color:#111827; font-weight:600;">背景音乐音量</div>
              <div style="font-size:12px; color:#6b7280;">{{ Math.round(bgmVolume * 100) }}%</div>
            </div>
            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              v-model.number="bgmVolume"
              style="width:100%;"
            />
            <div style="font-size:12px; color:#6b7280; margin-top:6px;">
              提示：浏览器可能需要你先点击页面一次才能开始播放背景音乐。
            </div>
          </div>

          <div style="display:flex; gap:8px; justify-content:flex-end;">
            <button class="btn" @click="previewSfx">试听音效</button>
            <button class="btn btn-primary" @click="previewBgm">试听背景音乐</button>
          </div>
        </div>
      </div>
    </div>
    </div>
</template>

<script setup>
  import { onMounted, onBeforeUnmount, ref, computed, nextTick, watch } from 'vue';
import axios from 'axios';

const rooms = ref([]);
const roomsTimer = ref(null);

const currentRoomId = ref(localStorage.getItem('bg_roomId') || null);
const currentRoom = ref(null);
const me = ref({ role: 'SPECTATOR', seat: null });

const boardState = ref({ pieces: {}, edges: [] });
const boardEl = ref(null);
const boardMetrics = ref({ cell: 40, gap: 2 });
const currentTurn = ref('BLACK');
const winner = ref(null);
const replayActive = ref(false);
const replayBoardState = ref({ pieces: {}, edges: [] });
const replayTurn = ref('BLACK');
const replayWinner = ref(null);
const replaySpeed = ref(2.4); // 值越大越慢
const selected = ref(null);

// -------- sound effects --------
// 音效资源默认放在：frontend/public/sounds/
// 具体文件名与替换方式见：SOUND_EFFECTS.md
const DEFAULT_SFX_VOLUME = 0.8;
const DEFAULT_BGM_VOLUME = 0.35;
const sfxVolume = ref(parseFloat(localStorage.getItem('bg_sfxVolume') || String(DEFAULT_SFX_VOLUME)));
const bgmVolume = ref(parseFloat(localStorage.getItem('bg_bgmVolume') || String(DEFAULT_BGM_VOLUME)));
function clamp01(n, fallback = 0) {
  const x = typeof n === 'number' ? n : parseFloat(String(n));
  if (!Number.isFinite(x)) return fallback;
  return Math.max(0, Math.min(1, x));
}
sfxVolume.value = clamp01(sfxVolume.value, DEFAULT_SFX_VOLUME);
bgmVolume.value = clamp01(bgmVolume.value, DEFAULT_BGM_VOLUME);

const SOUND_URLS = {
  pickup: '/sounds/pickup.mp3',
  drop: '/sounds/drop.mp3',
  win: '/sounds/win.mp3',
  lose: '/sounds/lose.mp3',
  end: '/sounds/end.mp3',
  bgLobby: '/sounds/bg-lobby.mp3',
  bgRoom: '/sounds/bg-room.mp3'
};

const audioCache = {};
const bgm = ref({
  audio: null,
  desiredUrl: null,
  unlocked: false,
  volume: bgmVolume.value
});

function safePlay(url, volume = sfxVolume.value) {
  if (!url) return;
  try {
    const key = url;
    let a = audioCache[key];
    if (!a) {
      a = new Audio(url);
      a.preload = 'auto';
      a.volume = volume;
      audioCache[key] = a;
    } else {
      a.volume = volume;
    }
    a.currentTime = 0;
    // 一些浏览器需要用户交互触发；失败会抛错，这里吞掉
    const p = a.play();
    if (p && typeof p.catch === 'function') p.catch(() => {});
  } catch (e) {
    // ignore
  }
}

function ensureBgmAudio(url) {
  if (!url) return null;
  const a = bgm.value.audio;
  if (a && bgm.value.desiredUrl === url) return a;
  // stop old
  if (a) {
    try {
      a.pause();
    } catch (e) {}
  }
  const na = new Audio(url);
  na.preload = 'auto';
  na.loop = true;
  na.volume = bgm.value.volume;
  bgm.value.audio = na;
  bgm.value.desiredUrl = url;
  return na;
}

async function playBgmForContext() {
  const url = inRoom.value ? SOUND_URLS.bgRoom : SOUND_URLS.bgLobby;
  const a = ensureBgmAudio(url);
  if (!a) return;
  a.volume = clamp01(bgm.value.volume, DEFAULT_BGM_VOLUME);
  if (!bgm.value.unlocked) return;
  try {
    const p = a.play();
    if (p && typeof p.catch === 'function') await p.catch(() => {});
  } catch (e) {}
}

function stopBgm() {
  const a = bgm.value.audio;
  if (!a) return;
  try {
    a.pause();
  } catch (e) {}
}

function unlockAudioOnce() {
  if (bgm.value.unlocked) return;
  bgm.value.unlocked = true;
  playBgmForContext();
}

const soundSettingsModal = ref(false);
function openSoundSettings() {
  soundSettingsModal.value = true;
}

function previewSfx() {
  safePlay(SOUND_URLS.drop, sfxVolume.value);
}

function previewBgm() {
  unlockAudioOnce();
  playBgmForContext();
}

function playPickupSound() {
  if (replayActive.value) return;
  safePlay(SOUND_URLS.pickup);
}

function playDropSound() {
  if (replayActive.value) return;
  safePlay(SOUND_URLS.drop);
}

function playWinSound() {
  if (replayActive.value) return;
  safePlay(SOUND_URLS.win);
}

function playLoseSound() {
  if (replayActive.value) return;
  safePlay(SOUND_URLS.lose);
}

function playEndSoundForSpectator() {
  if (replayActive.value) return;
  safePlay(SOUND_URLS.end);
}

const chat = ref([]);
const chatInput = ref('');

const recordsModal = ref(false);
const records = ref([]);
const recordContent = ref('');

function createUserId() {
  try {
    if (typeof crypto !== 'undefined' && crypto && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    if (typeof crypto !== 'undefined' && crypto && typeof crypto.getRandomValues === 'function') {
      const bytes = new Uint8Array(16);
      crypto.getRandomValues(bytes);
      // RFC4122 v4 bits
      bytes[6] = (bytes[6] & 0x0f) | 0x40;
      bytes[8] = (bytes[8] & 0x3f) | 0x80;
      const hex = Array.prototype.map.call(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
      return (
        hex.slice(0, 8) + '-' +
        hex.slice(8, 12) + '-' +
        hex.slice(12, 16) + '-' +
        hex.slice(16, 20) + '-' +
        hex.slice(20)
      );
    }
  } catch (e) {
    // ignore and fallback below
  }
  // Last fallback for very old environments.
  return `uid-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

const myUserId = ref(localStorage.getItem('bg_userId') || createUserId());
localStorage.setItem('bg_userId', myUserId.value);
function randomName() {
  const adjs = ['勇敢', '机智', '沉稳', '灵巧', '温柔', '果断', '开心', '冷静', '专注', '可靠'];
  const nouns = ['小虎', '小狼', '小熊', '小猫', '小鹿', '小狐', '小鲸', '小鹰', '小象', '小兔'];
  const adj = adjs[Math.floor(Math.random() * adjs.length)];
  const noun = nouns[Math.floor(Math.random() * nouns.length)];
  const num = String(Math.floor(Math.random() * 900 + 100));
  return `${adj}${noun}${num}`;
}

const myName = ref(localStorage.getItem('bg_name') || randomName());

const inRoom = computed(() => !!currentRoomId.value);
const isMaster = computed(() => String(myName.value || '').trim().toLowerCase() === 'hyuan');

const sse = ref(null);

const inviteModal = ref(false);
const inviteData = ref(null);

const takeoverModal = ref(false);
const takeoverDeclinedForOfferKey = ref(null);

function hashToHue(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) {
    h = (h * 31 + str.charCodeAt(i)) >>> 0;
  }
  return h % 360;
}

function userColor(userId) {
  const base = hashToHue(String(userId || ''));
  return `hsl(${base}, 70%, 40%)`;
}

function isBlocked() {
  // 是否被禁用由是否存在于连线端点决定
  return true;
}

const connectedPoints = computed(() => {
  const set = new Set();
  const edges = (displayBoardState.value && displayBoardState.value.edges) || [];
  for (const s of edges) {
    const parts = String(s).split('-');
    if (parts.length !== 2) continue;
    const [x1, y1] = parts[0].split(',').map((v) => parseInt(v, 10));
    const [x2, y2] = parts[1].split(',').map((v) => parseInt(v, 10));
    if (!Number.isNaN(x1) && !Number.isNaN(y1)) set.add(`${x1},${y1}`);
    if (!Number.isNaN(x2) && !Number.isNaN(y2)) set.add(`${x2},${y2}`);
  }
  return set;
});

function mySeatText(seat) {
  if (seat === 'WHITE') return '白棋棋手';
  if (seat === 'BLACK') return '黑棋棋手';
  return '观众';
}

const myRoleText = computed(() => {
  if (me.value.role !== 'PLAYER') return '观众';
  return mySeatText(me.value.seat);
});

function participantRoleText(u) {
  if (!u) return '';
  if (u.role !== 'PLAYER') return '观众';
  return mySeatText(u.seat);
}

function isMeUserId(userId) {
  return userId != null && String(userId) === String(myUserId.value);
}

function participantDisplayName(u) {
  if (!u) return '';
  const base = u.name || u.userId || '';
  return isMeUserId(u.userId) ? `${base}（我）` : base;
}

const emptySeats = computed(() => {
  if (!currentRoom.value) return [];
  const seats = [];
  if (!currentRoom.value.blackPlayerUserId) seats.push('BLACK');
  if (!currentRoom.value.whitePlayerUserId) seats.push('WHITE');
  return seats;
});

const takeoverOfferKey = computed(() => {
  const b = currentRoom.value?.blackPlayerUserId || 'EMPTY';
  const w = currentRoom.value?.whitePlayerUserId || 'EMPTY';
  return `${b}|${w}`;
});

async function acceptTakeover() {
  if (!currentRoomId.value) return;
  takeoverDeclinedForOfferKey.value = null;
  takeoverModal.value = false;
  await joinRoom(currentRoomId.value, 'PLAYER');
}

async function declineTakeover() {
  if (!currentRoomId.value) return;
  takeoverDeclinedForOfferKey.value = takeoverOfferKey.value;
  takeoverModal.value = false;
  await joinRoom(currentRoomId.value, 'SPECTATOR');
}

function updateBoardMetrics() {
  if (!boardEl.value) return;
  const cs = window.getComputedStyle(boardEl.value);
  const cell = parseFloat(cs.getPropertyValue('--cell'));
  const gap = parseFloat(cs.getPropertyValue('--gap'));
  boardMetrics.value = {
    cell: Number.isFinite(cell) && cell > 0 ? cell : 40,
    gap: Number.isFinite(gap) && gap >= 0 ? gap : 2
  };
}

const svgViewBox = computed(() => {
  const CELL = boardMetrics.value.cell;
  const GAP = boardMetrics.value.gap;
  const ROWS = 11; // x: 0..10
  const COLS = 9; // y: 0..8
  const svgW = COLS * CELL + (COLS - 1) * GAP;
  const svgH = ROWS * CELL + (ROWS - 1) * GAP;
  return `0 0 ${svgW} ${svgH}`;
});

const displayBoardState = computed(() => {
  return replayActive.value ? replayBoardState.value : boardState.value;
});

const displayCurrentTurn = computed(() => {
  return replayActive.value ? replayTurn.value : currentTurn.value;
});

const displayWinner = computed(() => {
  return replayActive.value ? replayWinner.value : winner.value;
});

watch(
  () => winner.value,
  (newWinner, oldWinner) => {
    if (replayActive.value) return;
    if (oldWinner != null) return; // only trigger on null -> non-null
    if (newWinner == null) return;

    const mySeat = me.value.seat;
    const iAmPlayer = me.value.role === 'PLAYER' && mySeat != null;

    if (!iAmPlayer) {
      // 观众：播放对局结束音效
      playEndSoundForSpectator();
      return;
    }

    if (newWinner === mySeat) {
      playWinSound();
    } else {
      playLoseSound();
    }
  }
);

const myTurnColor = computed(() => {
  if (me.value.role !== 'PLAYER') return null;
  if (me.value.seat === 'BLACK') return 'BLACK';
  if (me.value.seat === 'WHITE') return 'WHITE';
  return null;
});

const isMyTurn = computed(() => {
  if (replayActive.value) return false;
  if (displayWinner.value) return false;
  if (!myTurnColor.value) return false;
  return myTurnColor.value === displayCurrentTurn.value;
});

function isPieceClickable(cell) {
  return !!cell?.piece && isMyTurn.value && cell.piece === displayCurrentTurn.value;
}

function isCellClickable(cell) {
  if (!cell?.usable) return false;
  if (!isMyTurn.value) return false;
  if (!selected.value) return false;
  // allow clicking empty cells to try a move; backend validates connectivity
  return !cell.piece;
}

// 棋盘坐标定义：左上角为 (0,0)，垂直向下为 x，水平向右为 y。
// cc 数据中同样使用 [x,y] 这一坐标系：
//   x ∈ [0,10] 表示行索引（0 在最上），y ∈ [0,8] 表示列索引（0 在最左）。
// 因此前端网格大小固定为：rows = 11, cols = 9。
const cells = computed(() => {
  const result = [];
  const pieces = (displayBoardState.value && displayBoardState.value.pieces) || {};
  const maxX = 10; // 行数-1
  const maxY = 8; // 列数-1
  const connected = connectedPoints.value;
  for (let x = 0; x <= maxX; x++) {
    for (let y = 0; y <= maxY; y++) {
      const key = `${x},${y}`;
      // 只渲染（展示落子点圆点/可点击）那些在连线端点集合中的点
      const usable = connected.has(key) || !!pieces[key];
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
  if (!displayBoardState.value || !Array.isArray(displayBoardState.value.edges)) return [];
  const CELL = boardMetrics.value.cell;
  const GAP = boardMetrics.value.gap;
  const ROWS = 11; // x: 0..10
  const COLS = 9; // y: 0..8

  return displayBoardState.value.edges
    .map((s) => {
      const parts = s.split('-');
      if (parts.length !== 2) return null;
      const [x1, y1] = parts[0].split(',').map((v) => parseInt(v, 10));
      const [x2, y2] = parts[1].split(',').map((v) => parseInt(v, 10));
      if (Number.isNaN(x1) || Number.isNaN(y1) || Number.isNaN(x2) || Number.isNaN(y2)) {
        return null;
      }
      return {
        x1: y1 * (CELL + GAP) + CELL / 2,
        y1: x1 * (CELL + GAP) + CELL / 2,
        x2: y2 * (CELL + GAP) + CELL / 2,
        y2: x2 * (CELL + GAP) + CELL / 2
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

async function adminResetRoom(roomId) {
  try {
    await axios.post(`/api/rooms/${encodeURIComponent(roomId)}/admin-reset`, {
      userId: myUserId.value,
      name: myName.value
    });
  } catch (e) {
    // ignore
  }
  await fetchRooms();
}

async function joinRoom(roomId, mode) {
  localStorage.setItem('bg_name', myName.value);
  localStorage.setItem('bg_roomId', roomId);
  localStorage.setItem('bg_mode', mode);
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

  // 只在“用户加入为观众的那一刻”提供接手确认，避免其它人退出/入座引发现有观众的重复弹窗。
  takeoverModal.value = false;
  if (!replayActive.value && mode === 'SPECTATOR' && me.value.role !== 'PLAYER') {
    const seatsEmpty = (res.data.room?.blackPlayerUserId == null || res.data.room?.blackPlayerUserId === '') ||
      (res.data.room?.whitePlayerUserId == null || res.data.room?.whitePlayerUserId === '');
    // 使用接口返回的房间信息计算是否有空席位
    const hasEmptySeat =
      !res.data.room?.blackPlayerUserId || !res.data.room?.whitePlayerUserId;

    const offerKey = takeoverOfferKey.value;
    if (hasEmptySeat && offerKey !== takeoverDeclinedForOfferKey.value) {
      takeoverModal.value = true;
    }
  }
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
  localStorage.removeItem('bg_roomId');
  localStorage.removeItem('bg_mode');
  currentRoom.value = null;
  selected.value = null;
  replayActive.value = false;
  replayBoardState.value = { pieces: {}, edges: [] };
  replayWinner.value = null;
  takeoverModal.value = false;
  chat.value = [];
  await fetchRooms();
}

async function leaveGameToSpectator() {
  if (!currentRoomId.value) return;
  try {
    await axios.post(`/api/rooms/${currentRoomId.value}/leave-game`, { userId: myUserId.value });
  } catch (e) {
    // ignore
  }
  // 留在房间为观众：不要断开 SSE，等待后端推送 room/state
  localStorage.setItem('bg_mode', 'SPECTATOR');
  me.value = { role: 'SPECTATOR', seat: null };
  selected.value = null;
  takeoverModal.value = false;
}

function seatName(seat) {
  const list = currentRoom.value?.participants || [];
  const u = list.find((p) => p.seat === seat);
  return u ? u.name : null;
}

function seatNameWithMe(seat) {
  const list = currentRoom.value?.participants || [];
  const u = list.find((p) => p.seat === seat);
  if (!u) return null;
  return isMeUserId(u.userId) ? `${u.name}（我）` : u.name;
}

function canJoinSeat(seat) {
  if (!currentRoomId.value) return false;
  if (replayActive.value) return false;
  if (me.value.role === 'PLAYER') return false;
  if (seat === 'BLACK') return !currentRoom.value?.blackPlayerUserId;
  if (seat === 'WHITE') return !currentRoom.value?.whitePlayerUserId;
  return false;
}

async function joinSeat(seat) {
  if (!currentRoomId.value) return;
  try {
    await axios.post(`/api/rooms/${currentRoomId.value}/seats/${encodeURIComponent(seat)}/join`, {
      userId: myUserId.value
    });
    // SSE 会推送 room/state；这里不强制刷新
    localStorage.setItem('bg_mode', 'PLAYER');
  } catch (e) {}
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
  try {
    const res = await axios.get(`/api/records/mine?userId=${encodeURIComponent(myUserId.value)}`);
    records.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    // Fallback: old backend doesn't have /api/records/mine, filter client-side.
    const res = await axios.get('/api/rooms/records');
    const all = Array.isArray(res.data) ? res.data : [];
    const myNeedle = `"userId":"${myUserId.value}"`;
    const filtered = [];
    for (const r of all) {
      try {
        const c = await axios.get(`/api/rooms/records/${encodeURIComponent(r.id)}`);
        const content = c.data && c.data.content ? c.data.content : '';
        if (content.includes(myNeedle)) filtered.push(r);
      } catch (e2) {
        // ignore
      }
    }
    records.value = filtered;
  }
}

async function viewRecord(id) {
  const res = await axios.get(`/api/rooms/records/${encodeURIComponent(id)}`);
  recordContent.value = res.data.content || '';
}

let replayRunId = 0;

function waitMs(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function stopReplay() {
  replayRunId++;
  replayActive.value = false;
  replayBoardState.value = { pieces: {}, edges: [] };
  replayWinner.value = null;
  replayTurn.value = 'BLACK';
  selected.value = null;
}

async function startReplay(recordId) {
  stopReplay();
  recordsModal.value = false;
  const runId = ++replayRunId;
  replayActive.value = true;
  selected.value = null;

  const res = await axios.get(`/api/rooms/records/${encodeURIComponent(recordId)}`);
  const content = res.data.content || '';
  const lines = content
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);

  // JSONL 每行一个对象
  const events = lines
    .map((l) => {
      try {
        return JSON.parse(l);
      } catch (e) {
        return null;
      }
    })
    .filter(Boolean);

  // 只取带 board 快照的事件（init/move/end/quit）
  const steps = events
    .filter((evt) => evt && evt.board)
    .map((evt) => ({
      ts: evt.ts,
      board: evt.board,
      currentTurn: evt.currentTurn,
      winner: evt.winner
    }));

  // 基本按文件顺序播放；如果 ts 存在，也可以稳定排序
  if (steps.length > 1 && steps.some((s) => s.ts)) {
    steps.sort((a, b) => {
      const at = a.ts ? new Date(a.ts).getTime() : 0;
      const bt = b.ts ? new Date(b.ts).getTime() : 0;
      return at - bt;
    });
  }

  for (let i = 0; i < steps.length; i++) {
    if (replayRunId !== runId) return;
    const step = steps[i];
    replayBoardState.value = normalizeBoard(step.board);
    replayTurn.value = step.currentTurn || replayTurn.value;
    replayWinner.value = step.winner || null;

    // 步间隔时间：给用户足够观察每一步
    const base = i === 0 ? 650 : 850;
    const interval = base * replaySpeed.value;
    await waitMs(interval);
  }
}

function onPieceClick(cell) {
  if (replayActive.value) return;
  if (!cell.usable || !cell.piece) return;
  if (displayWinner.value) return;
  // only players can move; also must match turn by backend
  if (me.value.role !== 'PLAYER') return;
  const myTurnColor = me.value.seat === 'BLACK' ? 'BLACK' : me.value.seat === 'WHITE' ? 'WHITE' : null;
  if (!myTurnColor || myTurnColor !== displayCurrentTurn.value) return;
  if (cell.piece !== displayCurrentTurn.value) return;
  selected.value = { x: cell.x, y: cell.y };
  playPickupSound();
}

async function onCellClick(cell) {
  if (replayActive.value) return;
  if (!cell.usable || displayWinner.value) return;
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
    playDropSound();
    boardState.value = normalizeBoard(res.data.board);
    currentTurn.value = res.data.currentTurn;
    winner.value = res.data.winner;
    selected.value = null;
  } catch (e) {
  }
}

let resizeTimer = null;
function onResize() {
  if (resizeTimer) clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    updateBoardMetrics();
  }, 80);
}

onMounted(() => {
  nextTick(() => updateBoardMetrics());
  window.addEventListener('resize', onResize);
  window.addEventListener('pointerdown', unlockAudioOnce, { once: true });

  // 如果用户上次已进入房间，直接恢复进入房间态，不展示房间列表
  if (currentRoomId.value) {
    joinRoom(currentRoomId.value, localStorage.getItem('bg_mode') || 'SPECTATOR').catch(() => {
      // 后端可能已重启或用户不在房间内：清理本地记录并回到房间列表
      localStorage.removeItem('bg_roomId');
      localStorage.removeItem('bg_mode');
      currentRoomId.value = null;
      currentRoom.value = null;
      me.value = { role: 'SPECTATOR', seat: null };
      disconnectSse();
    });
  }

  fetchRooms().catch(() => {});
  const t = setInterval(() => {
    if (!inRoom.value) fetchRooms().catch(() => {});
  }, 2000);
  roomsTimer.value = t;
});

onBeforeUnmount(() => {
  disconnectSse();
  window.removeEventListener('resize', onResize);
  window.removeEventListener('pointerdown', unlockAudioOnce);
  stopBgm();
  if (roomsTimer.value) clearInterval(roomsTimer.value);
  if (resizeTimer) clearTimeout(resizeTimer);
});

watch(
  () => inRoom.value,
  () => {
    // switch BGM track between lobby/room
    playBgmForContext();
  }
);

watch(
  () => sfxVolume.value,
  (v) => {
    const nv = clamp01(v, DEFAULT_SFX_VOLUME);
    if (nv !== v) sfxVolume.value = nv;
    localStorage.setItem('bg_sfxVolume', String(nv));
  }
);

watch(
  () => bgmVolume.value,
  (v) => {
    const nv = clamp01(v, DEFAULT_BGM_VOLUME);
    if (nv !== v) bgmVolume.value = nv;
    bgm.value.volume = nv;
    if (bgm.value.audio) {
      try {
        bgm.value.audio.volume = nv;
      } catch (e) {}
    }
    localStorage.setItem('bg_bgmVolume', String(nv));
  }
);
</script>

