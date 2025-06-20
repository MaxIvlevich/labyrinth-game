package max.iv.labyrinth_game.websocket.events.listener;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.exceptions.ErrorType;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.model.game.enums.PlayerStatus;
import max.iv.labyrinth_game.service.game.RoomService;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.events.lobby.PlayerReturnedToLobbyEvent;
import max.iv.labyrinth_game.websocket.events.lobby.RoomStateNeedsBroadcastEvent;
import max.iv.labyrinth_game.websocket.events.room.PlayerReconnectedToRoomEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.UUID;
@Slf4j
@Component
public class GameReconnectionListener {
    private final RoomService roomService;
    private final SessionManager sessionManager;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public GameReconnectionListener(RoomService roomService, SessionManager sessionManager, ApplicationEventPublisher eventPublisher) {
        this.roomService = roomService;
        this.sessionManager = sessionManager;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void handlePlayerReconnection(PlayerReconnectedToRoomEvent event) {
        WebSocketSession session = event.getSession();
        UUID playerId = event.getPlayerId();
        String roomId = event.getRoomId();

        try {
            GameRoom room = roomService.getRoom(roomId);
            Optional<Player> playerOpt = room.getPlayers().stream()
                    .filter(p -> p.getId().equals(playerId))
                    .findFirst();

            if (playerOpt.isPresent()) {
                Player reconnectedPlayer = playerOpt.get();
                reconnectedPlayer.setStatus(PlayerStatus.CONNECTED);
                log.info("Player {} reconnected and status set to CONNECTED.", playerId);
                log.info("Validation successful. Re-associating session {} for player {} in room {}", session.getId(), playerId, roomId);

                // Пере-ассоциируем новую сессию с игроком и комнатой
                sessionManager.associatePlayerWithSession(session, playerId, roomId);

                // Теперь нужно отправить обновленное состояние всем в комнате
                // Для этого мы публикуем другое, уже существующее событие!
                eventPublisher.publishEvent(new RoomStateNeedsBroadcastEvent(this, roomId));

            } else {
                log.warn("Player {} tried to reconnect to room {}, but is not a member.", playerId, roomId);
                sessionManager.sendErrorMessageToSession(session, "You are no longer part of this room.",ErrorType.ROOM_NOT_FOUND);
                // стоит вернуть его в лобби
                sessionManager.returnPlayerToLobby(session.getId());
                eventPublisher.publishEvent(new PlayerReturnedToLobbyEvent(this, session, playerId));
            }
        } catch (IllegalArgumentException e) { // Например, roomService.getRoom() не нашел комнату
            log.warn("Player {} tried to reconnect to a non-existent room {}", playerId, roomId);
            sessionManager.sendErrorMessageToSession(session,
                    "The room you were in no longer exists.", ErrorType.ROOM_NOT_FOUND);
            sessionManager.returnPlayerToLobby(session.getId());
            eventPublisher.publishEvent(new PlayerReturnedToLobbyEvent(this, session, playerId));
        }
    }
}
