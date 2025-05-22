package max.iv.labyrinth_game.service;

import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.enums.GamePhase;
import org.springframework.stereotype.Component;

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
}
