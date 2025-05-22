package max.iv.labyrinth_game.service;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.Player;
import max.iv.labyrinth_game.model.enums.GamePhase;
import org.springframework.stereotype.Component;

import java.util.UUID;
@Slf4j
@Component
public class GameValidator {

    public void validateRoomBeforeGameStart(GameRoom room) {
        if (room.getPlayers().size() < 2) {
            throw new IllegalStateException("Not enough players to start the game in room: " + room.getRoomId());
        }
        if (room.getGamePhase() != GamePhase.WAITING_FOR_PLAYERS) {
            throw new IllegalStateException("Game already started or finished in room: " + room.getRoomId());
        }
    }

    public void validateGameInProgress(GameRoom room) {
        GamePhase currentPhase = room.getGamePhase();
        if (currentPhase != GamePhase.PLAYER_SHIFT &&
                currentPhase != GamePhase.PLAYER_MOVE) {
            log.warn("Validation failed: Game in room {} is not in an active play phase. Current phase: {}", room.getRoomId(), currentPhase);
            throw new IllegalStateException("Game is not in an active play phase in room: " + room.getRoomId());
        }
    }

    public void validatePlayerTurn(GameRoom room, UUID playerId) {
        Player currentPlayer = room.getCurrentPlayer();
        if (!currentPlayer.getId().equals(playerId)) {
            throw new IllegalStateException("It's not the turn of player: " + playerId);
        }
    }
}
