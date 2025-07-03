<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  tile: {
    type: Object,
    required: true,
  }
});

const currentTheme = ref('classic');


const imageUrl = computed(() => {
  const tileType = props.tile.type.toLowerCase();
  return `/images/tiles/${currentTheme.value}/${tileType}.png`;
});

// Вычисляемое свойство для класса поворота
const rotationClass = computed(() => {
  return `rot-${props.tile.orientation}`;
});
</script>

<template>
  <div
      class="tile"
      :class="rotationClass"
      :style="{ backgroundImage: `url(${imageUrl})` }"
  >
    <!-- Todo маркеры-->
  </div>
</template>

<style scoped>
.tile {
  width: 100%;
  height: 100%;
  background-repeat: no-repeat;
  background-position: center;
  background-size: 100%;
  transition: transform 0.3s ease;
  image-rendering: pixelated;
}

/* Стили для поворота тайлов */
.rot-0 { transform: rotate(0deg); }
.rot-1 { transform: rotate(90deg); }
.rot-2 { transform: rotate(180deg); }
.rot-3 { transform: rotate(270deg); }
</style>