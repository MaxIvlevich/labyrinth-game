package max.iv.labyrinth_game.websocket.events.lobby;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
@Slf4j
@Getter
public class RoomStateNeedsBroadcastEvent extends ApplicationEvent {

    private final String roomId;
    public RoomStateNeedsBroadcastEvent(Object source, String roomId) {
        super(source);
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room ID cannot be null or blank");
        }
        this.roomId = roomId;
    }
}
