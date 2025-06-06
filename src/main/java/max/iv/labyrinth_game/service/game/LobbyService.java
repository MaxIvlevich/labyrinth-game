package max.iv.labyrinth_game.service.game;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.websocket.LobbyBroadcaster;
import max.iv.labyrinth_game.websocket.events.lobby.PlayerNeedsRemovalFromLobbyEvent;
import max.iv.labyrinth_game.websocket.events.lobby.SessionNeedsRemovalFromLobbyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

@Slf4j
@Service
public class LobbyService {

    private final LobbyBroadcaster broadcaster;

    @Autowired
    public LobbyService(LobbyBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
        log.info("LobbyService initialized.");
    }

    public void addSessionToLobby(WebSocketSession session, UUID userId) {
        if (session == null ) return;
        if (!broadcaster.containsSession(session.getId())) {
            broadcaster.addSession(session.getId(), userId);
        }else {
            log.debug("Session {} already in lobby tracking. Not adding again via LobbyService.", session.getId());
        }
    }
    @EventListener
    public void handleSessionRemovalRequest(SessionNeedsRemovalFromLobbyEvent event) {
        log.info("LobbyService handling SessionNeedsRemovalFromLobbyEvent for session ID: {}", event.getSessionId());
        broadcaster.removeSession(event.getSessionId());
    }

    @EventListener
    public void handlePlayerRemovalRequest(PlayerNeedsRemovalFromLobbyEvent event) {
        log.info("LobbyService handling PlayerNeedsRemovalFromLobbyEvent for player ID: {}", event.getPlayerId());
        broadcaster.removePlayerFromLobby(event.getPlayerId());
    }
}
