package max.iv.labyrinth_game.websocket.events.lobby;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
@Slf4j
@Getter
public class LobbyRoomListNeedsUpdateEvent extends ApplicationEvent {
    public LobbyRoomListNeedsUpdateEvent(Object source) {
        super(source);
    }
}
