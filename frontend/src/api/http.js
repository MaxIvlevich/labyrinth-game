import { useAuthStore } from '@/stores/auth.js';
/**
 * Обертка над стандартным fetch для работы с API нашего приложения.
 * Автоматически обрабатывает ошибки, парсит JSON и может обрабатывать
 * ошибки аутентификации.
 *
 * @param {string} url - URL эндпоинта (например, '/api/auth/login').
 * @param {object} options - Опции для fetch (method, headers, body).
 * @returns {Promise<any>} - Возвращает Promise, который разрешается с данными из JSON.
 * @throws {Error} - Выбрасывает ошибку в случае неудачи, которую можно поймать через .catch() или try/catch.
 */
export async function http(url, options = {}) {
    try {
        const response = await fetch(url, options);

        if (response.ok) {
            if (response.status === 204) {
                return null;
            }
            return await response.json();
        }

        if (response.status === 401 || response.status === 403) {
            const authStore = useAuthStore();
            authStore.logout(); // logout  очистит localStorage
            console.error("Ошибка аутентификации. Пользователь был разлогинен.");
        }
        // Пытаемся получить текст ошибки из тела ответа
        const errorData = await response.json().catch(() => ({
            message: `Запрос завершился с ошибкой: ${response.status} ${response.statusText}`
        }))
        // Пробрасываем ошибку дальше, чтобы ее можно было поймать в месте вызова
        throw new Error(errorData.message || 'Произошла неизвестная ошибка');

    } catch (error) {
        // Ловим как сетевые ошибки (fetch не сработал), так и проброшенные выше
        console.error(`Ошибка выполнения запроса к ${url}:`, error.message);
        // Пробрасываем ошибку дальше, чтобы вызывающий код знал о проблеме
        throw error;
    }
}