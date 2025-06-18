package max.iv.labyrinth_game.websocket.messageHandlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.service.game.GameService;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.ErrorMessageResponse;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.events.lobby.LobbyRoomListNeedsUpdateEvent;
import max.iv.labyrinth_game.websocket.events.lobby.PlayerReturnedToLobbyEvent;
import max.iv.labyrinth_game.websocket.events.lobby.RoomStateNeedsBroadcastEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static max.iv.labyrinth_game.websocket.SessionManager.PLAYER_ID_ATTRIBUTE_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveRoomMessageHandler implements WebSocketMessageHandler {
    private final GameService gameService;
    private final SessionManager sessionManager;
    private final ApplicationEventPublisher eventPublisher;
    @Override
    public boolean supports(BaseMessage message) {
        return message.getType() == GameMessageType.LEAVE_ROOM;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {
        UUID playerId = (UUID) session.getAttributes().get(SessionManager.USER_ID_ATTRIBUTE_KEY);
        String roomId = (String) session.getAttributes().get(SessionManager.ROOM_ID_ATTRIBUTE_KEY);

        // 1. Проверяем, что игрок действительно был в комнате
        if (playerId == null || roomId == null) {
            log.warn("LEAVE_ROOM request from session {} which is not fully in a room (playerId: {}, roomId: {}). Ignoring.",
                    session.getId(), playerId, roomId);
            return;
        }

        log.info("Handling LEAVE_ROOM request from player {} in room {}", playerId, roomId);

        try {
            boolean roomWasRemoved = gameService.removePlayerFromRoom(playerId, roomId);

            sessionManager.returnPlayerToLobby(session.getId());
            eventPublisher.publishEvent(new PlayerReturnedToLobbyEvent(this, session, playerId));

            // и нужно обновить их состояние.
            if (!roomWasRemoved) {
                eventPublisher.publishEvent(new RoomStateNeedsBroadcastEvent(this, roomId));
            }

            // В любом случае (была комната удалена или просто изменилось кол-во игроков),
            // лобби нужно обновить.
            eventPublisher.publishEvent(new LobbyRoomListNeedsUpdateEvent(this));

        } catch (Exception e) {
            log.error("Error processing LEAVE_ROOM for player {} in room {}: {}", playerId, roomId, e.getMessage(), e);
            sessionManager.sendErrorMessageToSession(session, "Error while trying to leave the room.");
        }
    }
}
