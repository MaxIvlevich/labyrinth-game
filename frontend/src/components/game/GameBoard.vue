<script setup>
import { ref, computed } from 'vue';
import { useGameStore } from '@/stores/game.js';
import { useAuthStore } from '@/stores/auth.js';
import BoardCell from '@/components/game/BoardCell.vue';
import PlayerPiece from '@/components/game/PlayerPiece.vue';
import DropZone from '@/components/game/DropZone.vue';

const gameStore = useGameStore();
const authStore = useAuthStore();

// --- КОНСТАНТЫ И ЛОКАЛЬНОЕ СОСТОЯНИЕ ---
const props = defineProps({
  pendingShift: { type: Object, default: null },
  cellSize: { type: Number, required: true }
});

const emit = defineEmits(['drop-tile', 'rotate-tile']);

// --- ВЫЧИСЛЯЕМЫЕ СВОЙСТВА ДЛЯ ДАННЫХ ---
const board = computed(() => gameStore.game?.board);
const players = computed(() => gameStore.game?.players || []);

const isMyTurnToShift = computed(() => {
  const game = gameStore.game;
  return game?.currentPhase === 'PLAYER_SHIFT' && game?.currentPlayer?.id === authStore.userId;
});

// Готовим данные для рендеринга доски
const flatGrid = computed(() => board.value?.grid.flat() || []);
const groupedPlayers = computed(() => {
  const playersByPosition = players.value.reduce((acc, player) => {
    const key = `${player.currentX}-${player.currentY}`;
    if (!acc[key]) acc[key] = [];
    acc[key].push(player);
    return acc;
  }, {});
  return Object.values(playersByPosition).flatMap(group =>
      group.map((player, index) => ({ ...player, groupSize: group.length, indexInGroup: index }))
  );
});

// Готовим данные для рендеринга зон вставки
const dropZonesData = computed(() => {
  if (!board.value) return [];
  const zones = [];
  const size = board.value.size;
  for (let i = 1; i < size; i += 2) {
    zones.push({ key: `t-${i}`, positionClass: 'top', index: i, direction: 'SOUTH' });
    zones.push({ key: `b-${i}`, positionClass: 'bottom', index: i, direction: 'NORTH' });
    zones.push({ key: `l-${i}`, positionClass: 'left', index: i, direction: 'EAST' });
    zones.push({ key: `r-${i}`, positionClass: 'right', index: i, direction: 'WEST' });
  }
  return zones;
});

// --- СТИЛИ И ОБРАБОТЧИКИ ---

// Стиль для главного контейнера, задающий размер всей сетки 9x9
const fullAreaStyle = computed(() => {
  if (!board.value) return {};
  const fullSize = board.value.size + 2;
  return {
    '--full-size': fullSize,
    '--board-size': board.value.size,
    '--cell-size': `${props.cellSize}px`,
  };
});

// Функция для вычисления grid-area каждой зоны
function getZoneGridArea(zone) {
  const boardSize = board.value.size;
  const gridIndex = zone.index + 1;
  const startLine = gridIndex + 1;
  const endLine = gridIndex + 2;

  switch (zone.positionClass) {
      // grid-area: row-start / col-start / row-end / col-end
    case 'top':    return { gridArea: `1 / ${startLine} / 2 / ${endLine}` };
    case 'bottom': return { gridArea: `${boardSize + 2} / ${startLine} / ${boardSize + 3} / ${endLine}` };
    case 'left':   return { gridArea: `${startLine} / 1 / ${endLine} / 2` };
    case 'right':  return { gridArea: `${startLine} / ${boardSize + 2} / ${endLine} / ${boardSize + 3}` };
    default: return {};
  }
}

// Обработчик "бросания" тайла
function handleTileDrop(shiftInfo, event) {
  event.preventDefault();
  const tileData = JSON.parse(event.dataTransfer.getData('application/json'));
  emit('drop-tile', shiftInfo, tileData);
}

// Обработчик вращения тайла в зоне
function handleRotatePendingTile(shiftInfo) {
  emit('rotate-tile', shiftInfo);
}

function getPlayerAtBase(x, y) {
  return players.value.find(p => p.baseX === x && p.baseY === y);
}

const previewGrid = computed(() => {
  const shift = props.pendingShift;
  const originalGrid = board.value?.grid;
  if (!shift || !originalGrid) return originalGrid?.flat() || [];

  const newGrid = structuredClone(originalGrid);
  const { direction, index } = shift.shiftInfo;
  const size = board.value.size;
  let fallenTile;
  switch (direction) {
    case 'SOUTH': fallenTile = newGrid[size-1][index].tile; for(let y=size-1;y>0;y--)newGrid[y][index].tile=newGrid[y-1][index].tile; newGrid[0][index].tile = shift.tile; break;
    case 'NORTH': fallenTile = newGrid[0][index].tile; for(let y=0;y<size-1;y++)newGrid[y][index].tile=newGrid[y+1][index].tile; newGrid[size-1][index].tile = shift.tile; break;
    case 'EAST': fallenTile = newGrid[index][size-1].tile; for(let x=size-1;x>0;x--)newGrid[index][x].tile=newGrid[index][x-1].tile; newGrid[index][0].tile = shift.tile; break;
    case 'WEST': fallenTile = newGrid[index][0].tile; for(let x=0;x<size-1;x++)newGrid[index][x].tile=newGrid[index][x+1].tile; newGrid[index][size-1].tile = shift.tile; break;
  }
  return newGrid.flat();
});
</script>

<template>
  <!-- Главный контейнер-сетка (например, 9x9) -->
  <div v-if="board" class="full-board-area" :style="fullAreaStyle">

    <!-- 1. Сама доска (7x7), размещенная в центре сетки -->
    <div class="game-board">
      <BoardCell
          v-for="cell in previewGrid"
          :key="`cell-${cell.x}-${cell.y}`"
          :cell="cell"
          :base-owner="getPlayerAtBase(cell.x, cell.y)"
      />
    </div>

    <!-- 2. Оверлей с фишками, размещенный точно над доской -->
    <div class="pieces-overlay">
      <PlayerPiece
          v-for="player in groupedPlayers"
          :key="`player-${player.id}`"
          :player="player"
          :cell-size="props.cellSize"
      />
    </div>

    <!-- 3. Зоны вставки, размещенные по краям сетки (видимы, если наш ход) -->
    <template v-if="isMyTurnToShift">
      <div
          v-for="zone in dropZonesData"
          :key="zone.key"
          class="drop-zone-wrapper"
          :style="getZoneGridArea(zone)"
      >
        <DropZone
            :shift-info="zone"
            :tile="pendingShift?.shiftInfo.key === zone.key ? pendingShift.tile : null"
            @drop-tile="handleTileDrop"
            @rotate-tile="handleRotatePendingTile"
        />
      </div>
    </template>

  </div>
  <div v-else class="loading-placeholder">
    <p>Ожидание данных о доске...</p>
  </div>
</template>

<style scoped>
/* Главный контейнер, который является большой сеткой */
.full-board-area {
  display: grid;
  grid-template-columns: repeat(var(--full-size), var(--cell-size));
  grid-template-rows: repeat(var(--full-size), var(--cell-size));
  justify-content: center;
  align-content: center;
}

/* Доска занимает центральную область большой сетки */
.game-board {
  grid-column: 2 / span var(--board-size);
  grid-row: 2 / span var(--board-size);
  display: grid;
  grid-template-columns: repeat(var(--board-size), var(--cell-size));
  grid-template-rows: repeat(var(--board-size), var(--cell-size));
  border: 3px solid #6d4c41;
}

/* Оверлей с фишками идеально накладывается на доску */
.pieces-overlay {
  grid-column: 2 / span var(--board-size);
  grid-row: 2 / span var(--board-size);
  position: relative;
  pointer-events: none;
}

/* Обертка для зоны вставки просто занимает свою ячейку в большой сетке */
.drop-zone-wrapper {
  width: 100%;
  height: 100%;
  padding: 5px;
  box-sizing: border-box;
}

.loading-placeholder {
  min-height: 500px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>