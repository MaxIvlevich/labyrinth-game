package max.iv.labyrinth_game.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.Board;
import max.iv.labyrinth_game.model.Cell;
import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.Marker;
import max.iv.labyrinth_game.model.Player;
import max.iv.labyrinth_game.model.Tile;
import max.iv.labyrinth_game.model.enums.Direction;
import max.iv.labyrinth_game.model.enums.GamePhase;
import max.iv.labyrinth_game.service.actions.MoveActionContext;
import max.iv.labyrinth_game.service.actions.ShiftActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

@Slf4j
@Service
public class GameService {

    private final RoomService roomService;
    private final BoardSetupService boardSetupService;
    private final GameValidator gameValidator;
    private final BoardShiftService boardShiftService;
    private final Random random = new Random();

    // Карта для обработки действий сдвига в зависимости от фазы
    private EnumMap<GamePhase, BiConsumer<ShiftActionContext, GameService>> shiftActionHandlers;
    // Карта для обработки действий перемещения в зависимости от фазы
    private EnumMap<GamePhase, BiConsumer<MoveActionContext, GameService>> moveActionHandlers;

    @Autowired
    public GameService(RoomService roomService, BoardSetupService boardSetupService, GameValidator gameValidator, BoardShiftService boardShiftService) {
        this.roomService = roomService;
        this.boardSetupService = boardSetupService;
        this.gameValidator = gameValidator;
        this.boardShiftService = boardShiftService;
    }

    @PostConstruct
    private void initializeActionHandlers() {
        shiftActionHandlers = new EnumMap<>(GamePhase.class);
        shiftActionHandlers.put(GamePhase.PLAYER_SHIFT, GameService::handleShiftInShiftPhaseLogic);
        shiftActionHandlers.put(GamePhase.PLAYER_MOVE, (ctx, service) -> {
            log.warn("Attempted SHIFT action in {} phase for room {}", ctx.getRoom().getGamePhase(), ctx.getRoom().getRoomId());
            throw new IllegalStateException("Cannot perform shift action in phase: " + ctx.getRoom().getGamePhase());
        });
        moveActionHandlers = new EnumMap<>(GamePhase.class);
        moveActionHandlers.put(GamePhase.PLAYER_MOVE, GameService::handleMoveInMovePhaseLogic);
    }


    public GameRoom startGame(String roomId) {
        GameRoom room = roomService.getRoom(roomId);
        gameValidator.validateRoomBeforeGameStart(room);

        Board board = boardSetupService.setupBoard(room);
        room.setBoard(board);
        room.setCurrentPlayerIndex(random.nextInt(room.getPlayers().size()));
        room.setGamePhase(GamePhase.PLAYER_SHIFT);
        log.info("Game started in room: {}. First player: {} to make a SHIFT.",
                roomId, room.getCurrentPlayer().getName());
        return room;
    }

    public GameRoom performTurn(String roomId, UUID playerId, int shiftIndex, Direction shiftDirection, int moveToX, int moveToY) {
        GameRoom room = roomService.getRoom(roomId);
        gameValidator.validateGameInProgress(room);
        gameValidator.validatePlayerTurn(room, playerId);

        Player currentPlayer = room.getCurrentPlayer();
        performShiftAction(roomId, playerId, shiftIndex, shiftDirection);
        performMoveAction(roomId, playerId, moveToX, moveToY);

        log.info("Player {} (ID: {}) performed a turn in room {}. New current player: {}",
                currentPlayer.getName(), currentPlayer.getId(), roomId,
                room.getCurrentPlayer() != null ? room.getCurrentPlayer().getName() : "N/A");

        return room;
    }

    public void performShiftAction(String roomId, UUID playerId, int shiftIndex, Direction shiftDirection) {
        GameRoom room = roomService.getRoom(roomId);
        gameValidator.validatePlayerTurn(room, playerId);
        ShiftActionContext context = new ShiftActionContext(room, playerId, shiftIndex, shiftDirection);
        BiConsumer<ShiftActionContext, GameService> handler = shiftActionHandlers.get(room.getGamePhase());

        if (handler != null) {
            handler.accept(context, this); // Выполняем действие через найденный обработчик
        } else {
            log.warn("No SHIFT handler defined for game phase: {} in room {}", room.getGamePhase(), roomId);
            throw new IllegalStateException("Cannot perform shift action in phase: " + room.getGamePhase());
        }
    }

    public void performMoveAction(String roomId, UUID playerId, int moveToX, int moveToY) {
        GameRoom room = roomService.getRoom(roomId);
        gameValidator.validatePlayerTurn(room, playerId);
        MoveActionContext context = new MoveActionContext(room, playerId, moveToX, moveToY);
        BiConsumer<MoveActionContext, GameService> handler = moveActionHandlers.get(room.getGamePhase());
        if (handler != null) {
            handler.accept(context, this);
        } else {
            log.warn("No MOVE handler defined for game phase: {} in room {}", room.getGamePhase(), roomId);
            throw new IllegalStateException("Cannot perform move action in phase: " + room.getGamePhase());
        }
    }

    private static void handleShiftInShiftPhaseLogic(ShiftActionContext context, GameService self) {
        GameRoom room = context.getRoom();
        Player currentPlayer = context.getCurrentPlayer();

        log.info("Player {} (ID: {}) performing SHIFT in room {}: index {}, direction {}",
                currentPlayer.getName(), context.getPlayerId(), room.getRoomId(),
                context.getShiftIndex(), context.getShiftDirection());

        self.boardShiftService.shiftBoard(room.getBoard(), context.getShiftIndex(),
                context.getShiftDirection(), room.getPlayers());

        room.setGamePhase(GamePhase.PLAYER_MOVE);
        log.info("Room {} phase changed to PLAYER_TURN_MOVE for player {}",
                room.getRoomId(), currentPlayer.getName());
    }

    private static void handleMoveInMovePhaseLogic(MoveActionContext context, GameService self) {
        GameRoom room = context.getRoom();
        Player currentPlayer = context.getCurrentPlayer();
        Board board = room.getBoard();

        log.info("Player {} (ID: {}) performing MOVE in room {}: to ({},{})",
                currentPlayer.getName(), context.getPlayerId(), room.getRoomId(),
                context.getTargetX(), context.getTargetY());
        // Если игрок действительно пытается изменить позицию
        if (currentPlayer.getCurrentX() != context.getTargetX() || currentPlayer.getCurrentY() != context.getTargetY()) {
            // Проверяем возможность хода с помощью BFS
            if (!self.canMoveTo(board, currentPlayer, context.getTargetX(), context.getTargetY())) {
                log.warn("Player {} (ID: {}) cannot move from ({},{}) to ({},{}). Path not found.",
                        currentPlayer.getName(), context.getPlayerId(), currentPlayer.getCurrentX(), currentPlayer.getCurrentY(),
                        context.getTargetX(), context.getTargetY());
                throw new IllegalArgumentException("Cannot move to the specified cell. No valid path.");
            }
            // Перемещаем игрока
            currentPlayer.moveTo(context.getTargetX(), context.getTargetY());
            // Проверяем и собираем маркер, если он есть на новой клетке и является целью
            self.collectMarkerIfPresent(board, currentPlayer);
        } else {
            log.info("Player {} (ID: {}) chose not to move (stayed at ({},{})) in room {}",
                    currentPlayer.getName(), context.getPlayerId(), currentPlayer.getCurrentX(), currentPlayer.getCurrentY(), room.getRoomId());
        }
        // ПРОВЕРКА УСЛОВИЙ ПОБЕДЫ
        if (self.checkWinCondition(currentPlayer)) { // Передаем только игрока
            room.setWinner(currentPlayer); // Устанавливаем победителя в комнате
            room.setGamePhase(GamePhase.GAME_OVER); // Меняем фазу игры
            log.info("Player {} (ID: {}) WON the game in room {}!",
                    currentPlayer.getName(), context.getPlayerId(), room.getRoomId());

            // Важно: Если игра окончена, мы НЕ вызываем endTurn(), чтобы ход не передался дальше.
            // WebSocket хендлер должен будет отправить финальное состояние игры.
            return;
        }
        self.endTurn(room);
    }
    private boolean checkWinCondition(Player player) {
        if (player == null) {
            return false;
        }

        boolean allMarkersCollected = player.hasCollectedAllTargetMarkers();
        boolean onBase = player.isAtBase();

        if (allMarkersCollected) {
            log.info("Player {} (ID: {}) has collected all target markers. Now checking if on base...",
                    player.getName(), player.getId());
            if (onBase) {
                log.info("Player {} (ID: {}) is on base. WIN CONDITION MET.", player.getName(), player.getId());
                return true;
            } else {
                log.info("Player {} (ID: {}) has all markers but is not on base (at {},{} instead of {},{}).",
                        player.getName(), player.getId(), player.getCurrentX(), player.getCurrentY(), player.getBase().x(), player.getBase().y());
            }
        } else {
            log.trace("Player {} (ID: {}) has not collected all markers yet. Collected: {}, Targets: {}",
                      player.getName(), player.getId(), player.getCollectedMarkerIds(), player.getTargetMarkerIds());
        }
        return false;
    }
    private boolean isValidShift(int shiftIndex, Direction shiftDirection) {
        return shiftIndex != -1 && shiftDirection != null;
    }

    private void collectMarkerIfPresent(Board board, Player player) {
        Cell cell = board.getCell(player.getCurrentX(), player.getCurrentY());
        if (cell == null) return;
        Marker markerOnCell = cell.getActiveMarker();
        if (markerOnCell != null && player.getTargetMarkerIds().contains(markerOnCell.getId())) {
            player.collectMarker(markerOnCell);
            cell.removeActiveMarker();
            log.info("Player {} (ID: {}) collected marker {} at ({},{})",
                    player.getName(), player.getId(), markerOnCell.getId(), player.getCurrentX(), player.getCurrentY());
        }
    }

    private boolean canMoveTo(Board board, Player player, int targetX, int targetY) {
        if (board == null || player == null) return false;
        if (!board.isValidCoordinate(player.getCurrentX(), player.getCurrentY()) ||
                !board.isValidCoordinate(targetX, targetY)) {
            return false;
        }
        if (player.getCurrentX() == targetX && player.getCurrentY() == targetY) return true;
        return findPathBfs(board, player.getCurrentX(), player.getCurrentY(), targetX, targetY);
    }

    private boolean findPathBfs(Board board, int startX, int startY, int targetX, int targetY) {
        Queue<Point> queue = new LinkedList<>();
        Set<Point> visited = new HashSet<>();
        Point startPoint = new Point(startX, startY);
        queue.offer(startPoint);
        visited.add(startPoint);

        while (!queue.isEmpty()) {
            Point currentPoint = queue.poll();
            Cell currentCell = board.getCell(currentPoint.x, currentPoint.y);
            if (currentCell == null) continue;
            if (currentPoint.x == targetX && currentPoint.y == targetY) return true;

            for (Direction direction : Direction.values()) {
                if (currentCell.connectsTo(direction)) {
                    int nextX = currentPoint.x + direction.getDx();
                    int nextY = currentPoint.y + direction.getDy();
                    if (board.isValidCoordinate(nextX, nextY)) {
                        Cell neighborCell = board.getCell(nextX, nextY);
                        if (neighborCell != null && neighborCell.connectsTo(direction.opposite())) {
                            Point neighborPoint = new Point(nextX, nextY);
                            if (!visited.contains(neighborPoint)) {
                                visited.add(neighborPoint);
                                queue.offer(neighborPoint);
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void endTurn(GameRoom room) {
        if (room.getPlayers().isEmpty()) {
            log.warn("Attempted to end turn in a room {} with no players.", room.getRoomId());
            return;
        }
        if (room.getGamePhase() == GamePhase.GAME_OVER) {
            return;
        }
        int nextPlayerIndex = (room.getCurrentPlayerIndex() + 1) % room.getPlayers().size();
        room.setCurrentPlayerIndex(nextPlayerIndex);
        room.setGamePhase(GamePhase.PLAYER_SHIFT);

        log.info("Turn ended in room {}. Next player: {} (Index: {}). Phase set to PLAYER_TURN_SHIFT.",
                room.getRoomId(),
                room.getCurrentPlayer().getName(),
                nextPlayerIndex);
    }

    private record Point(int x, int y) {
    }

}

