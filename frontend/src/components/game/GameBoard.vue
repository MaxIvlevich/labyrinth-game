<script setup>
import { computed } from 'vue';
import { useGameStore } from '@/stores/game.js';
import { useAuthStore } from '@/stores/auth.js';
import BoardCell from '@/components/game/BoardCell.vue';
import PlayerPiece from '@/components/game/PlayerPiece.vue';
import DropZone from '@/components/game/DropZone.vue';

const props = defineProps({
  grid: { type: Array, required: true },
  boardSize: { type: Number, required: true },
  players: { type: Array, default: () => [] },
  pendingShift: { type: Object, default: null },
  cellSize: { type: Number, required: true }
});
const emit = defineEmits(['drop-tile']);

const gameStore = useGameStore();
const authStore = useAuthStore();
const isMyTurnToShift = computed(() => {
  const game = gameStore.game;
  return game?.currentPhase === 'PLAYER_SHIFT' && game?.currentPlayer?.id === authStore.userId;
});

const fullAreaStyle = computed(() => {
  const fullSize = props.boardSize + 2;
  return {
    '--full-size': fullSize,
    '--board-size': props.boardSize,
    '--cell-size': `${props.cellSize}px`,
  };
});


const groupedPlayers = computed(() => {
  // ... (эта логика остается без изменений)
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

// Данные для генерации DropZone'ов
const dropZonesData = computed(() => {
  const zones = [];
  for (let i = 1; i < props.boardSize; i += 2) {
    zones.push({key: `t-${i}`, positionClass: 'top', index: i, direction: 'SOUTH'});
    zones.push({key: `b-${i}`, positionClass: 'bottom', index: i, direction: 'NORTH'});
    zones.push({key: `l-${i}`, positionClass: 'left', index: i, direction: 'EAST'});
    zones.push({key: `r-${i}`, positionClass: 'right', index: i, direction: 'WEST'});
  }
  return zones;
});

function getZoneGridArea(zone) {
  const boardSize = props.boardSize;
  const line = zone.index + 2;

  switch (zone.positionClass) {
      // grid-area: row-start / col-start / row-end / col-end
    case 'top':    return { gridArea: `1 / ${line} / 2 / ${line + 1}` };
    case 'bottom': return { gridArea: `${boardSize + 2} / ${line} / ${boardSize + 3} / ${line + 1}` };
    case 'left':   return { gridArea: `${line} / 1 / ${line + 1} / 2` };
    case 'right':  return { gridArea: `${line} / ${boardSize + 2} / ${line + 1} / ${boardSize + 3}` };
    default: return {};
  }
}

function handleTileDrop(shiftInfo) {
  emit('drop-tile', shiftInfo);
}

function handleZoneClick(shiftInfo) {
  if (props.pendingShift?.shiftInfo.key === shiftInfo.key) {
    gameStore.rotatePendingTile();
  } else {
    if (gameStore.displayableExtraTile || props.pendingShift) {
      handleTileDrop(shiftInfo);
    }
  }
}

</script>

<template>
  <div v-if="props.grid.length" class="full-board-area" :style="fullAreaStyle">
    <!-- Игровая доска -->
    <div class="game-board">
      <!-- Просто рендерим ячейки из пропса `grid` -->
      <BoardCell
          v-for="(cell, index) in props.grid.flat()"
          :key="`cell-${index}`"
          :cell="cell"
      />
      <!-- Оверлей с фишками игроков -->
      <div class="pieces-overlay">
        <PlayerPiece v-for="player in groupedPlayers" :key="`player-${player.id}`" :player="player"
                     :cell-size="props.cellSize"/>
      </div>
    </div>

    <!-- DropZone'ы для сдвига -->
    <template v-if="isMyTurnToShift">
      <div v-for="zone in dropZonesData" :key="zone.key" class="drop-zone-wrapper" :style="getZoneGridArea(zone)">
        <DropZone
            :shift-info="zone"
            :tile="props.pendingShift?.shiftInfo.key === zone.key ? props.pendingShift.tile : null"
            @drop-tile="handleTileDrop"
            @zone-click="handleZoneClick"
        />
      </div>
    </template>
  </div>
  <div v-else class="loading-placeholder">
    <p>Ожидание данных о доске...</p>
  </div>
</template>

<style scoped>
.full-board-area {
  display: grid;
  /* full-size - это размер доски + 2 ряда/колонки для зон сдвига */
  grid-template-columns: repeat(var(--full-size), var(--cell-size));
  grid-template-rows: repeat(var(--full-size), var(--cell-size));
  position: relative;
}

.game-board {
  /* Позиционируем доску внутри грида, оставляя место для зон */
  grid-column: 2 / span var(--board-size);
  grid-row: 2 / span var(--board-size);

  display: grid;
  grid-template-columns: repeat(var(--board-size), 1fr);
  background-color: #ccc;
  border: 2px solid #333;
  position: relative; /* для оверлея фишек */
}

.pieces-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none; /* чтобы не мешал кликам по ячейкам, если они понадобятся */
}

.drop-zone-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 5px; /* небольшой отступ для красоты */
}
.loading-placeholder {
  color: #6c757d;
}
</style>