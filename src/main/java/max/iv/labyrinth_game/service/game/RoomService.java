package max.iv.labyrinth_game.service.game;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.enums.PlayerStatus;
import max.iv.labyrinth_game.websocket.dto.RoomInfoDTO;
import max.iv.labyrinth_game.websocket.events.lobby.LobbyRoomListNeedsUpdateEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoomService {

    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;
    private final GameValidator gameValidator;
    public RoomService(ApplicationEventPublisher eventPublisher, GameValidator gameValidator) {
        this.eventPublisher = eventPublisher;
        this.gameValidator = gameValidator;
    }
    public GameRoom createRoom(int maxPlayers,String roomName) {
        validateMaxPlayers(maxPlayers);
        GameRoom room = new GameRoom(maxPlayers,roomName);
        gameRooms.put(room.getRoomId(), room);
        log.info("Room created: {} with max players: {} room name {}", room.getRoomId(), maxPlayers,roomName);
        return room;
    }

    public GameRoom joinRoom(String roomId, String playerName) {
        GameRoom room = getExistingRoom(roomId);
        gameValidator.validateRoomForJoin(room);
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

        int currentPlayerCount = (int) room.getPlayers().stream()
                .filter(p -> p.getStatus() == PlayerStatus.CONNECTED)
                .count();
        return new RoomInfoDTO(
                room.getRoomId(),
                room.getRoomName(),
                currentPlayerCount,
                room.getMaxPlayers(),
                room.getGamePhase()
        );
    }

    public long getTotalRoomCount() {
        return gameRooms.size();
    }
    public void removeRoom(String roomId) {
        GameRoom removedRoom = gameRooms.remove(roomId);
        if (removedRoom != null) {
            log.info("Room {} was empty and has been removed.", roomId);
            eventPublisher.publishEvent(new LobbyRoomListNeedsUpdateEvent(this));
        }
    }
}
