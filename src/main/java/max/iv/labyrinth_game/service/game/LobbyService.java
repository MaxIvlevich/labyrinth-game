package max.iv.labyrinth_game.service.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.websocket.GameStateBroadcaster;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.RoomListUpdateResponse;
import max.iv.labyrinth_game.websocket.dto.RoomSummaryDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LobbyService {

    private final ObjectMapper objectMapper;
    private final RoomService roomService;
    private final SessionManager sessionManager;


    private final ConcurrentHashMap<String, UUID> lobbySessions = new ConcurrentHashMap<>();

    public LobbyService(ObjectMapper objectMapper, RoomService roomService, SessionManager sessionManager, GameStateBroadcaster gameStateBroadcaster) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
        this.sessionManager = sessionManager;
        log.info("LobbyService initialized.");
    }
    public void addSessionToLobby(WebSocketSession session, UUID userId) {
        if (session != null && userId != null) {
            lobbySessions.put(session.getId(), userId);
            log.info("Player {} (session {}) added to lobby. Total in lobby: {}", userId, session.getId(), lobbySessions.size());
            // Сразу отправить ему список комнат
            sendRoomListToSession(session);
        }
    }

    public void removeSessionFromLobby(WebSocketSession session) {
        if (session != null) {
            UUID userId = lobbySessions.remove(session.getId());
            if (userId != null) {
                log.info("Player {} (session {}) removed from lobby. Total in lobby: {}", userId, session.getId(), lobbySessions.size());
            }
        }
    }

    public void removePlayerFromLobby(UUID playerId) {
        String sessionIdToRemove = null;
        for (Map.Entry<String, UUID> entry : lobbySessions.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                sessionIdToRemove = entry.getKey();
                break;
            }
        }
        if (sessionIdToRemove != null) {
            lobbySessions.remove(sessionIdToRemove);
            log.info("Player {} (session {}) removed from lobby by playerId. Total in lobby: {}", playerId, sessionIdToRemove, lobbySessions.size());
        }
    }


    public List<RoomSummaryDTO> getCurrentRoomList() {
        return roomService.getAllRooms().stream()
                .map(this:: )
                .collect(Collectors.toList());
    }



    public void broadcastRoomListToLobby() {
        List<RoomSummaryDTO> roomList = getCurrentRoomList();
         RoomListUpdateResponse payload = new RoomListUpdateResponse(roomList,);
        log.info("Broadcasting room list update to {} lobby sessions.", lobbySessions.size());
        for (String sessionId : lobbySessions.keySet()) {
            WebSocketSession session = sessionManager.getAuthenticatedGameSessionById(sessionId);
            if (session != null && session.isOpen()) {
                sessionManager.sendMessageToSession(session, payload,objectMapper);
            }
        }
    }

    public void sendRoomListToSession(WebSocketSession session) {
        if (session == null || !session.isOpen()) return;
        List<RoomSummaryDTO> roomList = getCurrentRoomList();
         RoomListUpdateResponse payload = new RoomListUpdateResponse(roomList);
         sessionManager.sendMessageToSession(session, payload,objectMapper);
        log.info("Sent room list to session {}", session.getId());
    }
}
