<script setup>
import TilePiece from './TilePiece.vue';

defineProps({
  cell: {
    type: Object,
    required: true
  }
});
</script>

<template>
  <div class="cell" :class="{ stationary: cell.stationary }">
    <TilePiece v-if="cell.tile" :tile="cell.tile" />

  </div>
</template>

<style scoped>
.cell {
  background-color: #d7ccc8;
  border: 1px solid #bcaaa4;
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
}

.cell.stationary {
  background-color: #bcaaa4;
}
</style>