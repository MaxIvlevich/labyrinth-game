package max.iv.labyrinth_game.websocket.messageHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.game.Base;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.service.game.GameService;
import max.iv.labyrinth_game.websocket.GameStateBroadcaster;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.JoinRoomRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class JoinRoomMessageHandler implements WebSocketMessageHandler{
    private final GameService gameService;
    private final SessionManager sessionManager;
    private final GameStateBroadcaster gameStateBroadcaster;
    private final ObjectMapper objectMapper; // Для отправки ошибок через SessionManager
    private final Validator validator;
    public static final String USER_ID_ATTRIBUTE_KEY = "userId";
    public static final String USER_NAME_ATTRIBUTE_KEY = "userName";

    @Autowired
    public JoinRoomMessageHandler(GameService gameService,
                                  SessionManager sessionManager,
                                  GameStateBroadcaster gameStateBroadcaster,
                                  ObjectMapper objectMapper, Validator validator) {
        this.gameService = gameService;
        this.sessionManager = sessionManager;
        this.gameStateBroadcaster = gameStateBroadcaster;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }
    @Override
    public boolean supports(BaseMessage message) {
        return message != null && message.getType() == GameMessageType.JOIN_ROOM;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {
        if (!(message instanceof JoinRoomRequest request)) {
            log.error("Internal error: JoinRoomMessageHandler received non-JoinRoomRequest message type: {}", message.getClass().getSimpleName());
            sessionManager.sendErrorMessageToSession(session, "Internal server error: Invalid message type for JOIN_ROOM handler.", objectMapper);
            return;
        }
        if (sessionManager.validateRequestAndSendError(session, request, validator, "CREATE_ROOM")) {
            return;
        }
        String roomId = request.getRoomId();
        log.info("Handling JOIN_ROOM request from session {} for room {}", session.getId(), roomId);
        try {
            // 1. Извлекаем ID и имя пользователя из атрибутов сессии
            UUID userId = (UUID) session.getAttributes().get(USER_ID_ATTRIBUTE_KEY);
            String userName = (String) session.getAttributes().get(USER_NAME_ATTRIBUTE_KEY);

            if (userId == null || userName == null || userName.isBlank()) {
                log.warn("User ID or Name not found in session attributes for session {}. User must be authenticated.", session.getId());
                sessionManager.sendErrorMessageToSession(session, "User authentication/identification required to join a room.", objectMapper);
                return;
            }
            // 2. Проверяем, не пытается ли игрок присоединиться к комнате, в которой он уже есть (по ID игрока)
            if (session.getAttributes().get(SessionManager.PLAYER_ID_ATTRIBUTE_KEY) != null) {
                String existingRoomId = (String) session.getAttributes().get(SessionManager.ROOM_ID_ATTRIBUTE_KEY);
                log.warn("Player {} (session {}) attempted to join room {} but is already associated with room {}.",
                        userId, session.getId(), roomId, existingRoomId);
                sessionManager.sendErrorMessageToSession(session, "You are already in a room. Please leave it first to join another.", objectMapper);
                return;
            }
            // 3. Создаем объект Player (без аватара, его назначит GameService)
            Player newPlayer = new Player(userId, userName, new Base(0, 0, Set.of()));
            // 4. Добавляем игрока в комнату через GameService.
            GameRoom updatedRoom = gameService.addPlayerToRoom(roomId, newPlayer);
            // 5. Ассоциируем WebSocket сессию с ID игрока и ID комнаты
            sessionManager.associatePlayerWithSession(session, userId, roomId);
            // 6. Отправляем подтверждение присоединения этому клиенту
            sessionManager.sendMessageToSession(session, new BaseMessage(GameMessageType.JOIN_SUCCESS), objectMapper);
            log.info("Player {} (ID: {}, Avatar: {}) joined room {}. Session {} associated.",
                    userName, userId, newPlayer.getAvatar(), roomId, session.getId());
            // 7. Отправляем обновленное состояние игры всем в комнате
            gameStateBroadcaster.broadcastGameStateToRoom(roomId);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Failed to process JOIN_ROOM request for session {} to room {}: {}", session.getId(), roomId, e.getMessage());
            sessionManager.sendErrorMessageToSession(session, e.getMessage(), objectMapper);
        } catch (Exception e) {
            log.error("Unexpected error during JOIN_ROOM handling for session {} to room {}: {}", session.getId(), roomId, e.getMessage(), e);
            sessionManager.sendErrorMessageToSession(session, "An error occurred while joining the room.", objectMapper);
        }
    }
}
