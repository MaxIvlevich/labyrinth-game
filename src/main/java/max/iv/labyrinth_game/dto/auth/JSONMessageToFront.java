package max.iv.labyrinth_game.dto.auth;

import lombok.Getter;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;

@Getter
public class JSONMessageToFront {

    private final GameMessageType type = GameMessageType.WELCOME_MESSAGE; // Новый тип
    private final String message;

    public JSONMessageToFront(String message) {
        this.message = message;

    }
}
