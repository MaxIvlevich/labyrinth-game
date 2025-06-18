package max.iv.labyrinth_game.websocket.dto;

public enum GameMessageType {
    CREATE_ROOM,
    JOIN_ROOM,
    PLAYER_ACTION_SHIFT, // Для действия сдвига
    PLAYER_ACTION_MOVE,  // Для действия перемещения

    // Сервер -> Клиент
    ERROR_MESSAGE,       // Сообщение об ошибке
    ROOM_CREATED,        // Комната создана (ответ на CREATE_ROOM)
    JOIN_SUCCESS,        // Успешное присоединение (ответ на JOIN_ROOM)
    GAME_STATE_UPDATE,   // Обновление состояния игры
    WELCOME_MESSAGE,
    GAME_OVER_MESSAGE,

    ROOM_LIST_UPDATE,
    GET_ROOM_LIST_REQUEST,
    LEAVE_ROOM
}
