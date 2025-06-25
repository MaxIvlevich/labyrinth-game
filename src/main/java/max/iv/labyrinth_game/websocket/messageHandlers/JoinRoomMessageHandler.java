package max.iv.labyrinth_game.websocket.messageHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.exceptions.ErrorType;
import max.iv.labyrinth_game.exceptions.game.GameLogicException;
import max.iv.labyrinth_game.model.game.Base;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.service.game.GameService;
import max.iv.labyrinth_game.service.game.RoomService;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.JoinRoomRequest;
import max.iv.labyrinth_game.websocket.events.lobby.LobbyRoomListNeedsUpdateEvent;
import max.iv.labyrinth_game.websocket.events.lobby.PlayerNeedsRemovalFromLobbyEvent;
import max.iv.labyrinth_game.websocket.events.lobby.RoomStateNeedsBroadcastEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static max.iv.labyrinth_game.websocket.config.JwtAuthHandshakeInterceptor.USER_ID_ATTRIBUTE_KEY;
import static max.iv.labyrinth_game.websocket.config.JwtAuthHandshakeInterceptor.USER_NAME_ATTRIBUTE_KEY;

@Slf4j
@Component
public class JoinRoomMessageHandler implements WebSocketMessageHandler {

    private final GameService gameService;
    private final RoomService roomService;
    private final SessionManager sessionManager;
    private final Validator validator;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public JoinRoomMessageHandler(GameService gameService,
                                  RoomService roomService, SessionManager sessionManager,
                                  Validator validator,
                                  ApplicationEventPublisher eventPublisher) {
        this.gameService = gameService;
        this.roomService = roomService;
        this.sessionManager = sessionManager;
        this.validator = validator;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean supports(BaseMessage message) {
        return message != null && message.getType() == GameMessageType.JOIN_ROOM;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {
        JoinRoomRequest request = (JoinRoomRequest) message;
        if (sessionManager.validateRequestAndSendError(session, request, validator, "JOIN_ROOM")) {
            return;
        }
            // 1. Извлекаем ID и имя пользователя из атрибутов сессии
            UUID userId = (UUID) session.getAttributes().get(USER_ID_ATTRIBUTE_KEY);
            String userName = (String) session.getAttributes().get(USER_NAME_ATTRIBUTE_KEY);
            String roomIdToJoin = request.getRoomId();
            if (userId == null || userName == null) {
                sessionManager.sendErrorMessageToSession(session, "Authentication error. Cannot join room.", ErrorType.UNAUTHORIZED);
                return;
            }

            log.info("Handling JOIN_ROOM request from player {} ({}) for room {}", userName, userId, roomIdToJoin);

            try {
                GameRoom room = roomService.getRoom(roomIdToJoin);

                Optional<Player> existingPlayerOpt = room.getPlayers().stream()
                        .filter(p -> p.getId().equals(userId))
                        .findFirst();

                if (existingPlayerOpt.isPresent()) {
                    log.info("Player {} is re-joining room {} from lobby.", userId, roomIdToJoin);
                } else {
                    // СЦЕНАРИЙ 2: НОВЫЙ ИГРОК
                    // Здесь мы вызываем gameService, как и раньше.
                    log.info("Handling JOIN_ROOM request for new player {} for room {}", userName, roomIdToJoin);
                    Player newPlayer = new Player(userId, userName, new Base(0, 0, Set.of()));
                    gameService.addPlayerToRoom(roomIdToJoin, newPlayer);
                }

                // 3. Если не было исключений, значит все прошло успешно. Ассоциируем сессию.
                sessionManager.associatePlayerWithSession(session, userId, roomIdToJoin);

                // 4. Публикуем события для обновления UI у всех клиентов
                eventPublisher.publishEvent(new PlayerNeedsRemovalFromLobbyEvent(this, userId));
                eventPublisher.publishEvent(new RoomStateNeedsBroadcastEvent(this, roomIdToJoin));
                eventPublisher.publishEvent(new LobbyRoomListNeedsUpdateEvent(this));

                log.info("Player {} successfully processed to join room {}", userName, roomIdToJoin);

            } catch (GameLogicException | IllegalArgumentException e) {
                log.warn("Failed to process JOIN_ROOM for player {} into room {}: {}", userId, roomIdToJoin, e.getMessage());
                // Отправляем понятное сообщение об ошибке на фронт
                sessionManager.sendErrorMessageToSession(session, e.getMessage(), ErrorType.ROOM_IS_FULL);
            }
    }
}
