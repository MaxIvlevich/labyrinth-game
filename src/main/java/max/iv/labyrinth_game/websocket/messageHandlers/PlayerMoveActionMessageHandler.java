package max.iv.labyrinth_game.websocket.messageHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.exceptions.ErrorType;
import max.iv.labyrinth_game.exceptions.game.GameLogicException;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.enums.GamePhase;
import max.iv.labyrinth_game.service.game.RoomService;
import max.iv.labyrinth_game.service.game.actions.GameActionDispatcher;
import max.iv.labyrinth_game.service.game.actions.MoveActionContext;
import max.iv.labyrinth_game.websocket.GameStateBroadcaster;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.PlayerMoveActionRequest;
import max.iv.labyrinth_game.websocket.events.lobby.RoomStateNeedsBroadcastEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

@Slf4j
@Component
public class PlayerMoveActionMessageHandler implements WebSocketMessageHandler{
    private final RoomService roomService;
    private final GameActionDispatcher actionDispatcher;
    private final SessionManager sessionManager;
    private final GameStateBroadcaster gameStateBroadcaster;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Autowired
    public PlayerMoveActionMessageHandler(
            RoomService roomService, GameActionDispatcher actionDispatcher, SessionManager sessionManager,
            GameStateBroadcaster gameStateBroadcaster,
            ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper, Validator validator) {
        this.roomService = roomService;
        this.actionDispatcher = actionDispatcher;
        this.sessionManager = sessionManager;
        this.gameStateBroadcaster = gameStateBroadcaster;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.validator = validator;
        log.info("PlayerMoveActionMessageHandler initialized.");
    }
    @Override
    public boolean supports(BaseMessage message) {
        return message != null && message.getType() == GameMessageType.PLAYER_ACTION_MOVE;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {
        if (!(message instanceof PlayerMoveActionRequest request)) {
            log.error("Internal error: PlayerMoveActionMessageHandler received non-PlayerMoveActionRequest: {}", message.getClass().getSimpleName());
            sessionManager.sendErrorMessageToSession(session, "Internal server error: Invalid message type for MOVE action.", objectMapper);
            return;
        }
        if (sessionManager.validateRequestAndSendError(session, request, validator, "CREATE_ROOM")) {
            return;
        }

        UUID playerId = sessionManager.getPlayerIdBySession(session);
        String roomIdFromSession = sessionManager.getRoomIdBySession(session);

        // 1. Валидация: игрок должен быть ассоциирован с сессией и комнатой,
        if (playerId == null) {
            log.warn("Unauthorized MOVE action attempt by session {}: playerId not found via SessionManager.", session.getId());
            sessionManager.sendErrorMessageToSession(session,
                    "Player not authenticated or not in a room. Please join a room first.",
                    objectMapper);
            return;
        }
        if (roomIdFromSession == null) {
            log.warn("MOVE action attempt by player {} (session {}) without associated room.", playerId, session.getId());
            sessionManager.sendErrorMessageToSession(session,
                    "Player is not associated with any room.",
                    objectMapper);
            return;
        }
        if (!roomIdFromSession.equals(request.getRoomId())) {
            log.warn("Room ID mismatch for MOVE action by player {} (session {}). SessionRoom: {}, RequestRoom: {}",
                    playerId, session.getId(), roomIdFromSession, request.getRoomId());
            sessionManager.sendErrorMessageToSession(session,
                    "Room ID mismatch. Action is for a different room than you are in.",
                    objectMapper);
            return;
        }

        log.info("Handling PLAYER_ACTION_MOVE request from player {} in room {}: TargetX={}, TargetY={}",
                playerId, request.getRoomId(), request.getTargetX(), request.getTargetY());

        try {
            GameRoom room = roomService.getRoom(roomIdFromSession);
            // 2. Вызываем GameService для выполнения перемещения
            MoveActionContext context = new MoveActionContext(room, playerId,
                    request.getTargetX(),
                    request.getTargetY());

            actionDispatcher.dispatchMoveAction(context);

            eventPublisher.publishEvent(new RoomStateNeedsBroadcastEvent(this, roomIdFromSession));

            // 3. Рассылаем обновленное состояние игры всем в комнате
            gameStateBroadcaster.broadcastGameStateToRoom(request.getRoomId());

            log.info("Player {} successfully performed MOVE in room {}.", playerId, request.getRoomId());
            // 4. Проверяем, не закончилась ли игра после хода
            if (room.getGamePhase() == GamePhase.GAME_OVER && room.getWinner() != null) {
                //gameStateBroadcaster.broadcastGameOver(updatedRoom.getRoomId(), updatedRoom.getWinner());
                log.info("Game over in room {}. Winner: {}", room.getRoomId(), room.getWinner().getName());
            }

        } catch (GameLogicException e) {
            log.warn("Move action failed for player {}: Type={}, Message={}",
                    playerId, e.getErrorType(), e.getMessage());
            sessionManager.sendErrorMessageToSession(session, e.getMessage(), e.getErrorType());
        } catch (Exception e) { // Ловим другие возможные ошибки от сервисов
            log.error("Unexpected error during MOVE action for player {}: {}",
                    playerId, e.getMessage(), e);
            sessionManager.sendErrorMessageToSession(session, "An unexpected server error occurred.", ErrorType.UNKNOWN_ERROR);
        }
    }
}
