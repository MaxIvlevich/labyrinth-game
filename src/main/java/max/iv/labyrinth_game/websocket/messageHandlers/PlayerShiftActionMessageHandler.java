package max.iv.labyrinth_game.websocket.messageHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.service.game.GameService;
import max.iv.labyrinth_game.websocket.GameStateBroadcaster;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.PlayerShiftActionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

@Slf4j
@Component
public class PlayerShiftActionMessageHandler implements WebSocketMessageHandler{

    private final GameService gameService;
    private final SessionManager sessionManager;
    private final GameStateBroadcaster gameStateBroadcaster;
    private final ObjectMapper objectMapper;

    @Autowired
    public PlayerShiftActionMessageHandler(GameService gameService,
                                           SessionManager sessionManager,
                                           GameStateBroadcaster gameStateBroadcaster,
                                           ObjectMapper objectMapper) {
        this.gameService = gameService;
        this.sessionManager = sessionManager;
        this.gameStateBroadcaster = gameStateBroadcaster;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(BaseMessage message) {
        return message != null && message.getType() == GameMessageType.PLAYER_ACTION_SHIFT;

    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {

        if (!(message instanceof PlayerShiftActionRequest request)) {
            log.error("Internal error: PlayerShiftActionMessageHandler received non-PlayerShiftActionRequest: {}", message.getClass().getSimpleName());
            sessionManager.sendErrorMessageToSession(session, "Internal server error: Invalid message type for SHIFT action.", objectMapper);
            return;
        }

        UUID playerId = sessionManager.getPlayerIdBySession(session);
        String roomIdFromSession = sessionManager.getRoomIdBySession(session);


        // Валидация: игрок должен быть ассоциирован с сессией и комнатой,
        if (playerId == null) {
            log.warn("Unauthorized SHIFT action attempt by session {}: playerId not found via SessionManager.",
                    session.getId());
            sessionManager.sendErrorMessageToSession(session, "Player not authenticated or not in a room. Please join a room first.",
                    objectMapper);
            return;
        }
        if (roomIdFromSession == null) {
            log.warn("SHIFT action attempt by player {} (session {}) without associated room.", playerId, session.getId());
            sessionManager.sendErrorMessageToSession(session, "Player is not associated with any room.", objectMapper);
            return;
        }
        if (!roomIdFromSession.equals(request.getRoomId())) {
            log.warn("Room ID mismatch for SHIFT action by player {} (session {}). SessionRoom: {}, RequestRoom: {}",
                    playerId, session.getId(), roomIdFromSession, request.getRoomId());
            sessionManager.sendErrorMessageToSession(session, "Room ID mismatch. Action is for a different room than you are in.",
                    objectMapper);
            return;
        }

        log.info("Handling PLAYER_ACTION_SHIFT request from player {} in room {}: Index={}, Direction={}",
                playerId, request.getRoomId(), request.getShiftIndex(), request.getShiftDirection());

        try {
            // Вызываем GameService для выполнения сдвига
            gameService.performShiftAction(request.getRoomId(), playerId, request.getShiftIndex(), request.getShiftDirection());

            // Рассылаем обновленное состояние игры всем в комнате
            gameStateBroadcaster.broadcastGameStateToRoom(request.getRoomId());

            log.info("Player {} successfully performed SHIFT in room {}.", playerId, request.getRoomId());

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Shift action failed for player {} in room {}: {}", playerId, request.getRoomId(), e.getMessage());
            sessionManager.sendErrorMessageToSession(session, "Shift action failed: " + e.getMessage(), objectMapper);
        } catch (Exception e) { // Ловим другие возможные ошибки от сервисов
            log.error("Unexpected error during SHIFT action for player {} in room {}: {}", playerId, request.getRoomId(), e.getMessage(), e);
            sessionManager.sendErrorMessageToSession(session, "An unexpected error occurred during the shift action.", objectMapper);
        }
    }
}
