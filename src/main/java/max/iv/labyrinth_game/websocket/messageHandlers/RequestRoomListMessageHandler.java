package max.iv.labyrinth_game.websocket.messageHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.service.game.LobbyService;
import max.iv.labyrinth_game.websocket.LobbyBroadcaster;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.GetRoomListRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class RequestRoomListMessageHandler implements WebSocketMessageHandler{

    private final LobbyBroadcaster lobbyBroadcaster;
    private final SessionManager sessionManager;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    @Autowired
    public RequestRoomListMessageHandler(LobbyBroadcaster lobbyBroadcaster, SessionManager sessionManager, Validator validator, ObjectMapper objectMapper) {
        this.lobbyBroadcaster = lobbyBroadcaster;
        this.sessionManager = sessionManager;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(BaseMessage message) {
        return message != null && message.getType() == GameMessageType.GET_ROOM_LIST_REQUEST;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {
        if (!(message instanceof GetRoomListRequest request)) {
            log.error("Internal error: RequestRoomListMessageHandler received non-GetRoomListRequest: {}", message.getClass().getSimpleName());
            sessionManager.sendErrorMessageToSession(session, "Internal server error: Invalid message type for GET_ROOM_LIST_REQUEST handler.", objectMapper);
            return;
        }
        // 1. Валидация DTO запроса
        if (sessionManager.validateRequestAndSendError(session, request, validator, "GET_ROOM_LIST")) {
            return; // Если были ошибки валидации, выходим
        }
        if (sessionManager.getPlayerIdBySession(session) == null) {
            log.warn("Unauthorized attempt to get room list from session {}", session.getId());
            sessionManager.sendErrorMessageToSession(session, "You must be authenticated to request the room list.", objectMapper);
            return;
        }

        log.info("Handling GET_ROOM_LIST_REQUEST from session {}: PageNumber={}, PageSize={}",
                session.getId(), request.getPageNumber(), request.getPageSize());

        try {
            // 2. Вызываем LobbyService для отправки списка комнат этой сессии
            lobbyBroadcaster.sendRoomListToSession(session, request.getPageNumber(), request.getPageSize());
        } catch (IllegalArgumentException | IllegalStateException e) { // Ошибки от сервисов
            log.warn("Failed to process GET_ROOM_LIST_REQUEST for session {}: {}", session.getId(), e.getMessage());
            sessionManager.sendErrorMessageToSession(session, e.getMessage(), objectMapper);
        } catch (Exception e) { // Другие непредвиденные ошибки
            log.error("Unexpected error during GET_ROOM_LIST_REQUEST handling for session {}: {}", session.getId(), e.getMessage(), e);
            sessionManager.sendErrorMessageToSession(session, "An error occurred while fetching the room list.", objectMapper);
        }
    }
}
