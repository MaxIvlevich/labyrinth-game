package max.iv.labyrinth_game.service.game;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.enums.GamePhase;
import max.iv.labyrinth_game.websocket.dto.RoomInfoDTO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class RoomService {

    public static final List<String> AVAILABLE_PLAYER_COLORS = List.of("Red", "Blue", "Green", "Yellow");

    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    public GameRoom createRoom(int maxPlayers,String roomName) {
        validateMaxPlayers(maxPlayers);
        GameRoom room = new GameRoom(maxPlayers,roomName);
        gameRooms.put(room.getRoomId(), room);
        log.info("Room created: {} with max players: {} room name {}", room.getRoomId(), maxPlayers,roomName);
        return room;
    }

    public GameRoom joinRoom(String roomId, String playerName) {
        GameRoom room = getExistingRoom(roomId);
        validateRoomForJoin(room);
        return room;
    }

    public GameRoom getRoom(String roomId) {
        return getExistingRoom(roomId);
    }

    private void validateMaxPlayers(int maxPlayers) {
        if (maxPlayers < 2 || maxPlayers > 4) {
            throw new IllegalArgumentException("Max players must be between 2 and 4.");
        }
    }

    public void validateRoomForJoin(GameRoom room) {
        if (room.isFull()) {
            throw new IllegalStateException("Room is full: " + room.getRoomId());
        }
        if (room.getGamePhase() != GamePhase.WAITING_FOR_PLAYERS) {
            throw new IllegalStateException("Game already started or finished in room: " + room.getRoomId());
        }
    }

    private GameRoom getExistingRoom(String roomId) {
        GameRoom room = gameRooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found: " + roomId);
        }
        return room;
    }

    public List<RoomInfoDTO> getAllRoomsInfo(int pageNumber, int pageSize) {
        log.debug("Fetching rooms info for page: {}, size: {}", pageNumber, pageSize);
        List<RoomInfoDTO> allRooms = gameRooms.values().stream()
                .map(this::mapGameRoomToRoomInfoDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int totalRooms = allRooms.size();
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalRooms);

        if (fromIndex >= totalRooms) {
            return Collections.emptyList();
        }
        return allRooms.subList(fromIndex, toIndex);
    }

    private RoomInfoDTO mapGameRoomToRoomInfoDTO(GameRoom room) {
        if (room == null) return null;
        return new RoomInfoDTO(
                room.getRoomId(),
                room.getRoomName(),
                room.getPlayers() != null ? room.getPlayers().size() : 0,
                room.getMaxPlayers(),
                room.getGamePhase()
        );
    }

    public long getTotalRoomCount() {
        return gameRooms.size();
    }
}
