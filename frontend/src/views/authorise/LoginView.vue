<script setup>
import {ref} from 'vue';
import { useAuthStore } from '@/stores/auth.js';

const authStore = useAuthStore();

const formType = ref('login');

const username = ref('');
const password = ref('');
const errorMessage = ref('');
const successMessage = ref('');

function switchForm(type) {
  formType.value = type;
  username.value = '';
  password.value = '';
  email.value = '';
  errorMessage.value = '';
  successMessage.value = '';
}

async function handleLogin() {
  errorMessage.value = '';
  const result = await authStore.login(username.value, password.value);

  if (result !== true) {
    errorMessage.value = result;
  }
}

async function handleRegister() {
  errorMessage.value = '';
  successMessage.value = '';
  const result = await authStore.register(username.value, email.value, password.value);
  if (result === true) {
    successMessage.value = 'Регистрация прошла успешно! Теперь вы можете войти.';
    switchForm('login'); // Автоматически переключаем на форму входа
  } else {
    errorMessage.value = result;
  }
}
</script>

<template>
  <div class="login-container">
    <template v-if="formType === 'login'">
      <h2>Вход в Лабиринт</h2>
      <form @submit.prevent="handleLogin">
        <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>
        <div v-if="successMessage" class="success-message">{{ successMessage }}</div>
        <div class="form-group">
          <label for="username">Имя пользователя</label>
          <input type="text" id="username" v-model="username" required>
        </div>
        <div class="form-group">
          <label for="password">Пароль</label>
          <input type="password" id="password" v-model="password" required>
        </div>
        <button type="submit" class="btn btn-primary">Войти</button>
        <p class="switch-form-text">
          Нет аккаунта? <a @click="switchForm('register')">Зарегистрироваться</a>
        </p>
      </form>
    </template>
    <template v-if="formType === 'register'">
      <h2>Регистрация</h2>
      <form @submit.prevent="handleRegister">
        <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>

        <div class="form-group">
          <label for="reg-username">Имя пользователя (3-20 симв.)</label>
          <input type="text" id="reg-username" v-model="username" required>
        </div>
        <div class="form-group">
          <label for="reg-email">Email</label>
          <input type="email" id="reg-email" v-model="email" required>
        </div>
        <div class="form-group">
          <label for="reg-password">Пароль (6-40 симв.)</label>
          <input type="password" id="reg-password" v-model="password" required>
        </div>
        <button type="submit" class="btn-primary">Создать аккаунт</button>
        <p class="switch-form-text">
          Уже есть аккаунт? <a @click="switchForm('login')">Войти</a>
        </p>
      </form>
    </template>
  </div>
</template>

<style scoped>
/* Сюда можно скопировать ваши стили для .login-container и .form-group из старого style.css */
.login-container {
  width: 100%;
  max-width: 400px;
  padding: 30px 40px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  margin: 40px auto;
}

.login-container h2 {
  text-align: center;
  margin-bottom: 25px;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
  font-size: 0.9em;
}

input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ccc;
  border-radius: 4px;
}

button.btn-primary {
  width: 100%;
  padding: 12px;
  background-color: #28a745;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 1.1em;
  margin-top: 10px;
}

.error-message {
  color: #dc3545;
  background-color: #f8d7da;
  border: 1px solid #f5c6cb;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 20px;
  text-align: center;
}

.success-message {
  color: #155724;
  background-color: #d4edda;
  border: 1px solid #c3e6cb;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 20px;
  text-align: center;
}

.switch-form-text {
  text-align: center;
  margin-top: 20px;
  font-size: 0.9em;
}

.switch-form-text a {
  color: #007bff;
  cursor: pointer;
  text-decoration: underline;
}
</style>