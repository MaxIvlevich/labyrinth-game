package max.iv.labyrinth_game.websocket.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;
@Getter
public class PlayerNeedsRemovalFromLobbyEvent extends ApplicationEvent {
    private final UUID playerId;

    public PlayerNeedsRemovalFromLobbyEvent(Object source, UUID playerId) {
        super(source);
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID cannot be null for removal event");
        }
        this.playerId = playerId;
    }
}
