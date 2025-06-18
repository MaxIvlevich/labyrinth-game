package max.iv.labyrinth_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.websocket.dto.ErrorMessageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SessionManager{

    private final Map<String, WebSocketSession> authenticatedGameSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> allActiveSessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerIdToSessionId = new ConcurrentHashMap<>();
    private final SessionTerminationHandler terminationHandler;
    private final ObjectMapper objectMapper;
    public static final String PLAYER_ID_ATTRIBUTE_KEY = "playerId";
    public static final String ROOM_ID_ATTRIBUTE_KEY = "roomId";
    public static final String USER_ID_ATTRIBUTE_KEY = "userId";
    public static final String USER_NAME_ATTRIBUTE_KEY = "userName";

    @Autowired
    public SessionManager(ObjectMapper objectMapper,SessionTerminationHandler terminationHandler) {
        this.objectMapper = objectMapper;
        this.terminationHandler = terminationHandler;
        log.info("SessionManager initialized.");
    }

    public void registerSession(WebSocketSession session) {
        if (session == null) {
            log.warn("Attempted to register a null session.");
            return;
        }
        allActiveSessions.put(session.getId(), session);
        log.info("Session {} connected and registered. Total active sessions: {}", session.getId(), allActiveSessions.size());
    }

    public void associatePlayerWithSession(WebSocketSession session, UUID playerId, String roomId) {
        if (session == null || playerId == null || roomId == null) {
            log.error("Cannot associate session: one or more parameters are null. Session: {}, PlayerID: {}, RoomID: {}",
                    session != null ? session.getId() : "null", playerId, roomId);
            return;
        }
        String sessionId = session.getId();
        if (!allActiveSessions.containsKey(sessionId) || !allActiveSessions.get(sessionId).isOpen()) {
            log.warn("Attempted to associate a session {} that is not active or not registered. Aborting association.", sessionId);
            if (allActiveSessions.containsKey(sessionId) && !allActiveSessions.get(sessionId).isOpen()) {
                allActiveSessions.remove(sessionId);
            }
            return;
        }
        session.getAttributes().put(PLAYER_ID_ATTRIBUTE_KEY, playerId);
        session.getAttributes().put(ROOM_ID_ATTRIBUTE_KEY, roomId);

        authenticatedGameSessions.put(sessionId, session);
        playerIdToSessionId.put(playerId, sessionId);

        log.info("Associated session {} with Player ID {} and Room ID {}", sessionId, playerId, roomId);
    }

    public void unregisterSession(WebSocketSession session) {
        if (session == null) {
            log.warn("Attempted to unregister a null session.");
            return;
        }

        String sessionId = session.getId();
        allActiveSessions.remove(sessionId);
        authenticatedGameSessions.remove(sessionId);

        UUID playerId = (UUID) session.getAttributes().get(USER_ID_ATTRIBUTE_KEY);
        String roomId = (String) session.getAttributes().get(ROOM_ID_ATTRIBUTE_KEY);

        if (playerId != null) {
            playerIdToSessionId.remove(playerId);
            terminationHandler.handleSessionTermination(session, playerId, roomId);
        } else {
            terminationHandler.handleSessionTermination(session, null, null);
        }

        log.info("Session {} unregistered. Total active sessions: {}, Authenticated game sessions: {}",
                sessionId, allActiveSessions.size(), authenticatedGameSessions.size());
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
        return (UUID) session.getAttributes().get(USER_ID_ATTRIBUTE_KEY);
    }

    public String getRoomIdBySession(WebSocketSession session) {
        if (session == null) return null;
        return (String) session.getAttributes().get(ROOM_ID_ATTRIBUTE_KEY);
    }

    public Map<String, WebSocketSession> getAuthenticatedGameSessions() {
        return new ConcurrentHashMap<>(authenticatedGameSessions);
    }

    public void sendMessageToSession(WebSocketSession session, Object payload, ObjectMapper objectMapper) {
        if (session != null && session.isOpen()) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(jsonMessage));
                log.debug("Sent (via SessionManager) to session {}: {}", session.getId(), jsonMessage.length() > 100 ? jsonMessage.substring(0,100) + "..." : jsonMessage);
            } catch (IOException e) {
                log.error("Error sending message via SessionManager to session {}: PayloadClass={}, Error={}",
                        session.getId(), payload.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    public void sendErrorMessageToSession(WebSocketSession session, String messageText, ObjectMapper objectMapper) {
        sendMessageToSession(session, new ErrorMessageResponse(messageText), objectMapper);
    }

    public <T> boolean validateRequestAndSendError(WebSocketSession session, T requestDto, Validator validator, String actionName) {
        Set<ConstraintViolation<T>> violations = validator.validate(requestDto);
        if (!violations.isEmpty()) {
            String errorMessages = violations.stream()
                    .map(v -> "'" + v.getPropertyPath() + "': " + v.getMessage())
                    .collect(Collectors.joining("; "));
            String fullErrorMessage = "Validation failed for " + actionName + " request: " + errorMessages;

            log.warn("{} from session {}: {}", fullErrorMessage, session.getId(), errorMessages);
            sendErrorMessageToSession(session, fullErrorMessage);
            return true;
        }
        return false;
    }

    public void sendErrorMessageToSession(WebSocketSession session, String messageText) {
        if (session != null && session.isOpen()) {
            try {
                ErrorMessageResponse errorResponse = new ErrorMessageResponse(messageText); // Предполагаем DTO
                String jsonMessage = this.objectMapper.writeValueAsString(errorResponse);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                log.error("Failed to send error message to session {}: {}", session.getId(), messageText, e);
            }
        }
    }

    public WebSocketSession getAuthenticatedGameSessionById(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.trace("Attempted to get authenticated game session with null or blank sessionId.");
            return null;
        }
        // Сначала проверяем в общем пуле активных сессий, чтобы не работать с закрытыми
        WebSocketSession activeSession = getActiveSessionById(sessionId);
        if (activeSession == null) {
            // Если ее нет даже в общем пуле активных, то и в authenticatedGameSessions ее быть не должно (или она устарела)
            if(authenticatedGameSessions.containsKey(sessionId)){
                log.warn("Session {} found in authenticatedGameSessions but not in allActiveSessions. Cleaning up authenticatedGameSessions.", sessionId);
                authenticatedGameSessions.remove(sessionId);
                // playerIdToSessionId также нужно почистить, если такое возможно
            }
            return null;
        }
        // Теперь проверяем, есть ли эта активная сессия в списке аутентифицированных
        WebSocketSession gameSession = authenticatedGameSessions.get(sessionId);
        if (gameSession != null && gameSession.isOpen()) { // Убеждаемся, что это та же сессия и она открыта
            return gameSession;
        } else if (gameSession != null) { // Найдена, но закрыта
            log.warn("Session {} found in authenticatedGameSessions but is not open. Removing stale entry.", sessionId);
            authenticatedGameSessions.remove(sessionId);
            UUID playerId = getPlayerIdBySessionInternal(gameSession);
            if (playerId != null) {
                playerIdToSessionId.remove(playerId, sessionId);
            }
            return null;
        }
        // Сессия активна, но не аутентифицирована для игры
        log.trace("Session {} is active but not found in authenticatedGameSessions.", sessionId);
        return null;
    }

    private UUID getPlayerIdBySessionInternal(WebSocketSession session) {
        if (session == null) return null;
        return (UUID) session.getAttributes().get(PLAYER_ID_ATTRIBUTE_KEY);
    }

    public WebSocketSession getActiveSessionById(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.trace("Attempted to get active session with null or blank sessionId.");
            return null;
        }
        WebSocketSession session = allActiveSessions.get(sessionId);
        if (session != null && session.isOpen()) {
            return session;
        } else if (session != null) { // Сессия есть в мапе, но закрыта
            log.warn("Session {} found in allActiveSessions but is not open. Cleaning up.", sessionId);
            allActiveSessions.remove(sessionId); // Удаляем устаревшую запись
            if (authenticatedGameSessions.containsKey(sessionId)) {
                authenticatedGameSessions.remove(sessionId);
                UUID playerId = getPlayerIdBySessionInternal(session); // Внутренний метод для получения playerId из атрибутов
                if (playerId != null) {
                    playerIdToSessionId.remove(playerId, sessionId); // Удаляем конкретную пару
                }
                log.warn("Also removed stale session {} from authenticatedGameSessions.", sessionId);
            }
            return null;
        }
        log.trace("No active session found in allActiveSessions for sessionId: {}", sessionId);
        return null;
    }

    public void returnPlayerToLobby(String sessionId) {
        WebSocketSession session = allActiveSessions.get(sessionId);
        if (session != null && session.isOpen()) {
            Object removedRoomId = session.getAttributes().remove(ROOM_ID_ATTRIBUTE_KEY);
            if (removedRoomId != null) {
                log.info("Session {} has been disassociated from room {} and returned to lobby state.", sessionId, removedRoomId);
            } else {
                log.debug("Session {} was already in lobby state, no room ID to remove.", sessionId);
            }
        } else {
            log.warn("Attempted to return a non-active or null session {} to lobby.", sessionId);
        }
    }
}
