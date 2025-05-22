package max.iv.labyrinth_game.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.config.BaseConfig;
import max.iv.labyrinth_game.config.BoardConfig;
import max.iv.labyrinth_game.config.BoardConfigLoader;
import max.iv.labyrinth_game.config.StationaryTileConfig;
import max.iv.labyrinth_game.model.Base;
import max.iv.labyrinth_game.model.Board;
import max.iv.labyrinth_game.model.Cell;
import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.Marker;
import max.iv.labyrinth_game.model.Player;
import max.iv.labyrinth_game.model.Tile;
import max.iv.labyrinth_game.model.enums.Direction;
import max.iv.labyrinth_game.model.enums.GamePhase;
import max.iv.labyrinth_game.model.enums.TileType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@AllArgsConstructor
public class GameService {

    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final BoardConfigLoader boardConfigLoader;

    private static final int TOTAL_UNIQUE_MARKER_TYPES = 12;

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

    public GameRoom startGame(String roomId) {
        GameRoom room = getExistingRoom(roomId);
        validateRoomBeforeGameStart(room);

        Board board = setupBoard(room);
        room.setBoard(board);
        room.setCurrentPlayerIndex(random.nextInt(room.getPlayers().size()));
        room.setGamePhase(GamePhase.PLAYER_SHIFT);
        room.getCurrentPlayer().setHasShiftedThisTurn(false);

        log.info("Game started in room: {}", roomId);
        return room;
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

    private void validateRoomForJoin(GameRoom room) {
        if (room.isFull()) {
            throw new IllegalStateException("Room is full: " + room.getRoomId());
        }
        if (room.getGamePhase() != GamePhase.WAITING_FOR_PLAYERS) {
            throw new IllegalStateException("Game already started or finished in room: " + room.getRoomId());
        }
    }

    private void validateRoomBeforeGameStart(GameRoom room) {
        if (room.getPlayers().size() < 2) {
            throw new IllegalStateException("Not enough players to start the game in room: " + room.getRoomId());
        }
        if (room.getGamePhase() != GamePhase.WAITING_FOR_PLAYERS) {
            throw new IllegalStateException("Game already started or finished in room: " + room.getRoomId());
        }
    }

    private Board setupBoard(GameRoom room) {
        List<Player> players = room.getPlayers();
        BoardConfig config = boardConfigLoader.getBoardConfig();
        Board board = new Board(config.boardSize());

        setupBases(board, players, config);
        setupStationaryTiles(board, players, config);

        List<Tile> movableTiles = generateMovableTiles(config.boardSize());
        Collections.shuffle(movableTiles);

        if (movableTiles.isEmpty()) {
            throw new IllegalStateException("Movable tiles pool is empty, cannot setup board.");
        }

        board.setExtraTile(movableTiles.remove(movableTiles.size() - 1));
        populateBoardWithTiles(board, movableTiles);

        List<Marker> markers = placeMarkers(board, players, config);
        board.setAllMarkersInGame(markers);
        assignMarkersToPlayers(markers, players);

        return board;
    }

    private void setupBases(Board board, List<Player> players, BoardConfig config) {
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (i >= config.bases().size())
                continue;

            BaseConfig baseConfig = config.bases().get(i);
            if (!board.isValidCoordinate(baseConfig.x(), baseConfig.y()))
                continue;

            Base newBase = new Base(baseConfig.x(), baseConfig.y(), parseDirections(baseConfig.exits()));
            player.updateBase(newBase);
            player.resetToMyBase();

            Cell baseCell = board.getCell(baseConfig.x(), baseConfig.y());
            baseCell.setFixedOpenSides(parseDirections(baseConfig.exits()));
        }
    }

    private void setupStationaryTiles(Board board, List<Player> players, BoardConfig config) {
        if (config.stationaryTiles() == null) return;

        for (StationaryTileConfig tileConfig : config.stationaryTiles()) {
            if (!board.isValidCoordinate(tileConfig.x(), tileConfig.y())) continue;

            Cell cell = board.getCell(tileConfig.x(), tileConfig.y());
            if (cell != null && cell.isStationary()) {
                try {
                    TileType type = TileType.valueOf(tileConfig.type().toUpperCase());
                    cell.setTile(new Tile(type, tileConfig.orientation()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private List<Tile> generateMovableTiles(int boardSize) {
        List<Tile> tiles = new ArrayList<>();
        addTiles(tiles, TileType.STRAIGHT, 13, 2);
        addTiles(tiles, TileType.CORNER, 15, 4);
        addTiles(tiles, TileType.T_SHAPED, 6, 4);
        return tiles;
    }

    private void addTiles(List<Tile> tiles, TileType type, int count, int orientations) {
        for (int i = 0; i < count; i++) {
            tiles.add(new Tile(type, random.nextInt(orientations)));
        }
    }

    private void populateBoardWithTiles(Board board, List<Tile> tiles) {
        int index = 0;
        for (int y = 0; y < board.getSize(); y++) {
            for (int x = 0; x < board.getSize(); x++) {
                Cell cell = board.getCell(x, y);
                if (!cell.isStationary() && index < tiles.size()) {
                    cell.setTile(tiles.get(index++));
                }
            }
        }
    }

    private List<Marker> placeMarkers(Board board, List<Player> players, BoardConfig config) {
        List<Marker> markers = IntStream.range(0, TOTAL_UNIQUE_MARKER_TYPES)
                .mapToObj(Marker::new)
                .collect(Collectors.toList());

        List<Cell> eligibleCells = board.getAllCells().stream()
                .filter(cell -> players.stream().noneMatch(p -> p.isBase(cell.getX(), cell.getY())))
                .collect(Collectors.toList());

        Collections.shuffle(eligibleCells);

        for (int i = 0; i < Math.min(markers.size(), eligibleCells.size()); i++) {
            Cell cell = eligibleCells.get(i);
            Marker marker = markers.get(i);
            if (cell.getTile() != null) {
                cell.getTile().setMarker(marker);
            } else {
                cell.setStationaryMarker(marker);
            }
        }

        return markers;
    }

    private void assignMarkersToPlayers(List<Marker> markers, List<Player> players) {
        Collections.shuffle(markers);
        int markersPerPlayer = TOTAL_UNIQUE_MARKER_TYPES / players.size();

        int index = 0;
        for (Player player : players) {
            player.getTargetMarkerIds().clear();
            for (int i = 0; i < markersPerPlayer && index < markers.size(); i++) {
                Marker marker = markers.get(index++);
                marker.setPlayerId(player.getId());
                player.getTargetMarkerIds().add(marker.getId());
            }
        }
    }

    private Set<Direction> parseDirections(List<String> directions) {
        return directions == null ? Collections.emptySet() :
                directions.stream()
                        .map(String::toUpperCase)
                        .filter(d -> {
                            try {
                                Direction.valueOf(d);
                                return true;
                            } catch (IllegalArgumentException e) {
                                return false;
                            }
                        })
                        .map(Direction::valueOf)
                        .collect(Collectors.toSet());
    }
}

