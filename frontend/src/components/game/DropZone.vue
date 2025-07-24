<script setup>
import { ref } from 'vue';
import TilePiece from './TilePiece.vue';

const props = defineProps({
  shiftInfo: { type: Object, required: true },
  tile: { type: Object, default: null }
});

const emit = defineEmits(['drop-tile', 'zone-click']);

const isDragOver = ref(false);
function handleDragStart(event) {
  if (!props.tile) {
    event.preventDefault();
    return;
  }
  // Кладем в dataTransfer данные именно этого тайла
  event.dataTransfer.setData('application/json', JSON.stringify(props.tile));
  event.dataTransfer.effectAllowed = 'move';
  // Важно! Останавливаем всплытие, чтобы не сработал drag-событие у родителя
  event.stopPropagation();
}

function onDrop(event) {
  isDragOver.value = false;
  try {
    const tileJson = event.dataTransfer.getData('application/json');
    if (tileJson) {
      const droppedTileData = JSON.parse(tileJson);
      emit('drop-tile', props.shiftInfo, droppedTileData);
    }
  } catch (e) {
    console.error("Ошибка при обработке drop события:", e);
  }
}

function onClick() {
  emit('zone-click', props.shiftInfo);
}

function onDragLeave() {
  isDragOver.value = false;
}
</script>

<template>
  <div
      class="drop-zone"
      :class="{ 'is-drag-over': isDragOver, 'has-tile': !!tile }"
      @drop.prevent="onDrop"
      @dragover.prevent="isDragOver = true"
      @dragleave="onDragLeave"
      @click="onClick"
  >
    <div
        v-if="tile"
        class="tile-wrapper-in-zone"
        draggable="true"
        @dragstart="handleDragStart"
    >
      <TilePiece :tile="tile" />
    </div>

    <div v-else class="drop-zone-target"></div>
  </div>
</template>

<style scoped>
.tile-wrapper-in-zone {
  width: 100%;
  height: 100%;
  cursor: grab;
}
.tile-wrapper-in-zone:active {
  cursor: grabbing;
}
.drop-zone {
  width: 100%;
  height: 100%;
  border: 2px dashed #aaa;
  border-radius: 8px;
  transition: all 0.2s ease;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
}
.drop-zone.is-drag-over {
  border-color: #007bff;
  background-color: rgba(0, 123, 255, 0.1);
}
.drop-zone.has-tile {
  border-style: solid;
  border-color: #28a745;
}
.drop-zone-target::after {
  content: '+';
  font-size: 24px;
  color: #aaa;
}
</style>