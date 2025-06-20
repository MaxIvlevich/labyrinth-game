package max.iv.labyrinth_game.websocket.dto;

import max.iv.labyrinth_game.dto.geme.BoardDTO;
import max.iv.labyrinth_game.dto.geme.PlayerDTO;
import max.iv.labyrinth_game.model.game.enums.GamePhase;

import java.util.List;
import java.util.UUID;

public record GameStateUpdateDTO (
        GameMessageType type,
        String roomId,

        String roomName,
        GamePhase currentPhase,
        UUID currentPlayerId,
        List<PlayerDTO> players,
        BoardDTO board,
        UUID winnerId,
        String winnerName
) {

    public GameStateUpdateDTO(String roomId, String roomName,GamePhase currentPhase, UUID currentPlayerId, List<PlayerDTO> players, BoardDTO board) {
        this(GameMessageType.GAME_STATE_UPDATE, roomId, roomName,currentPhase, currentPlayerId, players, board, null, null);
    }

    public GameStateUpdateDTO(String roomId, String roomName,GamePhase currentPhase, List<PlayerDTO> players, BoardDTO board, UUID winnerId, String winnerName) {
        this(GameMessageType.GAME_STATE_UPDATE, roomId,roomName, currentPhase, null, players, board, winnerId, winnerName);
    }
}
