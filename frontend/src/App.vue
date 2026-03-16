<template>
  <div class="game-container">
    <div class="header">
      <div class="title">夹 / 挑 棋 原型</div>
      <div class="controls">
        <button class="btn" @click="reset('BLACK')">黑棋先手</button>
        <button class="btn" @click="reset('WHITE')">白棋先手</button>
      </div>
    </div>

    <div class="board-wrapper">
      <div class="board">
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
        <div class="turn-indicator">
          <span class="badge" v-if="!winner">
            <span
              class="badge-dot"
              :class="currentTurn === 'BLACK' ? 'black' : 'white'"
            ></span>
            <span>{{ currentTurn === 'BLACK' ? '黑棋回合' : '白棋回合' }}</span>
          </span>
          <div v-else class="winner">
            {{ winner === 'BLACK' ? '黑棋胜利 🎉' : '白棋胜利 🎉' }}
          </div>
        </div>
        <div class="hint">
          点击己方棋子选中，再点击一个与其有线相连的空点完成移动；移动后由后端自动完成夹/挑和翻子。
        </div>
        <div class="log">
          <div class="log-entry" v-for="(msg, index) in messages" :key="index">
            {{ msg }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue';
import axios from 'axios';

const boardState = ref(null);
const currentTurn = ref('BLACK');
const winner = ref(null);
const selected = ref(null);
const messages = ref([]);

function isBlocked(x, y) {
  if (x === 1 && [1, 2, 3, 4, 6, 7, 8, 9].includes(y)) return true;
  if (x === 2 && [1, 2, 3, 7, 8, 9].includes(y)) return true;
  return false;
}

const cells = computed(() => {
  const result = [];
  const pieces = (boardState.value && boardState.value.pieces) || {};
  for (let y = 9; y >= 1; y--) {
    for (let x = 1; x <= 9; x++) {
      const key = `${x},${y}`;
      const usable = !isBlocked(x, y);
      const piece = pieces[key] || null;
      result.push({ x, y, key, usable, piece });
    }
  }
  return result;
});

async function fetchState() {
  const res = await axios.get('/api/game/state');
  boardState.value = normalizeBoard(res.data.board);
  currentTurn.value = res.data.currentTurn;
  winner.value = res.data.winner;
}

function normalizeBoard(board) {
  // Spring 会把 Map<Position,PieceColor> 序列化为一个对象，key 是 Position.toString/字段。
  // 为简单起见，这里在后端使用默认 Map -> 我们前端假设 key 格式为 {"x":1,"y":1} 这种 Jackson 风格不方便，
  // 因此在后端改造前，前端使用一个兜底：如果存在 entries 字段，则按其中的 key.x/key.y 来恢复。
  if (!board) return { pieces: {} };
  const map = {};
  if (board.pieces && Array.isArray(board.pieces)) {
    for (const entry of board.pieces) {
      const p = entry.key || entry.k || entry.position;
      const color = entry.value || entry.v || entry.color;
      if (!p || !color) continue;
      const x = p.x ?? p.row ?? p.i;
      const y = p.y ?? p.col ?? p.j;
      if (x && y) {
        map[`${x},${y}`] = color;
      }
    }
  } else if (board.pieces && typeof board.pieces === 'object') {
    // 如果后台改成字符串 key: "x,y"
    for (const k of Object.keys(board.pieces)) {
      map[k] = board.pieces[k];
    }
  }
  return { pieces: map };
}

async function reset(firstPlayer) {
  const res = await axios.post(`/api/game/reset?firstPlayer=${firstPlayer}`);
  boardState.value = normalizeBoard(res.data.board);
  currentTurn.value = res.data.currentTurn;
  winner.value = res.data.winner;
  selected.value = null;
  messages.value.unshift(`${firstPlayer === 'BLACK' ? '黑棋' : '白棋'}先手，重新开始。`);
}

function onPieceClick(cell) {
  if (!cell.usable || !cell.piece) return;
  if (cell.piece !== currentTurn.value || winner.value) return;
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
    const res = await axios.post('/api/game/move', {
      fromX: selected.value.x,
      fromY: selected.value.y,
      toX: cell.x,
      toY: cell.y
    });
    if (!res.data.success) {
      messages.value.unshift(`非法走子：${res.data.message}`);
      return;
    }
    boardState.value = normalizeBoard(res.data.board);
    currentTurn.value = res.data.currentTurn;
    winner.value = res.data.winner;
    messages.value.unshift(
      `${res.data.message} (${selected.value.x},${selected.value.y}) → (${cell.x},${cell.y})`
    );
    selected.value = null;
  } catch (e) {
    messages.value.unshift('请求失败，请检查后端是否启动。');
  }
}

onMounted(() => {
  fetchState().catch(() => {
    messages.value.unshift('无法获取后端状态，请先启动 Java 服务。');
  });
});
</script>

