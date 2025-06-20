package max.iv.labyrinth_game.exceptions;

public enum ErrorType {

    UNKNOWN_ERROR,
    VALIDATION_ERROR,
    UNAUTHORIZED,
    DOUBLE_SESSION_AUTHORIZED,
    NULL_REQUEST,

    // Ошибки, связанные с комнатой
    ROOM_NOT_FOUND,
    ROOM_IS_FULL,
    GAME_ALREADY_STARTED,

    // Ошибки, связанные с ходом
    NOT_YOUR_TURN,
    INVALID_PHASE_FOR_ACTION,
    INVALID_MOVE,
    INVALID_SHIFT;
}
