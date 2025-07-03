
<script setup>
import { onMounted, watch } from 'vue';
import { useGameStore } from '@/stores/game.js';
import { useAuthStore } from '@/stores/auth.js';
import { initializeWebSocket } from '@/api/websocket.js';


// Импортируем все наши View
import LoginView from '@/views/authorise/LoginView.vue';
import LobbyView from '@/views/lobby/LobbyView.vue';
import GameView from '@/views/game/GameView.vue';

const gameStore = useGameStore();
const authStore = useAuthStore();

watch(
    () => authStore.isAuthenticated,
    (isNowAuthenticated, wasPreviouslyAuthenticated) => {
      // Эта функция сработает, когда isAuthenticated изменится
      if (isNowAuthenticated && !wasPreviouslyAuthenticated) {
        // Пользователь только что вошел в систему!
        console.log('Пользователь успешно вошел. Подключаюсь к WebSocket...');
        initializeWebSocket();
      }
    }
);
watch(
    () => gameStore.view,
    (newView, oldView) => {
      console.log(
          `%cVIEW CHANGED: с '${oldView}' на '${newView}'`,
          'color: blue; font-weight: bold;'
      );
      // Если view неожиданно стал 'game', мы хотим знать, почему
      if (newView === 'game') {
        console.log('Данные gameStore.game в этот момент:', JSON.parse(JSON.stringify(gameStore.game)));
        console.trace(); // Покажет нам стек вызовов
      }
    }
);

onMounted(() => {
  if (authStore.isAuthenticated) {
    console.log('Пользователь авторизован. Подключаюсь к WebSocket...');
    initializeWebSocket();
  } else {
    console.log('Пользователь не авторизован. Показываю страницу входа.');
  }
});

</script>

<template>
  <div class="page-wrapper">
    <!-- Шапка будет показываться только авторизованным пользователям -->
    <header v-if="authStore.isAuthenticated">
      <h1>Лабиринт Онлайн</h1>
      <div>
        <span>{{ authStore.username }}</span>
        <button @click="authStore.logout()">Выйти</button>
      </div>
    </header>

    <main class="content">
      <!-- Главный переключатель: показываем LoginView, если не авторизован -->
      <LoginView v-if="!authStore.isAuthenticated" />

      <!-- Иначе, показываем игру/лобби/загрузку -->
      <template v-else>
        <div v-if="gameStore.view === 'loading'">
          <h2>Подключение к серверу...</h2>
        </div>
        <LobbyView v-else-if="gameStore.isLobby" />
        <GameView v-else-if="gameStore.isGame" />
        <div v-else>
          <h2>Произошла ошибка: {{ gameStore.view }}</h2>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>

.page-wrapper {
  width: 90%;
  max-width: 1400px; /* Немного увеличим для игрового вида */
  margin: 20px auto;
  background-color: rgba(255, 255, 255, 0.9);
  border-radius: 15px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  padding: 20px 40px;
  backdrop-filter: blur(5px);
  border: 1px solid rgba(255, 255, 255, 0.4);
  min-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
}
.content {
  flex-grow: 1;
}
</style>