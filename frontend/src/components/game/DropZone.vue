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
  emit('drop-tile', props.shiftInfo);
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
      @dragover="onDragOver"
      @dragleave="onDragLeave"
  >
    <!-- Здесь может быть иконка или просто пустое место -->
  </div>
</template>

<style scoped>
.drop-zone {
  width: 100%;
  height: 100%;
  border: 2px dashed rgba(255, 255, 255, 0.4);
  border-radius: 8px;
  transition: all 0.2s ease;
  /* Центрируем содержимое, если оно будет */
  display: flex;
  align-items: center;
  justify-content: center;
}
.drop-zone.is-drag-over {
  background-color: rgba(255, 255, 255, 0.2);
  border-style: solid;
}
</style>