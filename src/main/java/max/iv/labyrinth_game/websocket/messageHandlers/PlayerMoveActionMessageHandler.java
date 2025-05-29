package max.iv.labyrinth_game.websocket.messageHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.enums.GamePhase;
import max.iv.labyrinth_game.service.game.GameService;
import max.iv.labyrinth_game.websocket.GameStateBroadcaster;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.PlayerMoveActionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

@Slf4j
@Component
public class PlayerMoveActionMessageHandler implements WebSocketMessageHandler{
    private final GameService gameService;
    private final SessionManager sessionManager;
    private final GameStateBroadcaster gameStateBroadcaster;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Autowired
    public PlayerMoveActionMessageHandler(GameService gameService,
                                          SessionManager sessionManager,
                                          GameStateBroadcaster gameStateBroadcaster,
                                          ObjectMapper objectMapper, Validator validator) {
        this.gameService = gameService;
        this.sessionManager = sessionManager;
        this.gameStateBroadcaster = gameStateBroadcaster;
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

        UUID playerId = sessionManager.getPlayerIdBySession(session); // Предполагаем, что Player ID - UUID
        String roomIdFromSession = sessionManager.getRoomIdBySession(session);

        // 1. Валидация: игрок должен быть ассоциирован с сессией и комнатой,
        if (playerId == null) {
            log.warn("Unauthorized MOVE action attempt by session {}: playerId not found via SessionManager.", session.getId());
            sessionManager.sendErrorMessageToSession(session, "Player not authenticated or not in a room. Please join a room first.", objectMapper);
            return;
        }
        if (roomIdFromSession == null) {
            log.warn("MOVE action attempt by player {} (session {}) without associated room.", playerId, session.getId());
            sessionManager.sendErrorMessageToSession(session, "Player is not associated with any room.", objectMapper);
            return;
        }
        if (!roomIdFromSession.equals(request.getRoomId())) {
            log.warn("Room ID mismatch for MOVE action by player {} (session {}). SessionRoom: {}, RequestRoom: {}",
                    playerId, session.getId(), roomIdFromSession, request.getRoomId());
            sessionManager.sendErrorMessageToSession(session, "Room ID mismatch. Action is for a different room than you are in.", objectMapper);
            return;
        }

        log.info("Handling PLAYER_ACTION_MOVE request from player {} in room {}: TargetX={}, TargetY={}",
                playerId, request.getRoomId(), request.getTargetX(), request.getTargetY());

        try {
            // 2. Вызываем GameService для выполнения перемещения
            GameRoom updatedRoom = gameService.performMoveAction(
                    request.getRoomId(),
                    playerId,
                    request.getTargetX(),
                    request.getTargetY()
            );

            // 3. Рассылаем обновленное состояние игры всем в комнате
            gameStateBroadcaster.broadcastGameStateToRoom(request.getRoomId());

            log.info("Player {} successfully performed MOVE in room {}.", playerId, request.getRoomId());
            // 4. Проверяем, не закончилась ли игра после хода
            if (updatedRoom.getGamePhase() == GamePhase.GAME_OVER && updatedRoom.getWinner() != null) {
                //gameStateBroadcaster.broadcastGameOver(updatedRoom.getRoomId(), updatedRoom.getWinner());
                log.info("Game over in room {}. Winner: {}", updatedRoom.getRoomId(), updatedRoom.getWinner().getName());
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Move action failed for player {} in room {}: {}", playerId, request.getRoomId(), e.getMessage());
            // Отправляем ошибку только инициатору действия
            sessionManager.sendErrorMessageToSession(session, "Move action failed: " + e.getMessage(), objectMapper);
        } catch (Exception e) { // Ловим другие возможные ошибки от сервисов
            log.error("Unexpected error during MOVE action for player {} in room {}: {}", playerId, request.getRoomId(), e.getMessage(), e);
            sessionManager.sendErrorMessageToSession(session, "An unexpected error occurred during the move action.", objectMapper);
        }
    }
}
