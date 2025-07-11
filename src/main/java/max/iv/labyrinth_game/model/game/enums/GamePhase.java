package max.iv.labyrinth_game.model.game.enums;

public enum GamePhase {
    WAITING_FOR_PLAYERS, // Ожидание игроков для старта
    PLAYER_SHIFT,        // Ход игрока: фаза сдвига тайла
    PLAYER_MOVE,         // Ход игрока: фаза перемещения фишки
    GAME_OVER;

    public boolean isActivePlayPhase() {
        return this == PLAYER_SHIFT || this == PLAYER_MOVE;
    }
}
