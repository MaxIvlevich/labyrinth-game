<script setup>
import { ref } from 'vue';
import TilePiece from './TilePiece.vue';
const props = defineProps({
  // Данные о том, где находится эта зона
  shiftInfo: { type: Object, required: true },
  tile: { type: Object, default: null }
});

const emit = defineEmits(['drop-tile', 'rotate-tile']);

function onTileDragStart(event) {
  if (!props.tile) return;
  // Делаем то же самое, что и в GameView: прикрепляем данные тайла
  event.dataTransfer.setData('application/json', JSON.stringify(props.tile));
  event.dataTransfer.effectAllowed = 'move';
  // Сообщаем родителю, что мы "взяли" тайл из этой конкретной зоны
  emit('tile-drag-start', props.shiftInfo);
}
// Локальное состояние для подсветки
const isDragOver = ref(false);

function onDrop(event) {
  isDragOver.value = false;
  emit('drop-tile', props.shiftInfo, event);
}

function onDragOver(event) {
  event.preventDefault(); // Обязательно, чтобы разрешить drop
  isDragOver.value = true;
}

function onDragLeave() {
  isDragOver.value = false;
}
</script>

<template>
  <div
      class="drop-zone"
      :class="{ 'is-drag-over': isDragOver }"
      @drop="onDrop"
      @dragover.prevent="isDragOver = true"
      @dragleave="onDragLeave"
      @click="$emit('rotate-tile', shiftInfo)"
  >
    <TilePiece
        v-if="tile"
        :tile="tile"
        draggable="true"
    @dragstart.stop="onTileDragStart"
    />
  </div>
</template>

<style scoped>
.drop-zone {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  transition: all 0.2s ease-out;
  background-color: rgba(0, 0, 0, 0.15);
  border: 2px dashed rgba(255, 255, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.drop-zone.is-drag-over {
  background-color: rgba(144, 238, 144, 0.4);
  border-style: solid;
  transform: scale(1.05);
}
</style>