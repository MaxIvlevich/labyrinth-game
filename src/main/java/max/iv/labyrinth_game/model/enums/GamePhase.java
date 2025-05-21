package max.iv.labyrinth_game.model.enums;

public enum GamePhase {
    WAITING_FOR_PLAYERS, // Ожидание игроков для старта
    PLAYER_SHIFT,        // Ход игрока: фаза сдвига тайла
    PLAYER_MOVE,         // Ход игрока: фаза перемещения фишки
    GAME_OVER
}
