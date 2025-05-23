package max.iv.labyrinth_game.websocket.messageHandlers;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.service.GameService;
import max.iv.labyrinth_game.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SessionManager{

    private final Map<String, WebSocketSession> authenticatedGameSessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerIdToSessionId = new ConcurrentHashMap<>();
    private final GameService gameService;
    private final RoomService roomService;
    private final GameStateBroadcaster gameStateBroadcaster;
    public static final String PLAYER_ID_ATTRIBUTE_KEY = "playerId";
    public static final String ROOM_ID_ATTRIBUTE_KEY = "roomId";

    @Autowired
    public SessionManager(GameService gameService, RoomService roomService, @Lazy GameStateBroadcaster gameStateBroadcaster) {
        this.gameService = gameService;
        this.roomService = roomService;
        this.gameStateBroadcaster = gameStateBroadcaster; // Сохраняем для будущего использования
        log.info("SessionManager initialized.");
    }

    public void registerSession(WebSocketSession session) {
        log.debug("Session {} connected, awaiting game association.", session.getId());
    }

    public void associatePlayerWithSession(WebSocketSession session, UUID playerId, String roomId) {
        if (session == null || playerId == null || roomId == null) {
            log.error("Cannot associate session: one or more parameters are null. Session: {}, PlayerID: {}, RoomID: {}",
                    session != null ? session.getId() : "null", playerId, roomId);
            return;
        }
        String sessionId = session.getId();

        session.getAttributes().put(PLAYER_ID_ATTRIBUTE_KEY, playerId);
        session.getAttributes().put(ROOM_ID_ATTRIBUTE_KEY, roomId);

        authenticatedGameSessions.put(sessionId, session);
        playerIdToSessionId.put(playerId, sessionId);

        log.info("Associated session {} with Player ID {} and Room ID {}", sessionId, playerId, roomId);
    }

    public void unregisterSession(WebSocketSession session) {
        if (session == null) return;
        String sessionId = session.getId();
        // Удаляем сессию из активных игровых сессий
        authenticatedGameSessions.remove(sessionId);

        UUID playerId = (UUID) session.getAttributes().get(PLAYER_ID_ATTRIBUTE_KEY);
        String roomId = (String) session.getAttributes().get(ROOM_ID_ATTRIBUTE_KEY);

        if (playerId != null) {
            playerIdToSessionId.remove(playerId); // Удаляем связь игрок -> сессия
            log.info("Player {} (session {}) disconnected.", playerId, sessionId);

            if (roomId != null) {
                try {
                    gameService.handlePlayerDisconnect(playerId, roomId);
                    log.info("Notified GameService about disconnect of player {} from room {}", playerId, roomId);
                    // Инициируем рассылку обновленного состояния комнаты
                    if (gameStateBroadcaster != null) {
                        gameStateBroadcaster.broadcastGameStateToRoom(roomId);
                    } else {
                        log.warn("GameStateBroadcaster is null in SessionManager, cannot broadcast after disconnect.");
                    }
                } catch (Exception e) {
                    log.error("Error during player disconnect handling for player {} in room {}: {}",
                            playerId, roomId, e.getMessage(), e);
                }
            } else {
                log.warn("RoomId not found in session attributes for disconnected player {} (session {}).",
                        playerId, sessionId);
            }
        } else {
            log.warn("No playerId found in session attributes for closed session {}.", sessionId);
        }
        log.info("Session {} fully unregistered.", sessionId);
    }

    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        unregisterSession(session);
    }
    public WebSocketSession getSessionByPlayerId(UUID playerId) {
        if (playerId == null) return null;
        String sessionId = playerIdToSessionId.get(playerId);
        if (sessionId != null) {
            WebSocketSession session = authenticatedGameSessions.get(sessionId);
            if (session != null && session.isOpen()) {
                return session;
            } else if (session != null) { // Сессия есть в мапе, но закрыта
                log.warn("Session {} for player {} found but is not open. Cleaning up.", sessionId, playerId);
                // Очистка устаревших записей, если сессия закрылась без вызова unregisterSession
                authenticatedGameSessions.remove(sessionId);
                playerIdToSessionId.remove(playerId);
                return null;
            }
        }
        log.trace("No active session found for playerId: {}", playerId); // trace, т.к. может быть частым
        return null;
    }
    public UUID getPlayerIdBySession(WebSocketSession session) {
        if (session == null) return null;
        return (UUID) session.getAttributes().get(PLAYER_ID_ATTRIBUTE_KEY);
    }
    public String getRoomIdBySession(WebSocketSession session) {
        if (session == null) return null;
        return (String) session.getAttributes().get(ROOM_ID_ATTRIBUTE_KEY);
    }
    public Map<String, WebSocketSession> getAuthenticatedGameSessions() {
        return authenticatedGameSessions;
    }
}
