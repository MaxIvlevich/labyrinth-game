package max.iv.labyrinth_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.service.GameService;
import max.iv.labyrinth_game.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> authenticatedSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdToPlayerId = new ConcurrentHashMap<>();
    private final Map<String, String> playerIdToSessionId = new ConcurrentHashMap<>();
    private final GameService gameService; // Сервис с игровой логикой
    private final ObjectMapper objectMapper; // Для сериализации/десериализации JSON
    private final RoomService roomService;

    @Autowired
    public GameWebSocketHandler(GameService gameService, ObjectMapper objectMapper, RoomService roomService) {
        this.gameService = gameService;
        this.objectMapper = objectMapper;
        this.roomService = roomService;
    }
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        authenticatedSessions.put(session.getId(), session);
        log.info("WebSocket connection established: Session ID = {}, Remote Address = {}", session.getId(), session.getRemoteAddress());
        // Можно отправить клиенту приветственное сообщение или его ID сессии
        // session.sendMessage(new TextMessage("Welcome! Your session ID is " + session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Received message from session {}: {}", session.getId(), payload);

        try {
            // Здесь будет логика разбора сообщения и вызова методов GameService
            // Например, десериализация JSON в объект команды/действия
            // GameAction gameAction = objectMapper.readValue(payload, GameAction.class);
            // processGameAction(session, gameAction);

            // Пока просто эхо для теста (или обработка простой команды)
            session.sendMessage(new TextMessage("Server received: " + payload));

        } catch (Exception e) {
            log.error("Error processing message from session {}: {}", session.getId(), payload, e);
            session.sendMessage(new TextMessage("Error processing your request: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        authenticatedSessions.remove(session.getId());
        log.info("WebSocket connection closed: Session ID = {}, Status = {}, Remote Address = {}",
                session.getId(), status, session.getRemoteAddress());

        // TODO: Обработать отключение игрока из GameService
        // String playerId = getPlayerIdFromSession(session); // Нужен механизм связи сессии с ID игрока
        // if (playerId != null) {
        //     gameService.handlePlayerDisconnect(playerId);
        // }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        // Можно также закрыть сессию или уведомить GameService
    }

    // --- Вспомогательные методы для отправки сообщений ---

    /**
     * Отправляет сообщение конкретной сессии.
     */
    public void sendMessageToSession(String sessionId, Object messagePayload) {
        WebSocketSession session = authenticatedSessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(messagePayload);
                session.sendMessage(new TextMessage(jsonMessage));
                log.debug("Sent message to session {}: {}", sessionId, jsonMessage);
            } catch (IOException e) {
                log.error("Error sending message to session {}: {}", sessionId, messagePayload, e);
            }
        } else {
            log.warn("Attempted to send message to closed or non-existent session: {}", sessionId);
        }
    }

    /**
     * Отправляет сообщение всем активным сессиям.
     * (Это может быть неэффективно для игры с комнатами, лучше отправлять только игрокам в комнате)
     */
    public void broadcastMessage(Object messagePayload) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(messagePayload);
        } catch (IOException e) {
            log.error("Error serializing broadcast message: {}", messagePayload, e);
            return;
        }

        authenticatedSessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(jsonMessage));
                } catch (IOException e) {
                    log.error("Error broadcasting message to session {}: {}", session.getId(), jsonMessage, e);
                }
            }
        });
        log.debug("Broadcasted message: {}", jsonMessage);
    }

    /**
     * Отправляет сообщение всем сессиям в указанной игровой комнате.
     * Для этого нам нужно будет как-то связать WebSocketSession с GameRoom или Player.
     * Пока это заглушка.
     */
    public void sendMessageToRoom(String roomId, Object messagePayload) {
        // TODO: Реализовать механизм получения сессий игроков в комнате 'roomId'
        // Например, GameRoom может хранить Set<String> sessionIds своих игроков,
        // или у Player будет поле String sessionId.

        log.info("Attempting to send message to room {}: {}", roomId, messagePayload);
        // Примерная логика (потребует доработки моделей):
        // GameRoom room = gameService.getRoom(roomId);
        // if (room != null) {
        //     String jsonMessage = objectMapper.writeValueAsString(messagePayload);
        //     for (Player player : room.getPlayers()) {
        //         if (player.getWebSocketSessionId() != null) { // Предполагаем, что у Player есть это поле
        //             sendMessageToSession(player.getWebSocketSessionId(), jsonMessage); // Отправляем уже сериализованное
        //         }
        //     }
        // }
        // Пока что для демонстрации отправим всем, как broadcast
        broadcastMessage(messagePayload); // ЗАМЕНИТЬ НА РЕАЛЬНУЮ ЛОГИКУ ОТПРАВКИ В КОМНАТУ
    }

}
