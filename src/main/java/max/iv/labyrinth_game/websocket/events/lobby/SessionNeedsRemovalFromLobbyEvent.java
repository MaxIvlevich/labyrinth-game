package max.iv.labyrinth_game.websocket.events.lobby;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
@Getter
public class SessionNeedsRemovalFromLobbyEvent extends ApplicationEvent {
    private final String sessionId;

    public SessionNeedsRemovalFromLobbyEvent(Object source, String sessionId) {
        super(source);
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID cannot be null or blank for removal event");
        }
        this.sessionId = sessionId;
    }
}
