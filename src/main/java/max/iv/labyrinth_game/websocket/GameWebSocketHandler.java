package max.iv.labyrinth_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.auth.JSONMessageToFront;
import max.iv.labyrinth_game.service.game.LobbyService;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.ErrorMessageResponse;
import max.iv.labyrinth_game.websocket.messageHandlers.WebSocketMessageHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketMessageHandler> messageHandlers;
    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;

    private final LobbyService lobbyService;

    @Autowired
    public GameWebSocketHandler(List<WebSocketMessageHandler> messageHandlers,
                                ObjectMapper objectMapper,
                                SessionManager sessionManager, LobbyService lobbyService) {
        this.messageHandlers = messageHandlers;
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.lobbyService = lobbyService;
        log.info("GameWebSocketHandler initialized with {} message handlers.", messageHandlers.size());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionManager.registerSession(session);
        log.info("CONNECTION ESTABLISHED: Session ID = {}, Remote Address = {}", session.getId(), session.getRemoteAddress());
        try {
            UUID userId = (UUID) session.getAttributes().get(SessionManager.USER_ID_ATTRIBUTE_KEY);

            if (userId != null) {
                sessionManager.closeExistingSessionForPlayer(userId, session.getId());
                sessionManager.mapPlayerToSession(userId, session.getId());
                lobbyService.addSessionToLobby(session, userId);
            }else {
                log.warn("Session {} connected, but user details not found...", session.getId());
            }
            JSONMessageToFront welcomeMsg = new JSONMessageToFront(
                    "Welcome to Labyrinth Game! Please create or join a room.");
            sessionManager.sendMessageToSession(session, welcomeMsg, objectMapper);

        } catch (Exception e) {
            log.error("Error sending welcome message to session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("CONNECTION CLOSED: Session ID = {}, Status = {}, Remote Address = {}",
                session.getId(), status, session.getRemoteAddress());
        sessionManager.unregisterSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
       log.error("TRANSPORT ERROR for session {}: {}", session.getId(), exception.getMessage(), exception);
        sessionManager.handleTransportError(session, exception);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("MESSAGE RECEIVED from session {}: {}", session.getId(), payload);
        try {
            BaseMessage baseMessage = objectMapper.readValue(payload, BaseMessage.class);

            boolean handled = false;
            for (WebSocketMessageHandler handler : messageHandlers) {
                if (handler.supports(baseMessage)) {
                    handler.handle(session, baseMessage);
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                log.warn("No handler found for message type: {} from session {}", baseMessage.getType(), session.getId());
                sendErrorMessageToSession(session, "Unknown or unsupported message type: " + baseMessage.getType());
            }
        } catch (IOException e) {
            // Ошибка при отправке ответного сообщения
            log.error("Error sending echo message to session {}: {}", session.getId(), e.getMessage(), e);
        } catch (Exception e) {
            // Любая другая непредвиденная ошибка при обработке
            log.error("Unexpected error in handleTextMessage for session {}: {}", session.getId(), e.getMessage(), e);
            // Можно отправить сообщение об ошибке клиенту, если сессия еще открыта
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage("Server error processing your message."));
                } catch (IOException ex) {
                    log.error("Failed to send error notification to session {}: {}", session.getId(), ex.getMessage());
                }
            }
        }
    }

    private void sendErrorMessageToSession(WebSocketSession session, String messageText) {
        if (session != null && session.isOpen()) {
            try {
                ErrorMessageResponse errorResponse = new ErrorMessageResponse(messageText); // Предполагаем такой DTO
                String jsonMessage = objectMapper.writeValueAsString(errorResponse);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                log.error("Failed to send error message to session {}: {}", session.getId(), messageText, e);
            }
        }
    }
}


