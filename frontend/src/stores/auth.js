import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { http } from '@/api/http.js';
import { closeWebSocket } from '@/api/websocket.js';

export const useAuthStore = defineStore('auth', () => {
    // === СОСТОЯНИЕ ===
    const accessToken = ref(localStorage.getItem('accessToken'));
    const refreshToken = ref(localStorage.getItem('refreshToken'));
    const username = ref(localStorage.getItem('username'));
    const userId = ref(localStorage.getItem('userId'));

    // === ГЕТТЕРЫ ===
    const isAuthenticated = computed(() => !!accessToken.value);

    // === ДЕЙСТВИЯ ===

    // Сохраняет данные после успешного логина
    function setAuthData(data) {
        accessToken.value = data.accessToken;
        refreshToken.value = data.refreshToken;
        username.value = data.username;
        userId.value = data.userId;

        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        localStorage.setItem('username', data.username);
        localStorage.setItem('userId', data.userId);
    }

    // Очищает данные при выходе
    function clearAuthData() {
        console.log("%cОЧИСТКА AUTH ДАННЫХ!", "color: red; font-weight: bold;");
        console.trace();
        accessToken.value = null;
        refreshToken.value = null;
        username.value = null;
        userId.value = null;

        localStorage.clear();

    }


    async function login(usernameOrEmail, password) {
        try {
            const data = await http('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ usernameOrEmail, password })
            });
            setAuthData(data);
            return true;
        } catch (error) {
            return error.message;
        }
    }
    async function register(username, email, password) {
        try {
            await http('/api/auth/signup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, email, password })
            });
            return true;
        } catch (error) {
            return error.message;
        }
    }

    // Выход пользователя
    async function logout() {
        closeWebSocket();
        try {
            await http('/api/auth/logout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken: refreshToken.value })
            });
        } catch (error) {
            console.error("Ошибка при выходе с сервера, но локальные данные будут очищены", error.message);
        } finally {
            clearAuthData();
        }
    }
    async function handleRefreshToken() {
        try {
            if (!refreshToken.value) return false;

            const data = await http('/api/auth/refresh', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken: refreshToken.value })
            });

            setAuthData(data);
            return true;
        } catch (error) {
            return false;
        }
    }


    return {
        accessToken,
        refreshToken,
        username,
        isAuthenticated,
        login,
        logout,
        register,
        handleRefreshToken,
        setAuthData, // Экспортируем для обновления токена
    };
});