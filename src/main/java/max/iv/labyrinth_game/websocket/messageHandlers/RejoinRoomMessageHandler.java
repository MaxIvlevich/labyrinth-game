package max.iv.labyrinth_game.websocket.messageHandlers;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.exceptions.auth.ErrorType;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.ReconnectToRoomRequest;
import max.iv.labyrinth_game.websocket.events.room.PlayerReconnectedToRoomEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

@Slf4j
@Component
public class RejoinRoomMessageHandler implements WebSocketMessageHandler{
    private final ApplicationEventPublisher eventPublisher;
    private final SessionManager sessionManager;

    @Autowired
    public RejoinRoomMessageHandler(ApplicationEventPublisher eventPublisher, SessionManager sessionManager) {
        this.eventPublisher = eventPublisher;
        this.sessionManager = sessionManager;
    }
    @Override
    public boolean supports(BaseMessage message) {
        return message.getType() == GameMessageType.RECONNECT_TO_ROOM;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {
        ReconnectToRoomRequest request = (ReconnectToRoomRequest) message;
        UUID playerId = sessionManager.getPlayerIdBySession(session);
        String roomId = request.getRoomId();

        if (playerId == null || roomId == null || roomId.isBlank()) {
            sessionManager.sendErrorMessageToSession(session, "Invalid reconnect request. Authentication or room ID is missing.", ErrorType.NULL_REQUEST);
            return;
        }

        log.info("Player {} is attempting to reconnect to room {}. Publishing event.", playerId, roomId);

        // Просто публикуем событие, передав всю ответственность дальше
        eventPublisher.publishEvent(new PlayerReconnectedToRoomEvent(this, session, playerId, roomId));
    }
}
