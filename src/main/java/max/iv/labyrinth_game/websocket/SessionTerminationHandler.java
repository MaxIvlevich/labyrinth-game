package max.iv.labyrinth_game.websocket;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.service.game.GameService;
import max.iv.labyrinth_game.websocket.events.lobby.LobbyRoomListNeedsUpdateEvent;
import max.iv.labyrinth_game.websocket.events.lobby.PlayerNeedsRemovalFromLobbyEvent;
import max.iv.labyrinth_game.websocket.events.lobby.RoomStateNeedsBroadcastEvent;
import max.iv.labyrinth_game.websocket.events.lobby.SessionNeedsRemovalFromLobbyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import java.util.UUID;

@Slf4j
@Component
public class SessionTerminationHandler {
    private final GameService gameService;
    private final ApplicationEventPublisher eventPublisher;

    public SessionTerminationHandler(GameService gameService,
                                     ApplicationEventPublisher eventPublisher) {
        this.gameService = gameService;
        this.eventPublisher = eventPublisher;
    }

    public void handleSessionTermination(WebSocketSession session, UUID playerId, String roomId) {
        String sessionId = session.getId();
        if (playerId == null) {
            log.info("Handling termination for unauthenticated session {}", sessionId);
            log.debug("Publishing SessionNeedsRemovalFromLobbyEvent for session {}", sessionId);
            eventPublisher.publishEvent(new SessionNeedsRemovalFromLobbyEvent(this, sessionId));
            log.debug("Publishing LobbyRoomListNeedsUpdateEvent for unauthenticated session {} termination.", sessionId);
            eventPublisher.publishEvent(new LobbyRoomListNeedsUpdateEvent(this));
            return;
        }
        // Игрок был аутентифицирован (имеет playerId)
        try {
            if (roomId != null) {
                gameService.handlePlayerDisconnect(playerId, roomId);
                log.info("Player {} disconnected from room {}", playerId, roomId);

                log.debug("Publishing RoomStateNeedsBroadcastEvent for room {}", roomId);
                eventPublisher.publishEvent(new RoomStateNeedsBroadcastEvent(this, roomId));

                // Публикуем событие для обновления списка комнат в лобби
                log.debug("Publishing LobbyRoomListNeedsUpdateEvent due to player disconnect from room");
                eventPublisher.publishEvent(new LobbyRoomListNeedsUpdateEvent(this));
            } else {
                log.info("Player {} (session {}) was authenticated but not in a room. Initiating removal from lobby tracking.", playerId, sessionId);
                log.debug("Publishing PlayerNeedsRemovalFromLobbyEvent for player {}", playerId);
                // Публикуем событие
                eventPublisher.publishEvent(new PlayerNeedsRemovalFromLobbyEvent(this, playerId));
                log.debug("Publishing LobbyRoomListNeedsUpdateEvent due to player removal from lobby");
                // Публикуем событие
                eventPublisher.publishEvent(new LobbyRoomListNeedsUpdateEvent(this));
            }
        } catch (Exception e) {
            log.error("Error handling termination for player {}: {}", playerId, e.getMessage(), e);
        }
    }
}
