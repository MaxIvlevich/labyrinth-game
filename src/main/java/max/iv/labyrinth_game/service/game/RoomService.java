package max.iv.labyrinth_game.service.game;

import com.opencsv.bean.CsvToBean;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.enums.GamePhase;
import max.iv.labyrinth_game.websocket.dto.RoomSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    public Page<RoomSummaryDTO> getAllRooms() {
        int pageNumber = 0;
        int pageSize= 8 ;

        List<GameRoom> allRoomsList = new ArrayList<>(gameRooms.values());
        allRoomsList.sort(Comparator.comparing(GameRoom::getRoomId));

        int totalRooms = allRoomsList.size();
        int startIndex = 0;
        int endIndex = Math.min(startIndex + pageSize, totalRooms);

        List<RoomSummaryDTO> roomSummariesOnPage;
        if (startIndex >= totalRooms) {
            roomSummariesOnPage = Collections.emptyList();
        } else {
            roomSummariesOnPage = allRoomsList.subList(startIndex, endIndex).stream()
                    .map(this::mapToRoomSummaryDTO)
                    .collect(Collectors.toList());
        }

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return new PageImpl<>(roomSummariesOnPage, pageable, totalRooms);
    }


}
