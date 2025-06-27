import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useAuthStore = defineStore('auth', () => {
    // === СОСТОЯНИЕ ===
    const accessToken = ref(localStorage.getItem('accessToken'));
    const refreshToken = ref(localStorage.getItem('refreshToken'));
    const username = ref(localStorage.getItem('username'));
    const userId = ref(localStorage.getItem('userId'));

    // === ГЕТТЕРЫ ===
    // Простой геттер, чтобы проверить, авторизован ли пользователь.
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
        accessToken.value = null;
        refreshToken.value = null;
        username.value = null;
        userId.value = null;

        localStorage.clear();
        // Мы не будем перезагружать страницу, Vue сам все перерисует
    }

    // Логин пользователя
    async function login(username, password) {
        try {
            // fetch сам будет проксирован через Vite
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ usernameOrEmail: username, password })
            });

            const data = await response.json();

            if (!response.ok) {
                // Если сервер вернул ошибку, пробрасываем ее дальше
                throw new Error(data.message || 'Ошибка входа');
            }

            // Если все хорошо, сохраняем данные
            setAuthData(data);
            return true;
        } catch (error) {
            console.error("Ошибка при логине:", error);
            // Возвращаем текст ошибки, чтобы показать его в форме
            return error.message;
        }
    }
    async function register(username, email, password) {
        try {
            const response = await fetch('/api/auth/signup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, email, password })
            });

            // Если бэкенд возвращает тело ответа при ошибке
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || 'Ошибка регистрации');
            }

            // Если регистрация прошла успешно, просто возвращаем true
            return true;
        } catch (error) {
            console.error("Ошибка при регистрации:", error);
            return error.message;
        }
    }

    // Выход пользователя
    async function logout() {
        try {
            await fetch('/api/auth/logout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken: refreshToken.value })
            });
        } catch (e) {
            console.error("Ошибка при выходе с сервера, но локальные данные будут очищены", e);
        } finally {
            // В любом случае очищаем локальные данные
            clearAuthData();
        }
    }
    async function handleRefreshToken() {
        const currentRefreshToken = localStorage.getItem('refreshToken');
        if (!currentRefreshToken) {
            console.log("Нет refresh-токена для обновления.");
            clearAuthData(); // Если нет refresh-токена, выходим из системы
            return false;
        }

        try {
            const response = await fetch('/api/auth/refresh', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken: currentRefreshToken })
            });

            const data = await response.json();

            if (!response.ok) {
                // Refresh-токен тоже протух или невалиден
                throw new Error(data.message || "Не удалось обновить токен");
            }

            // Успех! Сохраняем новую пару токенов
            setAuthData(data);
            console.log("Токены успешно обновлены.");
            return true;

        } catch (error) {
            console.error("Ошибка при обновлении токена:", error.message);
            // Не удалось обновить, значит, нужно полностью разлогинить пользователя
            clearAuthData();
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