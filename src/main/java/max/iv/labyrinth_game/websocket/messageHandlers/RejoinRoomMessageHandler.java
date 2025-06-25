package max.iv.labyrinth_game.websocket.messageHandlers;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.exceptions.ErrorType;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.enums.PlayerStatus;
import max.iv.labyrinth_game.service.game.RoomService;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.ReconnectToRoomRequest;
import max.iv.labyrinth_game.websocket.events.lobby.RoomStateNeedsBroadcastEvent;
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
    private final RoomService roomService;

    @Autowired
    public RejoinRoomMessageHandler(ApplicationEventPublisher eventPublisher, SessionManager sessionManager, RoomService roomService) {
        this.eventPublisher = eventPublisher;
        this.sessionManager = sessionManager;
        this.roomService = roomService;
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

        try {
            GameRoom room = roomService.getRoom(roomId);

            // Проверяем, что игрок действительно числится в этой комнате
            boolean playerIsInRoom = room.getPlayers().stream().anyMatch(p -> p.getId().equals(playerId));

            if (playerIsInRoom) {
                log.info("Player {} reconnected to room {}. Re-associating session.", playerId, roomId);

                // 1. Устанавливаем статус игрока в CONNECTED
                room.getPlayers().stream()
                        .filter(p -> p.getId().equals(playerId))
                        .findFirst()
                        .ifPresent(p -> p.setStatus(PlayerStatus.CONNECTED));
                // 2. Ассоциируем новую сессию
                sessionManager.associatePlayerWithSession(session, playerId, roomId);
                // 3. Рассылаем всем обновленное состояние
                eventPublisher.publishEvent(new RoomStateNeedsBroadcastEvent(this, roomId));


            } else {
                log.warn("Player {} tried to reconnect to room {}, but is not a member.", playerId, roomId);
                sessionManager.sendErrorMessageToSession(session, "You are no longer in this room", ErrorType.ROOM_NOT_FOUND);
            }

        } catch (IllegalArgumentException e) {
            log.warn("Player {} tried to reconnect to a non-existent room {}", playerId, roomId);
            sessionManager.sendErrorMessageToSession(session, "The room you were in no longer exists.", ErrorType.ROOM_NOT_FOUND);
        }
    }
}
