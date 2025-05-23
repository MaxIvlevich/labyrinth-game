package max.iv.labyrinth_game.websocket.dto;

import max.iv.labyrinth_game.dto.BoardDTO;
import max.iv.labyrinth_game.dto.PlayerDTO;
import max.iv.labyrinth_game.model.enums.GamePhase;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;

import java.util.List;
import java.util.UUID;

public record GameStateUpdateDTO (
        GameMessageType type,
        String roomId,
        GamePhase currentPhase,
        UUID currentPlayerId,
        List<PlayerDTO> players,
        BoardDTO board,
        UUID winnerId,
        String winnerName
) {

    public GameStateUpdateDTO(String roomId, GamePhase currentPhase, UUID currentPlayerId, List<PlayerDTO> players, BoardDTO board) {
        this(GameMessageType.GAME_STATE_UPDATE, roomId, currentPhase, currentPlayerId, players, board, null, null);
    }

    public GameStateUpdateDTO(String roomId, GamePhase currentPhase, List<PlayerDTO> players, BoardDTO board, UUID winnerId, String winnerName) {
        this(GameMessageType.GAME_STATE_UPDATE, roomId, currentPhase, null, players, board, winnerId, winnerName);
    }
}
