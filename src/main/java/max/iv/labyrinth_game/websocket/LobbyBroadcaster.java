package max.iv.labyrinth_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.service.game.RoomService;
import max.iv.labyrinth_game.websocket.dto.PageInfo;
import max.iv.labyrinth_game.websocket.dto.RoomInfoDTO;
import max.iv.labyrinth_game.websocket.dto.RoomListUpdateResponse;
import max.iv.labyrinth_game.websocket.events.lobby.LobbyRoomListNeedsUpdateEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

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

    @EventListener
    public void handleLobbyRoomListNeedsUpdate(LobbyRoomListNeedsUpdateEvent event) {
        log.info("Event received: LobbyRoomListNeedsUpdateEvent. Broadcasting updated room list to lobby.");
        try {
            broadcastRoomList();
        } catch (Exception e) {
            log.error("Error broadcasting room list in response to event: {}", e.getMessage(), e);
        }
    }

    public void addSession(String sessionId, UUID userId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Attempted to add a session to lobby with null or blank sessionId.");
            return;

        }
        lobbySessions.put(sessionId, userId);
        if (userId != null) {
            log.info("Session {} (user {}) added to lobby tracking.", sessionId, userId);
        } else {
            log.info("Session {} (user not yet identified) added to lobby tracking.", sessionId);
        }


    }

    public void removeSession(String sessionId) {
        if (sessionId == null) return;
        UUID removed = lobbySessions.remove(sessionId);
        if (removed != null) {
            log.info("Session {} (user {}) removed from lobby tracking.", sessionId, removed);
        } else {
            // Проверяем, была ли сессия без userId вообще в мапе
            if (lobbySessions.containsKey(sessionId)) {
                lobbySessions.remove(sessionId);
                log.info("Session {} (no specific user ID) removed from lobby tracking.", sessionId);
            } else {
                log.debug("Session {} was not in lobby tracking to be removed.", sessionId);
            }
        }
    }

    public void broadcastRoomList() {
        List<RoomInfoDTO> roomList = roomService.getAllRoomsInfo(0, 8);
        PageInfo pageDetails = new PageInfo(0, 8, 1, roomList.size());
        RoomListUpdateResponse payload = new RoomListUpdateResponse(roomList, pageDetails);

        for (String sessionId : new HashSet<>(lobbySessions.keySet())) {
            WebSocketSession session = sessionManager.getActiveSessionById(sessionId);
            if (session != null && session.isOpen()) {
                if (sessionManager.getRoomIdBySession(session) == null) {
                    sessionManager.sendMessageToSession(session, payload, objectMapper);
                }
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
}
