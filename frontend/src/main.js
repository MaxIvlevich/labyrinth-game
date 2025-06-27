import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import './style.css' // TODO

const pinia = createPinia() // 2. Создаем экземпляр Pinia
const app = createApp(App)

app.use(pinia) // 3. Подключаем Pinia к приложению Vue
app.mount('#app')
