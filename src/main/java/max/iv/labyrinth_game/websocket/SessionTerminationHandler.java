package max.iv.labyrinth_game.websocket;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.service.game.GameService;
import max.iv.labyrinth_game.service.game.LobbyService;
import max.iv.labyrinth_game.websocket.events.LobbyRoomListNeedsUpdateEvent;
import max.iv.labyrinth_game.websocket.events.RoomStateNeedsBroadcastEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import java.util.UUID;

@Slf4j
@Component
public class SessionTerminationHandler {
    private final GameService gameService;
    private final LobbyService lobbyService;
    private final ApplicationEventPublisher eventPublisher;

    public SessionTerminationHandler(GameService gameService,
                                     LobbyService lobbyService,
                                     GameStateBroadcaster gameStateBroadcaster, ApplicationEventPublisher eventPublisher) {
        this.gameService = gameService;
        this.lobbyService = lobbyService;
        this.eventPublisher = eventPublisher;
    }

    public void handleSessionTermination(WebSocketSession session, UUID playerId, String roomId) {
        if (playerId == null) {
            // Игрок не был авторизован — просто удалим из лобби
            lobbyService.removeSessionFromLobby(session);
            log.info("Unauthenticated session {} removed from lobby", session.getId());
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
                log.info("Player {} not in room, only removing from lobby", playerId);
                lobbyService.removePlayerFromLobby(playerId);
                log.debug("Publishing LobbyRoomListNeedsUpdateEvent due to player removal from lobby");
                eventPublisher.publishEvent(new LobbyRoomListNeedsUpdateEvent(this));
            }
        } catch (Exception e) {
            log.error("Error handling termination for player {}: {}", playerId, e.getMessage(), e);
        }
    }
}
