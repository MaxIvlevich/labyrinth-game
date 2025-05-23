package max.iv.labyrinth_game.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.BoardDTO;
import max.iv.labyrinth_game.dto.CellDTO;
import max.iv.labyrinth_game.dto.MarkerDTO;
import max.iv.labyrinth_game.dto.TileDTO;
import max.iv.labyrinth_game.model.Board;
import max.iv.labyrinth_game.model.Cell;
import max.iv.labyrinth_game.model.Marker;
import max.iv.labyrinth_game.model.Tile;
import max.iv.labyrinth_game.model.enums.Direction;
import max.iv.labyrinth_game.websocket.dto.GameStateUpdateDTO;
import max.iv.labyrinth_game.dto.PlayerDTO;
import max.iv.labyrinth_game.model.Base;
import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.Player;
import max.iv.labyrinth_game.model.enums.GamePhase;
import max.iv.labyrinth_game.service.GameService;
import max.iv.labyrinth_game.service.RoomService;
import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import max.iv.labyrinth_game.websocket.dto.CreateRoomRequest;
import max.iv.labyrinth_game.websocket.dto.ErrorMessageResponse;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.JoinRoomRequest;
import max.iv.labyrinth_game.websocket.dto.PlayerMoveActionRequest;
import max.iv.labyrinth_game.websocket.dto.PlayerShiftActionRequest;
import max.iv.labyrinth_game.websocket.dto.RoomCreatedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> authenticatedSessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerIdToSessionId = new ConcurrentHashMap<>();
    private final GameService gameService; // Сервис с игровой логикой
    private final ObjectMapper objectMapper; // Для сериализации/десериализации JSON
    private final RoomService roomService;

    @Autowired
    public GameWebSocketHandler(GameService gameService, ObjectMapper objectMapper, RoomService roomService) {
        this.gameService = gameService;
        this.objectMapper = objectMapper;
        this.roomService = roomService;
    }
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        //authenticatedSessions.put(session.getId(), session);
        log.info("WebSocket connection established: Session ID = {}, Remote Address = {}", session.getId(), session.getRemoteAddress());
        session.sendMessage(new TextMessage("Welcome! Your session ID is " + session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Received message from session {}: {}", session.getId(), payload);

        try {
            BaseMessage baseMessage = objectMapper.readValue(payload, BaseMessage.class);
            UUID playerIdFromSession =  (UUID) session.getAttributes().get("playerId");
            switch (baseMessage.getType()) {
                case CREATE_ROOM -> {
                    handleCreateRoom(session, (CreateRoomRequest) baseMessage);
                    break;
                }
                case JOIN_ROOM -> {
                    handleJoinRoom(session, (JoinRoomRequest) baseMessage);
                    break;
                }
                case  PLAYER_ACTION_MOVE-> {
                    PlayerMoveActionRequest moveRequest = (PlayerMoveActionRequest) baseMessage;
                    validateActionPrerequisites(session, playerIdFromSession, moveRequest.getRoomId(), "MOVE");
                    handlePlayerMoveAction(playerIdFromSession, moveRequest);
                    break;
                }
                case  PLAYER_ACTION_SHIFT -> {
                    PlayerShiftActionRequest shiftRequest = (PlayerShiftActionRequest) baseMessage;
                    validateActionPrerequisites(session, playerIdFromSession, shiftRequest.getRoomId(), "SHIFT");
                    handlePlayerShiftAction(playerIdFromSession, shiftRequest);
                    break;
                }
                default -> {
                    sendMessageToSession(session.getId(), new ErrorMessageResponse("Unknown message type"));
                }
            }
        }catch (JsonProcessingException e) {
            log.error("JSON parsing error for message from session {}: {}. Payload: {}", session.getId(), e.getMessage(), payload, e);
            sendErrorMessageToSession(session, "Invalid message format.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Action denied for session {}: {}", session.getId(), e.getMessage(), e);
            sendErrorMessageToSession(session, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing message from session {}: {}", session.getId(), payload, e);
            sendErrorMessageToSession(session, "An unexpected error occurred: " + e.getMessage());
        }
    }

    private void sendErrorMessageToSession(WebSocketSession session, String s) {
        sendMessageToSession(session.getId(), new ErrorMessageResponse(s));
    }

    private void handlePlayerShiftAction(UUID playerId, PlayerShiftActionRequest request) {
        // playerId уже проверен в validateActionPrerequisites
        try {
            gameService.performShiftAction(request.getRoomId(), playerId, request.getShiftIndex(), request.getShiftDirection());
            broadcastGameStateToRoom(request.getRoomId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Shift action failed for player {} in room {}: {}", playerId, request.getRoomId(), e.getMessage());
            WebSocketSession playerSession = getSessionByPlayerId(playerId);
            if (playerSession != null) {
                sendErrorMessageToSession(playerSession, "Shift action failed: " + e.getMessage());
            }
        } catch (Exception e) { // Другие непредвиденные ошибки
            log.error("Unexpected error during shift action for player {} in room {}: {}", playerId, request.getRoomId(), e.getMessage(), e);
            WebSocketSession playerSession = getSessionByPlayerId(playerId);
            if (playerSession != null) {
                sendErrorMessageToSession(playerSession, "An unexpected error occurred during shift.");
            }
        }
    }

    private void handlePlayerMoveAction(UUID playerId, PlayerMoveActionRequest request) {
        // playerId уже проверен
        try {
            GameRoom room = gameService.performMoveAction(request.getRoomId(), playerId, request.getTargetX(), request.getTargetY());
            broadcastGameStateToRoom(request.getRoomId());

            if (room.getGamePhase() == GamePhase.GAME_OVER && room.getWinner() != null) {
                broadcastGameOverMessage(room.getRoomId(), room.getWinner());
                log.info("Game over in room {}. Winner: {}", room.getRoomId(), room.getWinner().getName());
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Move action failed for player {} in room {}: {}", playerId, request.getRoomId(), e.getMessage());
            WebSocketSession playerSession = getSessionByPlayerId(playerId);
            if (playerSession != null) {
                sendErrorMessageToSession(playerSession, "Move action failed: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error during move action for player {} in room {}: {}", playerId, request.getRoomId(), e.getMessage(), e);
            WebSocketSession playerSession = getSessionByPlayerId(playerId);
            if (playerSession != null) {
                sendErrorMessageToSession(playerSession, "An unexpected error occurred during move.");
            }
        }
    }

    private WebSocketSession getSessionByPlayerId(UUID playerId) {
        if (playerId == null) return null;
        String sessionId = playerIdToSessionId.get(playerId); // Ключ теперь UUID
        if (sessionId != null) {
            return authenticatedSessions.get(sessionId); // authenticatedSessions хранит sessionId -> WebSocketSession
        }
        log.warn("No session found for playerId: {}", playerId);
        return null;
    }

    private void broadcastGameOverMessage(String roomId, Player winner) {

    }

    private void validateActionPrerequisites(WebSocketSession session, UUID playerIdFromSession, String roomIdFromRequest, String actionType) {
        if (playerIdFromSession == null) {
            log.warn("Unauthorized {} action attempt by session {}: playerId not found in session attributes.", actionType, session.getId());
            throw new IllegalStateException("Player not authenticated for " + actionType + " action. Please create or join a room first.");
        }
        String roomIdFromSession = (String) session.getAttributes().get("roomId");
        if (roomIdFromSession == null) {
            log.warn("Unauthorized {} action attempt by player {} (session {}): roomId not found in session attributes.",
                    actionType, playerIdFromSession, session.getId());
            throw new IllegalStateException("Player " + playerIdFromSession + " is not associated with any room for " + actionType + " action.");
        }
        if (!roomIdFromSession.equals(roomIdFromRequest)) {
            log.warn("Room ID mismatch for {} action by player {} (session {}). Expected: {}, Got: {}",
                    actionType, playerIdFromSession, session.getId(), roomIdFromSession, roomIdFromRequest);
            throw new IllegalArgumentException("Room ID mismatch. Action intended for room " + roomIdFromRequest +
                    " but session is associated with room " + roomIdFromSession + ".");
        }
    }

    private void handleJoinRoom(WebSocketSession session, JoinRoomRequest request) {
        try {
            GameRoom joinedRoom = roomService.getRoom(request.getRoomId());
            // Валидацию (комната полная, игра идет) может делать roomService.joinRoom или gameService.joinRoom
             String sessionId = session.getId();
            // Создаем нового игрока
            UUID newPlayerId = UUID.randomUUID();
            Player newPlayer = new Player(newPlayerId, request.getPlayerName(), request.getPlayerColor(), new Base(0,0,Set.of()));


            gameService.addPlayerToRoom(joinedRoom.getRoomId(), newPlayer);
            session.getAttributes().put("playerId", newPlayer.getId());
            session.getAttributes().put("roomId", joinedRoom.getRoomId());
            authenticatedSessions.put(sessionId, session);
            playerIdToSessionId.put(newPlayerId, sessionId);

            sendMessageToSession(sessionId, new BaseMessage(GameMessageType.JOIN_SUCCESS));
            log.info("Player {} (ID: {}) joined room {}. Session {} associated.",
                    newPlayer.getName(), newPlayer.getId(), joinedRoom.getRoomId(), session.getId());

            broadcastGameStateToRoom(joinedRoom.getRoomId());

            // Логика старта игры, если комната полная
            if (joinedRoom.isFull() && joinedRoom.getGamePhase() == GamePhase.WAITING_FOR_PLAYERS) {
                log.info("Room {} is full with {} players. Attempting to start game...",
                        joinedRoom.getRoomId(), joinedRoom.getPlayers().size());
                gameService.startGame(joinedRoom.getRoomId());
                broadcastGameStateToRoom(joinedRoom.getRoomId());
            }

        } catch (Exception e) {
            log.error("Error joining room {} for session {}: {}", request.getRoomId(), session.getId(), e.getMessage(), e);
            sendErrorMessageToSession(session, "Error joining room: " + e.getMessage());
        }
    }

    private void handleCreateRoom(WebSocketSession session, CreateRoomRequest request) {
        try {
            // Предполагаем, что Player ID - это String.
            UUID newPlayerId = UUID.randomUUID();
            // GameService должен вернуть созданную комнату и, возможно, объект создателя
            // или мы создаем игрока здесь и передаем в GameService.
            // Для простоты, предположим, GameService.createRoomAndAddPlayer(params)
            String sessionId = session.getId();
            // Создаем комнату через RoomService
            GameRoom createdRoom = roomService.createRoom(request.getMaxPlayers());
            // Создаем игрока
            Player creator = new Player(newPlayerId, request.getPlayerName(), "Red", new Base(0,0, Set.of())); // Пример цвета
            createdRoom.addPlayer(creator); // Добавляем игрока в комнату
            // Ассоциируем сессию с игроком и комнатой
            session.getAttributes().put("playerId", creator.getId());
            session.getAttributes().put("roomId", createdRoom.getRoomId());
            authenticatedSessions.put(sessionId, session);
            playerIdToSessionId.put(newPlayerId, sessionId);

            RoomCreatedResponse response = new RoomCreatedResponse(createdRoom.getRoomId(), creator.getId());
            sendMessageToSession(session.getId(), response);
            log.info("Room {} created by player {} (ID: {}). Session {} associated.",
                    createdRoom.getRoomId(), creator.getName(), creator.getId(), session.getId());

            // Отправляем состояние комнаты (пока только с одним игроком, игра не начата)
            broadcastGameStateToRoom(createdRoom.getRoomId());
        } catch (Exception e) {
            log.error("Error creating room for session {}: {}", session.getId(), e.getMessage(), e);
            sendErrorMessageToSession(session, "Error creating room: " + e.getMessage());
        }
    }

    private void broadcastGameStateToRoom(String roomId) {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) {
            log.warn("Cannot broadcast game state: room {} not found.", roomId);
            return;
        }
        Object gameStateDto = createPlaceholderGameStateDTO(room);

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

        for (Player player : room.getPlayers()) {
            WebSocketSession playerSession = getSessionByPlayerId(player.getId());
            if (playerSession != null) {
                sendMessageToSession(playerSession.getId(), jsonMessage); // Передаем ID сессии и уже сериализованную строку
            } else {
                log.warn("No active session found for player {} (ID: {}) in room {}. Cannot send game state update.",
                        player.getName(), player.getId(), roomId);
            }
        }
    }

    private GameStateUpdateDTO createPlaceholderGameStateDTO(GameRoom room) {
        if (room == null) {
            // Это не должно происходить, но для безопасности
            return new GameStateUpdateDTO("ERROR_ROOM_NULL", GamePhase.WAITING_FOR_PLAYERS, null, new ArrayList<>(), null);
        }

        // 1. Конвертируем игроков в PlayerDTO
        List<PlayerDTO> playerDTOs = room.getPlayers().stream()
                .map(this::convertPlayerToDTO)
                .collect(Collectors.toList());

        // 2. Конвертируем доску в BoardDTO
        BoardDTO boardDTO = convertBoardToDTO(room.getBoard());

        // 3. Определяем ID и имя текущего игрока
        UUID currentPlayerId = (room.getCurrentPlayer() != null) ? room.getCurrentPlayer().getId() : null;

        // 4. Определяем победителя, если игра окончена
        UUID winnerId = null;
        String winnerName = null;
        if (room.getGamePhase() == GamePhase.GAME_OVER && room.getWinner() != null) {
            winnerId = room.getWinner().getId();
            winnerName = room.getWinner().getName();
            currentPlayerId = null; // Если игра окончена, текущего хода может не быть
            return new GameStateUpdateDTO(room.getRoomId(),room.getGamePhase(),playerDTOs,boardDTO,winnerId,winnerName);
        }

        return new GameStateUpdateDTO(room.getRoomId(),room.getGamePhase(),currentPlayerId,playerDTOs,boardDTO);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        authenticatedSessions.remove(sessionId);
        log.info("WebSocket connection closed: Session ID = {}, Status = {}, Remote Address = {}",
                sessionId, status, session.getRemoteAddress());

        UUID playerId = (UUID) session.getAttributes().get("playerId"); // Получаем ID игрока
        String roomId = (String) session.getAttributes().get("roomId");

        if (playerId != null) {
            playerIdToSessionId.remove(playerId); // Удаляем связь игрок -> сессия

            if (roomId != null) {
                try {
                    log.info("Player {} (session {}) disconnected from room {}. Notifying GameService.",
                            playerId, sessionId, roomId);
                    gameService.handlePlayerDisconnect(playerId, roomId);
                    // После того как GameService обработал отключение (например, удалил игрока из комнаты)
                    broadcastGameStateToRoom(roomId);
                } catch (Exception e) {
                    log.error("Error during player disconnect handling for player {} in room {}: {}",
                            playerId, roomId, e.getMessage(), e);
                }
            } else {
                log.warn("RoomId not found in session attributes for disconnected player {} (session {}). Cannot notify GameService properly.",
                        playerId, sessionId);
            }
        } else {
            log.warn("No playerId found in session attributes for closed session {}. No game-specific disconnect logic to run.", sessionId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        // Можно также закрыть сессию или уведомить GameService
    }

    // --- Вспомогательные методы для отправки сообщений ---

    public void sendMessageToSession(String sessionId, Object messagePayload) {
        WebSocketSession session = authenticatedSessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(messagePayload);
                session.sendMessage(new TextMessage(jsonMessage));
                log.debug("Sent message to session {}: {}", sessionId, jsonMessage);
            } catch (IOException e) {
                log.error("Error sending message to session {}: {}", sessionId, messagePayload, e);
            }
        } else {
            log.warn("Attempted to send message to closed or non-existent session: {}", sessionId);
        }
    }
    public void broadcastMessage(Object messagePayload) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(messagePayload);
        } catch (IOException e) {
            log.error("Error serializing broadcast message: {}", messagePayload, e);
            return;
        }

        authenticatedSessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(jsonMessage));
                } catch (IOException e) {
                    log.error("Error broadcasting message to session {}: {}", session.getId(), jsonMessage, e);
                }
            }
        });
        log.debug("Broadcasted message: {}", jsonMessage);
    }
    public void sendMessageToRoom(String roomId, Object messagePayload) {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return;

        for (Player player : room.getPlayers()) {
            WebSocketSession session = getSessionByPlayerId(player.getId());
            if (session != null && session.isOpen()) {
                sendMessageToSession(session.getId(), messagePayload);
            }
        }
    }
}
