<script setup>
import { computed } from 'vue';
import { useGameStore } from '@/stores/game.js';
import BoardCell from '@/components/game/BoardCell.vue';

const gameStore = useGameStore();
const board = gameStore.game.board;

const boardStyles = computed(() => {
  if (!board) return {};
  return {
    '--board-size': board.size,
    '--cell-size': '60px' // TODO сделать адаптивным.
  };
});

const flatGrid = computed(() => board?.grid.flat() || []);

</script>

<template>
  <div class="game-board-container">
    <div v-if="board" class="game-board" :style="boardStyles">
      <BoardCell
          v-for="cell in flatGrid"
          :key="`${cell.x}-${cell.y}`"
          :cell="cell"
      />

      <!-- TODO фишки игроков и кнопки сдвига -->
    </div>
    <div v-else>
      <p>Ожидание данных о доске...</p>
    </div>
  </div>
</template>

<style scoped>
.game-board-container {
  position: relative;
}

.game-board {
  display: grid;
  grid-template-columns: repeat(var(--board-size), var(--cell-size));
  grid-template-rows: repeat(var(--board-size), var(--cell-size));

  border: 3px solid #6d4c41; /* Рамка "под дерево" */
  background-color: #a1887f; /* Фон "стола" */
  box-shadow: inset 0 0 15px rgba(0,0,0,0.4);
}
</style>