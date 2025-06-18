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
            // Можно отправить ошибку, но лучше тихо проигнорировать, т.к. цель уже достигнута.
            return;
        }

        log.info("Handling LEAVE_ROOM request from player {} in room {}", playerId, roomId);

        try {
            // 2. Вызываем GameService для удаления игрока из комнаты
            // 1. Вызываем сервис для удаления игрока из логики комнаты
            gameService.removePlayerFromRoom(playerId, roomId);

            // 2. Отвязываем сессию от комнаты
            sessionManager.returnPlayerToLobby(session.getId());

            // 3. Публикуем событие, что игрок вернулся в лобби
            // LobbyService подхватит его и добавит сессию в свой трекинг
            eventPublisher.publishEvent(new PlayerReturnedToLobbyEvent(this, session, playerId));

            // 4. Публикуем событие для обновления состояния комнаты для оставшихся игроков
            eventPublisher.publishEvent(new RoomStateNeedsBroadcastEvent(this, roomId));

            // 5. Публикуем событие для обновления списка комнат для всех в лобби
            eventPublisher.publishEvent(new LobbyRoomListNeedsUpdateEvent(this));

        } catch (Exception e) {
            log.error("Error processing LEAVE_ROOM for player {} in room {}: {}", playerId, roomId, e.getMessage(), e);
            sessionManager.sendErrorMessageToSession(session, "Error while trying to leave the room.");
        }
    }
}
