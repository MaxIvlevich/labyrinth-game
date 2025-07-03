<script setup>
import { computed } from 'vue';

const props = defineProps({
  player: { type: Object, required: true },
  cellSize: { type: Number, required: true }
});

function getAvatarColor(avatarType) {
  const colors = { 'KNIGHT': '#8a94a1', 'MAGE': '#7a5b9e', 'ARCHER': '#5a8d5c', 'DWARF': '#c56b3e', 'ROGUE': '#808080' };
  return colors[avatarType] || '#6c757d';
}

const pieceStyles = computed(() => {
  const pieceSize = props.cellSize * 0.4; // Сделаем 70%
  const offset = (props.cellSize - pieceSize) / 2;

  let translateX = '0%';
  let translateY = '0%';

  // Если в ячейке больше одной фишки
  if (props.player.groupSize > 1) {
    const angle = (props.player.indexInGroup / props.player.groupSize) * 2 * Math.PI;
    const shiftAmount = pieceSize * 0.25; // Сдвиг на 25% от размера фишки

    // Расталкиваем фишки по кругу от центра
    translateX = `${Math.cos(angle) * shiftAmount}px`;
    translateY = `${Math.sin(angle) * shiftAmount}px`;
  }
  return {
    top: `${props.player.currentY * props.cellSize + offset}px`,
    left: `${props.player.currentX * props.cellSize + offset}px`,
    backgroundColor: getAvatarColor(props.player.avatarType),
    width: `${pieceSize}px`,
    height: `${pieceSize}px`,
    // Добавляем transform для сдвига
    transform: `translate(${translateX}, ${translateY})`,
  };
});
</script>

<template>
  <div
      class="player-piece"
      :style="pieceStyles"
      :class="{ 'disconnected-piece': player.status === 'DISCONNECTED' }"
  ></div>
</template>

<style scoped>
.player-piece {
  position: absolute; /* Позиционируется относительно .pieces-overlay */
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 2px 4px rgba(0,0,0,0.5);
  transition: all 0.4s ccubic-bezier(0.68, -0.55, 0.27, 1.55), transform 0.3s ease-out;
  z-index: 10;
  pointer-events: auto; /* Фишка может ловить клики */
}
.disconnected-piece { opacity: 0.5; filter: grayscale(1); }
</style>