package max.iv.labyrinth_game.service;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.Board;
import max.iv.labyrinth_game.model.Cell;
import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.Marker;
import max.iv.labyrinth_game.model.Player;
import max.iv.labyrinth_game.model.Tile;
import max.iv.labyrinth_game.model.enums.Direction;
import max.iv.labyrinth_game.model.enums.GamePhase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class GameService {

    private final RoomService roomService;
    private final BoardSetupService boardSetupService;
    private final GameValidator gameValidator;
    private final BoardShiftService boardShiftService;
    private final Random random = new Random();

    @Autowired
    public GameService(RoomService roomService, BoardSetupService boardSetupService, GameValidator gameValidator, BoardShiftService boardShiftService) {
        this.roomService = roomService;
        this.boardSetupService = boardSetupService;
        this.gameValidator = gameValidator;
        this.boardShiftService = boardShiftService;
    }

    public GameRoom startGame(String roomId) {
        GameRoom room = roomService.getRoom(roomId);
        gameValidator.validateRoomBeforeGameStart(room);

        Board board = boardSetupService.setupBoard(room);
        room.setBoard(board);
        room.setCurrentPlayerIndex(random.nextInt(room.getPlayers().size()));
        room.setGamePhase(GamePhase.PLAYER_SHIFT);
        room.getCurrentPlayer().setHasShiftedThisTurn(false);

        log.info("Game started in room: {}", roomId);
        return room;
    }

    public GameRoom performTurn(String roomId, UUID playerId, int shiftIndex, Direction shiftDirection, int moveToX, int moveToY) {
        GameRoom room = roomService.getRoom(roomId);
        gameValidator.validateGameInProgress(room);
        gameValidator.validatePlayerTurn(room, playerId);

        Player currentPlayer = room.getCurrentPlayer();
        validateAndPerformShift(room, currentPlayer, shiftIndex, shiftDirection);
        validateAndPerformMove(room, currentPlayer, moveToX, moveToY);

        endTurn(room);
        log.info("Player {} (ID: {}) performed a turn in room {}. New current player: {}",
                currentPlayer.getName(), currentPlayer.getId(), roomId,
                room.getCurrentPlayer() != null ? room.getCurrentPlayer().getName() : "N/A");

        return room;
    }

    private void validateAndPerformShift(GameRoom room, Player player, int shiftIndex, Direction direction) {
        if (player.hasShiftedThisTurn() && isValidShift(shiftIndex, direction)) {
            log.warn("Player {} attempted to shift again in the same turn.", player.getId());
            throw new IllegalStateException("Player already shifted the board this turn.");
        }

        if (isValidShift(shiftIndex, direction)) {
            boardShiftService.shiftBoard(room.getBoard(), shiftIndex, direction, room.getPlayers());
            player.setHasShiftedThisTurn(true);
        } else if (!player.hasShiftedThisTurn() && (shiftIndex != -1 || direction != null)) {
            log.error("Inconsistent turn state for player {}: shift parameters present but hasShiftedThisTurn is false.", player.getId());
        }
    }

    private void validateAndPerformMove(GameRoom room, Player player, int moveToX, int moveToY) {
        Point current = new Point(player.getCurrentX(), player.getCurrentY());
        Point target = new Point(moveToX, moveToY);

        if (!current.equals(target)) {
            if (!canMoveTo(room.getBoard(), player, moveToX, moveToY)) {
                log.warn("Player {} cannot move from {} to {}. Path not found.", player.getId(), current, target);
                throw new IllegalArgumentException("Cannot move to the specified cell. No valid path.");
            }
            log.info("Player {} moving from {} to {}", player.getId(), current, target);
            player.moveTo(moveToX, moveToY);
            collectMarkerIfPresent(room.getBoard(), player);
        } else {
            log.info("Player {} chose not to move from {}.", player.getId(), current);
        }
    }

    private boolean isValidShift(int shiftIndex, Direction shiftDirection) {
        return shiftIndex != -1 && shiftDirection != null;
    }

    private void collectMarkerIfPresent(Board board, Player player) {
        Cell cell = board.getCell(player.getCurrentX(), player.getCurrentY());
        if (cell == null) return;

        Marker marker = Optional.ofNullable(cell.getTile())
                .map(Tile::getMarker)
                .orElse(cell.getStationaryMarker());

        if (marker != null && player.getTargetMarkerIds().contains(marker.getId())) {
            player.collectMarker(marker);
            if (marker.equals(cell.getStationaryMarker())) {
                cell.setStationaryMarker(null);
            } else {
                cell.getTile().setMarker(null);
            }
            log.info("Player {} collected marker {}", player.getName(), marker.getId());
        }
    }

    private boolean canMoveTo(Board board, Player player, int targetX, int targetY) {
        if (board == null || player == null) {
            log.warn("Board or Player is null in canMoveTo. Board: {}, Player: {}", board, player);
            return false;
        }

        if (!board.isValidCoordinate(player.getCurrentX(), player.getCurrentY()) ||
                !board.isValidCoordinate(targetX, targetY)) {
            log.warn("Invalid coordinates for pathfinding. Start: ({},{}), Target: ({},{}).",
                    player.getCurrentX(), player.getCurrentY(), targetX, targetY);
            return false;
        }

        if (player.getCurrentX() == targetX && player.getCurrentY() == targetY) {
            return true;
        }

        return findPathBfs(board, player.getCurrentX(), player.getCurrentY(), targetX, targetY);
    }

    private boolean findPathBfs(Board board, int startX, int startY, int targetX, int targetY) {
        Queue<Point> queue = new LinkedList<>();
        Set<Point> visited = new HashSet<>();
        Point start = new Point(startX, startY);

        queue.offer(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Point current = queue.poll();
            if (current.x == targetX && current.y == targetY) {
                return true;
            }

            Cell cell = board.getCell(current.x, current.y);
            if (cell == null) continue;

            for (Direction direction : Direction.values()) {
                if (!cell.connectsTo(direction)) continue;

                int nextX = current.x + direction.getDx();
                int nextY = current.y + direction.getDy();

                if (!board.isValidCoordinate(nextX, nextY)) continue;

                Cell neighbor = board.getCell(nextX, nextY);
                if (neighbor == null || !neighbor.connectsTo(direction.opposite())) continue;

                Point neighborPoint = new Point(nextX, nextY);
                if (visited.add(neighborPoint)) {
                    queue.offer(neighborPoint);
                }
            }
        }

        return false;
    }

    private void endTurn(GameRoom room) {
        room.getCurrentPlayer().setHasShiftedThisTurn(false);
        int nextIndex = (room.getCurrentPlayerIndex() + 1) % room.getPlayers().size();
        room.setCurrentPlayerIndex(nextIndex);
        room.setGamePhase(GamePhase.PLAYER_SHIFT);
    }

    private record Point(int x, int y) {
    }
}

