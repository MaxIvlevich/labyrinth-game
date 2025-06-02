package max.iv.labyrinth_game.websocket.dto;

import max.iv.labyrinth_game.model.game.enums.GamePhase;

public record RoomInfoDTO(
        String roomId,
        String roomName,
        int currentPlayerCount,
        int maxPlayers,
        GamePhase gamePhase // Чтобы показать, идет игра или ожидание
        // List<String> playerNames // Опционально, если хотим показывать имена в лобби
) {
}
