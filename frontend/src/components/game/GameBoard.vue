<script setup>
import {computed} from 'vue';
import {useGameStore} from '@/stores/game.js';
import BoardCell from '@/components/game/BoardCell.vue';
import PlayerPiece from '@/components/game/PlayerPiece.vue';

const gameStore = useGameStore();

const board = computed(() => gameStore.game?.board);
const players = computed(() => gameStore.game?.players || []);

const boardStyles = computed(() => {
  if (!board.value) return {};
  return {
    '--board-size': board.value.size,
    '--cell-size': '60px'
  };
});

const flatGrid = computed(() => {
  // Проверяем `board.value`
  return board.value?.grid.flat() || [];
});

const groupedPlayers = computed(() => {
  if (!players) return [];

  // Создаем объект, где ключ - "x-y", а значение - массив игроков на этой клетке
  const playersByPosition = players.value.reduce((acc, player) => {
    const key = `${player.currentX}-${player.currentY}`;
    if (!acc[key]) {
      acc[key] = [];
    }
    acc[key].push(player);
    return acc;
  }, {});

  // Преобразуем объект обратно в плоский список, но с дополнительной информацией
  return Object.values(playersByPosition).flatMap(group =>
      group.map((player, index) => ({
        ...player, // Копируем все свойства игрока
        groupSize: group.length, // Сколько всего игроков в этой ячейке
        indexInGroup: index, // Порядковый номер этого игрока в группе (0, 1, 2...)
      }))
  );
});
</script>

<template>
  <!-- Основной контейнер, который задает размеры и позиционирование -->
  <div v-if="board" class="game-board-container" :style="boardStyles">

    <!-- Слой 1: Подложка с ячейками -->
    <div class="board-grid">
      <BoardCell
          v-for="cell in flatGrid"
          :key="`cell-${cell.x}-${cell.y}`"
          :cell="cell"
      />
    </div>
    <!-- Слой 2: Наложение с фишками и другими динамическими элементами -->
    <div class="pieces-overlay">
      <PlayerPiece
          v-for="player in groupedPlayers"
          :key="`player-${player.id}`"
          :player="player"
          :cell-size="60"
      />
      <!-- Сюда позже добавим кнопки сдвига -->
    </div>

  </div>
  <div v-else>
    <p>Ожидание данных о доске...</p>
  </div>
</template>

<style scoped>
/* Контейнер, который держит все слои вместе */
.game-board-container {
  position: relative;
  /* Задаем размеры контейнера на основе CSS переменных */
  width: calc(var(--board-size) * var(--cell-size));
  height: calc(var(--board-size) * var(--cell-size));
}

/* Слой с сеткой ячеек */
.board-grid {
  display: grid;
  grid-template-columns: repeat(var(--board-size), var(--cell-size));
  grid-template-rows: repeat(var(--board-size), var(--cell-size));
  width: 100%;
  height: 100%;
  border: 3px solid #6d4c41;
  background-color: #a1887f;
}

/* Слой с фишками, который накладывается точно поверх сетки */
.pieces-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  /* Этот слой не должен ловить клики, чтобы можно было кликать на ячейки под ним */
  pointer-events: none;
}
</style>