package max.iv.labyrinth_game.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.Base;
import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.Player;
import max.iv.labyrinth_game.model.enums.GamePhase;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
@Service
@AllArgsConstructor
public class RoomService {

    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    public GameRoom createRoom(int maxPlayers) {
        validateMaxPlayers(maxPlayers);
        GameRoom room = new GameRoom(maxPlayers);
        gameRooms.put(room.getRoomId(), room);
        log.info("Room created: {} with max players: {}", room.getRoomId(), maxPlayers);
        return room;
    }

    public GameRoom joinRoom(String roomId, String playerName, String playerColor) {
        GameRoom room = getExistingRoom(roomId);
        validateRoomForJoin(room);

        Player player = new Player(UUID.randomUUID(), playerName, playerColor, new Base(0, 0, Set.of()));
        room.addPlayer(player);

        log.info("Player {} ({}) joined room {}.", playerName, player.getId(), roomId);
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

    private void validateRoomForJoin(GameRoom room) {
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
}
