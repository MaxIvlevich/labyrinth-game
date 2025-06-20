package max.iv.labyrinth_game.service.game;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.exceptions.ErrorType;
import max.iv.labyrinth_game.exceptions.game.GameLogicException;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.model.game.enums.GamePhase;
import org.springframework.stereotype.Component;

import java.util.UUID;
@Slf4j
@Component
public class GameValidator {

    public void validateRoomBeforeGameStart(GameRoom room) {
        if (room.getPlayers().size() < 2) {
            throw new GameLogicException("Not enough players to start the game", ErrorType.UNKNOWN_ERROR);
        }
        if (room.getGamePhase() != GamePhase.WAITING_FOR_PLAYERS) {
            throw new GameLogicException("Game already started or finished", ErrorType.GAME_ALREADY_STARTED);
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
            throw new GameLogicException("It's not your turn", ErrorType.NOT_YOUR_TURN);
        }
    }

    public void validateRoomForJoin(GameRoom room) {
        if (room.isFull()) {
            throw new GameLogicException("Room is full", ErrorType.ROOM_IS_FULL);
        }
        if (room.getGamePhase() != GamePhase.WAITING_FOR_PLAYERS) {
            throw new GameLogicException("Game has already started", ErrorType.GAME_ALREADY_STARTED);
        }
    }
}
