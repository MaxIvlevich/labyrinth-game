<script setup>
import { computed } from 'vue';

const props = defineProps({
  // 'top', 'bottom', 'left', 'right' - для позиционирования
  positionClass: { type: String, required: true },
  // Индекс ряда/колонки для сдвига
  index: { type: Number, required: true },
  // Направление сдвига ('NORTH', 'SOUTH', 'EAST', 'WEST')
  direction: { type: String, required: true }
});

// Событие, которое компонент отправляет "наверх" при клике
const emit = defineEmits(['shift']);

// Вычисляемые стили для точного позиционирования кнопки
const buttonStyle = computed(() => {
  const cellSize = 60; // Размер ячейки, который мы используем в GameBoard
  const position = `${props.index * cellSize + cellSize / 2}px`;

  switch (props.positionClass) {
    case 'top':    return { top: '0', left: position };
    case 'bottom': return { bottom: '0', left: position };
    case 'left':   return { left: '0', top: position };
    case 'right':  return { right: '0', top: position };
    default: return {};
  }
});

function handleClick() {
  // При клике отправляем событие с нужными данными
  emit('shift', { direction: props.direction, index: props.index });
}
</script>

<template>
  <button
      class="shift-btn"
      :class="positionClass"
      :style="buttonStyle"
      @click.stop="handleClick"
  ></button>
</template>

<style scoped>
.shift-btn {
  position: absolute;
  width: 36px;
  height: 36px;
  background-color: #ffca28;
  border: 2px solid #ff8f00;
  border-radius: 50%;
  cursor: pointer;
  pointer-events: all; /* Важно, чтобы кнопки ловили клики */
  transition: all 0.2s ease;

  background-image: url('/images/ui/arrow.svg');

  background-size: 60%;
  background-repeat: no-repeat;
  background-position: center;

  /* Центрируем кнопку относительно точки позиционирования (top/left) */
  transform: translate(-50%, -50%);
}

.shift-btn:hover {
  background-color: #ffde7a;
  /* Увеличиваем кнопку, сохраняя центрирование */
  transform: translate(-50%, -50%) scale(1.1);
}

/* Поворот иконки-стрелки в зависимости от позиции */
.shift-btn.top    { /* Поворот для стрелки ВНИЗ */ }
.shift-btn.bottom { transform: translate(-50%, -50%) rotate(180deg); }
.shift-btn.left   { transform: translate(-50%, -50%) rotate(-90deg); }
.shift-btn.right  { transform: translate(-50%, -50%) rotate(90deg); }

/* Если ваша стрелка по умолчанию смотрит вправо, как моя из примера, то эти стили будут правильными.
   Если она смотрит вверх, то нужны другие значения rotate. */
</style>