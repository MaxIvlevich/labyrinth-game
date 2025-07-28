package max.iv.labyrinth_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.geme.PointDTO;
import max.iv.labyrinth_game.mappers.game.GameStateMapper;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.model.game.enums.GamePhase;
import max.iv.labyrinth_game.service.game.GameService;
import max.iv.labyrinth_game.service.game.RoomService;
import max.iv.labyrinth_game.websocket.dto.GameStateUpdateDTO;
import max.iv.labyrinth_game.websocket.events.lobby.RoomStateNeedsBroadcastEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GameStateBroadcaster {
    private final SessionManager sessionManager;
    private final GameService gameService;
    private final RoomService roomService;
    private final ObjectMapper objectMapper;
    private final GameStateMapper gameStateMapper;

    @Autowired
    public GameStateBroadcaster(SessionManager sessionManager,
                                RoomService roomService,
                                ObjectMapper objectMapper,
                                GameStateMapper gameStateMapper,
                                GameService gameService) {
        this.sessionManager = sessionManager;
        this.roomService = roomService;
        this.objectMapper = objectMapper;
        this.gameService = gameService;
        this.gameStateMapper = gameStateMapper;
        log.info("GameStateBroadcaster initialized.");
    }

    @EventListener
    public void handleRoomStateNeedsBroadcast(RoomStateNeedsBroadcastEvent event) {
        if (event == null || event.getRoomId() == null) {
            log.warn("Received a null RoomStateNeedsBroadcastEvent or event with null roomId. Skipping broadcast.");
            return;
        }
        String roomId = event.getRoomId();
        log.info("Event received: RoomStateNeedsBroadcastEvent for room {}. Broadcasting personalized game state.", roomId);
        try {
            // ВЫЗЫВАЕМ НАШ НОВЫЙ МЕТОД
            broadcastPersonalizedState(roomId);
        } catch (Exception e) {
            log.error("Error broadcasting personalized game state for room {} in response to event: {}", roomId, e.getMessage(), e);
        }
    }

    public void broadcastPersonalizedState(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            log.warn("Cannot broadcast game state: roomId is null or blank.");
            return;
        }
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) {
            log.warn("Cannot broadcast game state: room {} not found.", roomId);
            return;
        }
        if (room.getPlayers() == null || room.getPlayers().isEmpty()) {
            log.info("No players in room {} to broadcast game state to.", roomId);
            return;
        }
        log.info("Broadcasting personalized state for room {} to {} players.", roomId, room.getPlayers().size());

        // 1. Вычисляем достижимые клетки ОДИН РАЗ (если нужно)
        Set<GameService.Point> reachableCellsForCurrentPlayer = null;
        if (room.getGamePhase() == GamePhase.PLAYER_MOVE && room.getCurrentPlayer() != null) {
            Player currentPlayer = room.getCurrentPlayer();
            reachableCellsForCurrentPlayer = gameService.findAllReachableCells(
                    room.getBoard(),
                    currentPlayer.getCurrentX(),
                    currentPlayer.getCurrentY()
            );
        }

        // 2. Рассылаем состояние каждому игроку
        for (Player player : room.getPlayers()) {
            WebSocketSession playerSession = sessionManager.getSessionByPlayerId(player.getId());
            if (playerSession == null) {
                log.warn("No active session for player {}. Skipping broadcast for them.", player.getName());
                continue;
            }

            // 3. Определяем, нужно ли отправлять `reachableCells` этому конкретному игроку
            Set<GameService.Point> cellsForThisPlayer = player.equals(room.getCurrentPlayer())
                    ? reachableCellsForCurrentPlayer
                    : null;

            // Конвертируем внутренние Point в PointDTO
            Set<PointDTO> reachableCellsDto = (cellsForThisPlayer != null)
                    ? cellsForThisPlayer.stream().map(p -> new PointDTO(p.x(), p.y())).collect(Collectors.toSet())
                    : null;

            // 4. Используем наш обновленный маппер для создания DTO
            GameStateUpdateDTO dto = gameStateMapper.toDto(room, player, reachableCellsDto);

            // 5. Сериализуем и отправляем сообщение
            try {
                String jsonMessage = objectMapper.writeValueAsString(dto);
                sendMessageToSessionInternal(playerSession, jsonMessage);
            } catch (IOException e) {
                log.error("Error serializing personalized game state for player {} in room {}: {}",
                        player.getName(), roomId, e.getMessage());
            }
        }
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
