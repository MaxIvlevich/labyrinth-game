package max.iv.labyrinth_game.service.game;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.websocket.LobbyBroadcaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
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
        if (session == null || userId == null) return;
        if (!broadcaster.containsSession(session.getId())) {
            broadcaster.addSession(session.getId(), userId);
        }
    }

    public void removeSessionFromLobby(WebSocketSession session) {
        if (session != null) {
            broadcaster.removeSession(session.getId());
        }
    }

    public void broadcastRoomListToLobby() {
        broadcaster.broadcastRoomList();
    }

    public void removePlayerFromLobby(UUID playerId) {
        broadcaster.removePlayerFromLobby(playerId);
    }
}
