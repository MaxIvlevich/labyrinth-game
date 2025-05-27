package max.iv.labyrinth_game.websocket.messageHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.mappers.GameStateMapper;
import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.Player;
import max.iv.labyrinth_game.service.RoomService;
import max.iv.labyrinth_game.websocket.dto.GameStateUpdateDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Slf4j
@Component
public class GameStateBroadcaster {
    private final SessionManager sessionManager;
    private final RoomService roomService;
    private final ObjectMapper objectMapper;
    private final GameStateMapper gameStateMapper;

    @Autowired
    public GameStateBroadcaster(SessionManager sessionManager,
                                RoomService roomService,
                                ObjectMapper objectMapper,
            GameStateMapper gameStateMapper){
        this.sessionManager = sessionManager;
        this.roomService = roomService;
        this.objectMapper = objectMapper;
        this.gameStateMapper = gameStateMapper;
        log.info("GameStateBroadcaster initialized.");
    }

    public void broadcastGameStateToRoom(String roomId) {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) {
            log.warn("Cannot broadcast game state: room {} not found.", roomId);
            return;
        }
        GameStateUpdateDTO gameStateDto = gameStateMapper.toDto(room);  // todo
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(gameStateDto);
        } catch (IOException e) {
            log.error("Error serializing game state for room {}: DTOClass={}, Error={}",
                    roomId, gameStateDto.getClass().getSimpleName(), e.getMessage());
            return;
        }

        log.info("Broadcasting game state for room {} to {} players.",
                roomId, room.getPlayers().size());

        // Шаг 2: Получение сессий игроков и отправка
        for (Player player : room.getPlayers()) {
            WebSocketSession playerSession = sessionManager.getSessionByPlayerId(player.getId()); // Player.getId() должен быть UUID
            if (playerSession != null) { // Отправляем только если сессия активна
                sendMessageToSessionInternal(playerSession, jsonMessage);
            } else {
                log.warn("No active session found for player {} (ID: {}) in room {}. Cannot send game state update.",
                        player.getName(), player.getId(), roomId);
            }
        }
    }
    public void broadcastGameOver(String roomId, Player winner) {
        if (winner == null) {
            log.warn("Cannot broadcast game over for room {}: winner is null.", roomId);
            broadcastGameStateToRoom(roomId);
            return;
        }
        log.info("Broadcasting GAME OVER for room {}. Winner: {} (ID: {})", roomId, winner.getName(), winner.getId());
    }
    private void sendMessageToSessionInternal(WebSocketSession session, String jsonMessage) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(jsonMessage));
                log.debug("Sent to session {}: {}", session.getId(), jsonMessage.length() > 100 ? jsonMessage.substring(0, 100) + "..." : jsonMessage);
            } catch (IOException e) {
                log.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
            }
        } else {
            log.warn("Attempted to send message, but session is null or closed for internal send. Session ID (if known): {}",
                    session != null ? session.getId() : "N/A");
        }
    }

}
