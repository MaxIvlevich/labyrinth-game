package max.iv.labyrinth_game.websocket.events.room;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;
@Getter
public class PlayerReconnectedToRoomEvent extends ApplicationEvent {
    private final WebSocketSession session;
    private final UUID playerId;
    private final String roomId;

    public PlayerReconnectedToRoomEvent(Object source,WebSocketSession session, UUID playerId, String roomId) {
        super(source);
        this.session = session;
        this.playerId = playerId;
        this.roomId = roomId;
    }
}
