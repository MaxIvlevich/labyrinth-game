package max.iv.labyrinth_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.service.game.RoomService;
import max.iv.labyrinth_game.websocket.dto.PageInfo;
import max.iv.labyrinth_game.websocket.dto.RoomInfoDTO;
import max.iv.labyrinth_game.websocket.dto.RoomListUpdateResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LobbyBroadcaster {
    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private final RoomService roomService;
    private final ConcurrentHashMap<String, UUID> lobbySessions = new ConcurrentHashMap<>();

    @Autowired
    public LobbyBroadcaster(ObjectMapper objectMapper, SessionManager sessionManager, RoomService roomService) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.roomService = roomService;
        log.info("LobbyBroadcaster initialized.");
    }

    public void addSession(String sessionId, UUID userId) {
        lobbySessions.put(sessionId, userId);
        log.info("Added session {} with user {} to lobby.", sessionId, userId);
    }

    public void removeSession(String sessionId) {
        UUID removed = lobbySessions.remove(sessionId);
        if (removed != null) {
            log.info("Removed session {} (user {}) from lobby.", sessionId, removed);
        }
    }

    public void broadcastRoomList() {
        List<RoomInfoDTO> roomList = roomService.getAllRoomsInfo(0, 8);
        PageInfo pageDetails = new PageInfo(0, 8, 1, roomList.size());
        RoomListUpdateResponse payload = new RoomListUpdateResponse(roomList, pageDetails);

        for (String sessionId : new HashSet<>(lobbySessions.keySet())) {
            WebSocketSession session = sessionManager.getAuthenticatedGameSessionById(sessionId);
            if (session != null && session.isOpen()) {
                sessionManager.sendMessageToSession(session, payload, objectMapper);
            } else {
                lobbySessions.remove(sessionId);
                log.warn("Removed stale or closed session {} from lobbySessions.", sessionId);
            }
        }
        log.info("Broadcasted room list to {} sessions.", lobbySessions.size());
    }

    public boolean containsSession(String sessionId) {
        return lobbySessions.containsKey(sessionId);
    }
    public void removePlayerFromLobby(UUID playerId) {
        if (playerId == null) return;

        String sessionIdToRemove = null;

        for (Map.Entry<String, UUID> entry : lobbySessions.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                sessionIdToRemove = entry.getKey();
                break;
            }
        }

        if (sessionIdToRemove != null) {
            lobbySessions.remove(sessionIdToRemove);
            log.info("Removed player {} (session {}) from lobby.", playerId, sessionIdToRemove);
        } else {
            log.debug("Player {} not found in lobbySessions.", playerId);
        }
    }
    public void sendRoomListToSession(WebSocketSession session, int page, int size) {
        if (session == null || !session.isOpen()) {
            log.warn("Cannot send room list: session is null or not open.");
            return;
        }
        List<RoomInfoDTO> roomList = roomService.getAllRoomsInfo(page, size);
        long totalRooms = roomService.getTotalRoomCount();
        int totalPages = (int) Math.ceil((double) totalRooms / size);
        PageInfo pageInfo = new PageInfo(page, size, totalPages, totalRooms);
        RoomListUpdateResponse payload = new RoomListUpdateResponse(roomList, pageInfo);

        sessionManager.sendMessageToSession(session, payload, objectMapper);
        log.info("Sent room list to session {}", session.getId());
    }

    public void broadcastRoomListToAll(Collection<WebSocketSession> sessions) {
        List<RoomInfoDTO> roomList = roomService.getAllRoomsInfo(0, 8);
        PageInfo pageInfo = new PageInfo(0, 8, 1, roomList.size());
        RoomListUpdateResponse payload = new RoomListUpdateResponse(roomList, pageInfo);

        for (WebSocketSession session : sessions) {
            if (session != null && session.isOpen()) {
                sessionManager.sendMessageToSession(session, payload, objectMapper);
            }
        }
    }
}
