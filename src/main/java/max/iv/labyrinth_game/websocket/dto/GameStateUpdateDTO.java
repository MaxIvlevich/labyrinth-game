package max.iv.labyrinth_game.websocket.dto;

import max.iv.labyrinth_game.dto.geme.BoardDTO;
import max.iv.labyrinth_game.dto.geme.PlayerDTO;
import max.iv.labyrinth_game.dto.geme.PointDTO;
import max.iv.labyrinth_game.model.game.enums.GamePhase;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record GameStateUpdateDTO (
        GameMessageType type,
        String roomId,
        String roomName,
        GamePhase currentPhase,
        PlayerDTO currentPlayer,
        List<PlayerDTO> players,
        BoardDTO board, // Используем ваш существующий BoardDTO
        UUID winnerId,
        String winnerName,
        Set<PointDTO> reachableCells,
        Integer myCurrentTargetMarkerId

) {
    // Этот конструктор может понадобиться для обратной совместимости или тестов,
    // но в новой логике мы будем использовать канонический конструктор record-а.
    public GameStateUpdateDTO(
            String roomId, String roomName, GamePhase currentPhase, PlayerDTO currentPlayer,
            List<PlayerDTO> players, BoardDTO board, UUID winnerId, String winnerName) {
        this(
                GameMessageType.GAME_STATE_UPDATE, roomId, roomName, currentPhase,
                currentPlayer, players, board, winnerId, winnerName,
                null, null // Новые поля по умолчанию null
        );
    }
}
