package max.iv.labyrinth_game.websocket.events.lobby;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;
@Getter
public class PlayerReturnedToLobbyEvent extends ApplicationEvent {
    private final WebSocketSession session;
    private final UUID playerId;

    public PlayerReturnedToLobbyEvent(Object source, WebSocketSession session, UUID playerId) {
        super(source);
        this.session = session;
        this.playerId = playerId;
    }
}
