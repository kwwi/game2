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
  const res = await axios.get('/api/game/state');
  boardState.value = normalizeBoard(res.data.board);
  currentTurn.value = res.data.currentTurn;
  winner.value = res.data.winner;
}

function normalizeBoard(board) {
  if (!board) return { pieces: {}, edges: [] };
  const pieces = board.pieces && typeof board.pieces === 'object' ? board.pieces : {};
  const edges = Array.isArray(board.edges) ? board.edges : [];
  return { pieces, edges };
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

