import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    //  порт, на котором будет работать фронтенд
    port: 5173,
    proxy: {
      // Все запросы, начинающиеся с /api, будут перенаправлены
      '/api': {
        target: 'http://localhost:8080', // это порт Spring Boot
        changeOrigin: true, //для  работы прокси
      },
      // Отдельная настройка для WebSocket
      '/game': {
        target: 'ws://localhost:8080',   //  порт  Spring Boot
        ws: true,
      }
    }
  },

  resolve: {
    alias: [
      { find: '@', replacement: new URL('./src', import.meta.url).pathname }
    ]
  }
})
