<script setup>
import { ref } from 'vue';
import TilePiece from './TilePiece.vue';

const props = defineProps({
  shiftInfo: { type: Object, required: true },
  tile: { type: Object, default: null }
});

const emit = defineEmits(['drop-tile', 'pickup-tile']);

const isDragOver = ref(false);

function onDrop(event) {
  isDragOver.value = false;
  emit('drop-tile', props.shiftInfo, event);
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
      @click="$emit('pickup-tile', shiftInfo)"
  >
    <TilePiece v-if="tile" :tile="tile" />
  </div>
</template>

<style scoped>
.drop-zone {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 8px;
  background-color: rgba(0, 0, 0, 0.15);
  border: 2px dashed rgba(255, 255, 255, 0.4);
  transition: all 0.2s ease-out;
  display: flex;
  align-items: center;
  justify-content: center;
}
.drop-zone.is-drag-over {
  background-color: rgba(144, 238, 144, 0.4);
  border-style: solid;
}
</style>