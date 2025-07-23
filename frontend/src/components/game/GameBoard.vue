<script setup>
import {computed, toRaw} from 'vue';
import {useGameStore} from '@/stores/game.js';
import {useAuthStore} from '@/stores/auth.js';
import BoardCell from '@/components/game/BoardCell.vue';
import PlayerPiece from '@/components/game/PlayerPiece.vue';
import DropZone from '@/components/game/DropZone.vue';

const props = defineProps({
  board: {type: Object, default: null},
  players: {type: Array, default: () => []},
  isMyTurnToShift: {type: Boolean, default: false},
  pendingShift: {type: Object, default: null},
  cellSize: {type: Number, required: true}
});
const emit = defineEmits(['drop-tile', 'pickup-tile']);

const gameStore = useGameStore();
const authStore = useAuthStore();

const board = computed(() => props.board);
const players = computed(() => props.players || []);

const isMyTurnToShift = computed(() => {
  const game = gameStore.game;
  return game?.currentPhase === 'PLAYER_SHIFT' && game?.currentPlayer?.id === authStore.userId;
});

const fullAreaStyle = computed(() => {
  if (!board.value) return {};
  const fullSize = board.value.size + 2;
  return {
    '--full-size': fullSize,
    '--board-size': board.value.size,
    '--cell-size': `${props.cellSize}px`,
  };
});

const previewGrid = computed(() => {
  const shift = props.pendingShift;
  const originalGrid = board.value?.grid;
  if (!shift || !originalGrid) return originalGrid?.flat() || [];

  const rawGrid = toRaw(originalGrid);
  const newGrid = structuredClone(rawGrid);
  const {direction, index} = shift.shiftInfo;
  const size = board.value.size;

  switch (direction) {
    case 'SOUTH':
      for (let y = size - 1; y > 0; y--) newGrid[y][index].tile = newGrid[y - 1][index].tile;
      newGrid[0][index].tile = shift.tile;
      break;
    case 'NORTH':
      for (let y = 0; y < size - 1; y++) newGrid[y][index].tile = newGrid[y + 1][index].tile;
      newGrid[size - 1][index].tile = shift.tile;
      break;
    case 'EAST':
      for (let x = size - 1; x > 0; x--) newGrid[index][x].tile = newGrid[index][x - 1].tile;
      newGrid[index][0].tile = shift.tile;
      break;
    case 'WEST':
      for (let x = 0; x < size - 1; x++) newGrid[index][x].tile = newGrid[index][x + 1].tile;
      newGrid[index][size - 1].tile = shift.tile;
      break;
  }
  return props.board.grid.flat();
  //return newGrid.flat();
});

const groupedPlayers = computed(() => {
  const playersByPosition = props.players.reduce((acc, player) => {
    const key = `${player.currentX}-${player.currentY}`;
    if (!acc[key]) acc[key] = [];
    acc[key].push(player);
    return acc;
  }, {});
  return Object.values(playersByPosition).flatMap(group =>
      group.map((player, index) => ({...player, groupSize: group.length, indexInGroup: index}))
  );
});
const dropZonesData = computed(() => {
  const zones = [];
  const size = props.board.size;
  for (let i = 2; i < size; i += 2) {
    zones.push({key: `t-${i}`, positionClass: 'top', index: i, direction: 'SOUTH'});
    zones.push({key: `b-${i}`, positionClass: 'bottom', index: i, direction: 'NORTH'});
    zones.push({key: `l-${i}`, positionClass: 'left', index: i, direction: 'EAST'});
    zones.push({key: `r-${i}`, positionClass: 'right', index: i, direction: 'WEST'});
  }
  return zones;
});

function getZoneGridArea(zone) {
  const boardSize = props.board.size;
  const startLine = zone.index + 1;
  const endLine = startLine + 1;

  switch (zone.positionClass) {
      // grid-area: row-start / col-start / row-end / col-end
    case 'top':    return { gridArea: `1 / ${startLine} / 2 / ${endLine}` };
    case 'bottom': return { gridArea: `${boardSize + 2} / ${startLine} / ${boardSize + 3} / ${endLine}` };
    case 'left':   return { gridArea: `${startLine} / 1 / ${endLine} / 2` };
    case 'right':  return { gridArea: `${startLine} / ${boardSize + 2} / ${endLine} / ${boardSize + 3}` };
    default: return {};
  }
}

function handleTileDrop(shiftInfo, event) {
  emit('drop-tile', shiftInfo, event);
}

function handlePickupTile(shiftInfo) {
  emit('pickup-tile', shiftInfo);
}
</script>

<template>
  <div v-if="props.board" class="full-board-area" :style="fullAreaStyle">
    <div class="game-board">
      <BoardCell v-for="cell in previewGrid" :key="`cell-${cell.x}-${cell.y}`" :cell="cell"/>

      <div class="pieces-overlay">
        <PlayerPiece v-for="player in groupedPlayers" :key="`player-${player.id}`" :player="player"
                     :cell-size="props.cellSize"/>
      </div>
    </div>
    <template v-if="isMyTurnToShift">
      <div v-for="zone in dropZonesData" :key="zone.key" class="drop-zone-wrapper" :style="getZoneGridArea(zone)">
        <div style="position:absolute; top:0; left:0; color:red; font-size:10px; z-index: 101;">
          {{ zone.index }}
        </div>
        <DropZone
            :shift-info="zone"
            :tile="props.pendingShift?.shiftInfo.key === zone.key ? props.pendingShift.tile : null"
            @drop-tile="handleTileDrop"
            @pickup-tile="handlePickupTile"
        />
      </div>
    </template>
  </div>
  <div v-else class="loading-placeholder">
    <p>Ожидание данных о доске...</p>
  </div>
</template>

<style scoped>
.full-board-area::after {
  content: '';
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  display: grid;
  grid-template-columns: repeat(var(--full-size), var(--cell-size));
  grid-template-rows: repeat(var(--full-size), var(--cell-size));
  border: 1px solid red;
  pointer-events: none;
  z-index: 100;
}

/* Главный контейнер, который является большой сеткой */
.full-board-area {
  position: relative;
  display: grid;
  /* Создаем сетку (board.size + 2) x (board.size + 2) для доски и дроп-зон */
  grid-template-columns: repeat(var(--full-size), var(--cell-size));
  grid-template-rows: repeat(var(--full-size), var(--cell-size));
}

/* Доска занимает центральную область большой сетки */
.game-board {
  /* Размещаем в сетке, начиная со 2-й колонки/ряда */
  grid-column: 2 / span var(--board-size);
  grid-row: 2 / span var(--board-size);

  /* Внутренняя сетка для ячеек доски */
  display: grid;
  grid-template-columns: repeat(var(--board-size), var(--cell-size));
  grid-template-rows: repeat(var(--board-size), var(--cell-size));
  border: 3px solid #6d4c41;

  /* Делаем доску точкой отсчета для оверлея с фишками */
  position: relative;

  z-index: 1;
}

/* Оверлей с фишками идеально накладывается на доску */
.pieces-overlay {
/* Позиционируем абсолютно относительно .game-board */
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none; /* Чтобы клики "проходили" сквозь него */
  z-index: 2;
}

/* Обертка для зоны вставки занимает свою ячейку в большой сетке */
.drop-zone-wrapper {
  position: absolute;
  width: var(--cell-size);
  height: var(--cell-size);
  padding: 5px;
  box-sizing: border-box;
  z-index: 3;
}

.loading-placeholder {
  min-height: 500px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>