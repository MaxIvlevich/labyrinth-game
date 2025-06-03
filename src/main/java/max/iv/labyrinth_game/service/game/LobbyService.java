package max.iv.labyrinth_game.service.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.websocket.GameStateBroadcaster;
import max.iv.labyrinth_game.websocket.SessionManager;
import max.iv.labyrinth_game.websocket.dto.PageInfo;
import max.iv.labyrinth_game.websocket.dto.RoomListUpdateResponse;
import max.iv.labyrinth_game.websocket.dto.RoomInfoDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LobbyService {

    private final ObjectMapper objectMapper;
    private final RoomService roomService;
    private final SessionManager sessionManager;
    private static final int DEFAULT_PAGE_NUMBER = 0;
    private static final int DEFAULT_PAGE_SIZE = 8;

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

            String currentRoomId = sessionManager.getRoomIdBySession(session);
            if (currentRoomId != null) {
                log.info("Player {} (session {}) is already in room {}. Not adding to lobby.", userId, session.getId(), currentRoomId);
                return;
            }
            sendRoomListToSession(session,DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE);
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

    public void broadcastRoomListToLobby() {
        List<RoomInfoDTO> roomList = getCurrentRoomList();
        PageInfo pageDetails = getRoomListPageInfo(DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE);
        RoomListUpdateResponse payload = new RoomListUpdateResponse(roomList,pageDetails);
        log.info("Broadcasting room list update to {} lobby sessions.", lobbySessions.size());
        for (String sessionId : lobbySessions.keySet()) {
            WebSocketSession session = sessionManager.getAuthenticatedGameSessionById(sessionId);
            if (session != null && session.isOpen()) {
                sessionManager.sendMessageToSession(session, payload, objectMapper);
            }
        }
        for (String sessionId : new ConcurrentHashMap<>(lobbySessions).keySet()) {
            WebSocketSession session = sessionManager.getAuthenticatedGameSessionById(sessionId);
            if (session != null && session.isOpen()) {
                sessionManager.sendMessageToSession(session, payload, objectMapper);
            } else {
                lobbySessions.remove(sessionId);
            }
        }
    }

    private List<RoomInfoDTO> getCurrentRoomList() {
        log.debug("Fetching current room list.");
        return roomService.getAllRoomsInfo(LobbyService.DEFAULT_PAGE_NUMBER, LobbyService.DEFAULT_PAGE_SIZE);
    }

    public void sendRoomListToSession(WebSocketSession session,int number,int size) {
        if (session == null || !session.isOpen()) {
            log.warn("Cannot send room list: session is null or not open.");
            return;
        }
        List<RoomInfoDTO> roomList = getCurrentRoomList();
        PageInfo pageDetails = getRoomListPageInfo(number,size);
        RoomListUpdateResponse payload = new RoomListUpdateResponse(roomList,pageDetails);
        sessionManager.sendMessageToSession(session, payload, objectMapper);
        log.info("Sent room list to session {}", session.getId());
    }
    public PageInfo getRoomListPageInfo(int pageNumber, int pageSize) {
        long totalRooms = roomService.getTotalRoomCount();
        int totalPages = (int) Math.ceil((double) totalRooms / pageSize);
        return new PageInfo(pageNumber, pageSize, totalPages, totalRooms);
    }
}
