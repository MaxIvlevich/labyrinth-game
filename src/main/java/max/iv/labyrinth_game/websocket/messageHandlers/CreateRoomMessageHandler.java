package max.iv.labyrinth_game.websocket.messageHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.game.Base;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.service.game.GameService;
import max.iv.labyrinth_game.service.game.RoomService;
import max.iv.labyrinth_game.websocket.GameStateBroadcaster;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.CreateRoomRequest;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.RoomCreatedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import jakarta.validation.Validator;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class CreateRoomMessageHandler implements WebSocketMessageHandler{

    private final RoomService roomService;
    private final SessionManager sessionManager;
    private final GameStateBroadcaster gameStateBroadcaster;
    private final ObjectMapper objectMapper;
    private final GameService gameService;
    private final Validator validator;
    public final String  USER_ID_ATTRIBUTE_KEY = "userId";
    public final String  USER_NAME_ATTRIBUTE_KEY = "userName";

    @Autowired
    public CreateRoomMessageHandler(RoomService roomService,
                                    SessionManager sessionManager,
                                    GameStateBroadcaster gameStateBroadcaster,
                                    ObjectMapper objectMapper,
                                    GameService gameService, Validator validator) {
        this.roomService = roomService;
        this.sessionManager = sessionManager;
        this.gameStateBroadcaster = gameStateBroadcaster;
        this.objectMapper = objectMapper;
        this.gameService = gameService;
        this.validator = validator;
    }

    @Override
    public boolean supports(BaseMessage message) {
        return message != null && message.getType() == GameMessageType.CREATE_ROOM;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {
        if (!(message instanceof CreateRoomRequest request)) {
            sessionManager.sendErrorMessageToSession(session, "Internal server error: Invalid message type for handler.", objectMapper);
            return;
        }
        if (sessionManager.validateRequestAndSendError(session, request, validator, "CREATE_ROOM")) {
            return;
        }
        log.info("Handling CREATE_ROOM request from session {}: MaxPlayers={}",
                session.getId(), request.getMaxPlayers());
        try {
            // 1. Создаем комнату через RoomService
            GameRoom createdRoom = roomService.createRoom(request.getMaxPlayers());
            log.info("Room {} created by RoomService.", createdRoom.getRoomId());
            String roomId = createdRoom.getRoomId();

            // 2. Создаем игрока-создателя
            UUID userId = (UUID) session.getAttributes().get(USER_ID_ATTRIBUTE_KEY);
            String userName = (String) session.getAttributes().get(USER_NAME_ATTRIBUTE_KEY);
            if (userId == null || userName == null) {
                log.warn("User ID or Name not found in session attributes for session {}. User must be authenticated.", session.getId());
                sessionManager.sendErrorMessageToSession(session, "User authentication required to create a room.", objectMapper);
                return;
            }
            Player creator = new Player(userId, userName, new Base(0, 0, Set.of()));

            // 3. Добавляем создателя в комнату через GameService
            GameRoom roomWithCreator = gameService.addPlayerToRoom(roomId, creator);
            // 4. Ассоциируем WebSocket сессию с ID игрока и ID комнаты
            sessionManager.associatePlayerWithSession(session, userId, roomId);
            // 5. Формируем и отправляем ответ клиенту
            RoomCreatedResponse response = new RoomCreatedResponse(roomId, userId);
            sessionManager.sendMessageToSession(session, response, objectMapper);
            log.info("Sent RoomCreatedResponse to session {}", session.getId());
            // 6. Отправляем начальное состояние комнаты
            gameStateBroadcaster.broadcastGameStateToRoom(roomWithCreator.getRoomId());

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Failed to process CREATE_ROOM request for session {}: {}", session.getId(), e.getMessage());
            sessionManager.sendErrorMessageToSession(session, e.getMessage(), objectMapper);
        } catch (Exception e) {
            log.error("Unexpected error during CREATE_ROOM handling for session {}: {}", session.getId(), e.getMessage(), e);
            sessionManager.sendErrorMessageToSession(session, "An error occurred while creating the room.", objectMapper);
        }
    }
}

