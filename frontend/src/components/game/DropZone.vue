<script setup>
import { ref } from 'vue';

const props = defineProps({
  // Данные о том, где находится эта зона
  shiftInfo: { type: Object, required: true }
});

const emit = defineEmits(['drop-tile']);

// Локальное состояние для подсветки
const isDragOver = ref(false);

function onDrop(event) {
  event.preventDefault(); // Обязательно для drop-события
  isDragOver.value = false;
  // Сообщаем родителю, что сюда "бросили" тайл, и передаем инфо о зоне
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
  >

  </div>
</template>

<style scoped>
.drop-zone {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  transition: all 0.2s ease;
  background-color: rgba(0, 0, 0, 0.1);
  border: 2px dashed rgba(255, 255, 255, 0.4);
  box-shadow: inset 0 0 5px rgba(0, 0, 0, 0.2);
}
.drop-zone.is-drag-over {
  background-color: rgba(144, 238, 144, 0.4);
  transform: scale(1.05);
}
</style>