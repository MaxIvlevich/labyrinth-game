package max.iv.labyrinth_game.service.game;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.game.Board;
import max.iv.labyrinth_game.model.game.Cell;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.Marker;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.model.game.enums.Direction;
import max.iv.labyrinth_game.model.game.enums.GamePhase;
import max.iv.labyrinth_game.model.game.enums.PlayerAvatar;
import max.iv.labyrinth_game.service.game.actions.MoveActionContext;
import max.iv.labyrinth_game.service.game.actions.ShiftActionContext;
import max.iv.labyrinth_game.websocket.events.lobby.LobbyRoomListNeedsUpdateEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EventListener;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
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
    private final List<PlayerAvatar> avatars = PlayerAvatar.getAllAvatars();

    @Autowired
    public GameService(RoomService roomService, BoardSetupService boardSetupService, GameValidator gameValidator,
                       BoardShiftService boardShiftService) {
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

    public GameRoom performShiftAction(String roomId, UUID playerId, int shiftIndex, Direction shiftDirection) {
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
        return room;
    }

    public GameRoom performMoveAction(String roomId, UUID playerId, int moveToX, int moveToY) {
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
        return room;
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
        if (room.getGamePhase() != GamePhase.PLAYER_MOVE) {
            log.error("CRITICAL: handleMoveInMovePhaseLogic called in incorrect phase {} for room {}",
                    room.getGamePhase(), room.getRoomId());
            throw new IllegalStateException("Internal error: Move logic called in wrong phase.");
        }

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

            return;
        }
        self.endTurn(room);
    }
    private boolean checkWinCondition(Player player) {
        if (player == null) {
            return false;
        }
        if (player.isReadyToWin()) {
            log.info("Player {} (ID: {}) met win conditions!", player.getName(), player.getId());
            return true;
        }
        return false;
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

    public  GameRoom addPlayerToRoom(String roomId, Player newPlayer) {
        GameRoom room = roomService.getRoom(roomId);
        roomService.validateRoomForJoin(room);
        UUID newPlayerId = newPlayer.getId();
        boolean alreadyInRoom = room.getPlayers().stream()
                .anyMatch(p -> p.getId().equals(newPlayerId));

        if (alreadyInRoom) {
            log.warn("Player {} is already in room {}. Join request denied.", newPlayerId, roomId);
            throw new IllegalStateException("You are already in this room.");
        }
        int playerCount = room.getPlayers().size();
        if (playerCount < avatars.size()) {
            newPlayer.setAvatar(avatars.get(playerCount));
        } else {
            log.warn("Not enough unique avatars for player count {}. Using default.", playerCount);
            // Можно предусмотреть дефолтный аватар
        }

        room.addPlayer(newPlayer);
        log.info("Player {} (ID: {}) added to room {} by GameService. Players in room: {}",
                newPlayer.getName(), newPlayer.getId(), roomId, room.getPlayers().size());

        // 5. Если комната заполнилась, начинаем игру
        if (room.isFull() && room.getGamePhase() == GamePhase.WAITING_FOR_PLAYERS) {
            log.info("Room {} is now full with {} players. Starting game...", roomId, room.getPlayers().size());
            this.startGame(roomId);
        }
        return room;
    }

    public GameRoom handlePlayerDisconnect(UUID disconnectedPlayerId, String roomId) {
        log.info("Handling disconnect for player ID {} in room ID {}", disconnectedPlayerId, roomId);
        GameRoom room = roomService.getRoom(roomId);

        if (room == null) {
            log.warn("Room {} not found during disconnect handling for player {}. Cannot process disconnect.", roomId, disconnectedPlayerId);
            throw new IllegalArgumentException("Room not found: " + roomId);
        }

        List<Player> playersInRoom = room.getPlayers();
        Optional<Player> playerToRemoveOpt = playersInRoom.stream()
                .filter(p -> p.getId().equals(disconnectedPlayerId))
                .findFirst();

        if (playerToRemoveOpt.isEmpty()) {
            log.warn("Player ID {} not found in room {} during disconnect handling. Room state might be inconsistent or player already removed.", disconnectedPlayerId, roomId);
            return room;
        }

        Player disconnectedPlayer = playerToRemoveOpt.get();
        boolean wasCurrentPlayer = room.getCurrentPlayer() != null && room.getCurrentPlayer().getId().equals(disconnectedPlayerId);
        int indexOfDisconnectedPlayer = playersInRoom.indexOf(disconnectedPlayer); // Получаем индекс ДО удаления

        // 1. Удаляем игрока из списка игроков комнаты
        playersInRoom.remove(disconnectedPlayer);
        log.info("Player {} (name: {}) removed from room {}. Remaining players: {}",
                disconnectedPlayer.getId(), disconnectedPlayer.getName(), roomId, playersInRoom.size());

        // Если игра уже была завершена, просто выходим, состояние комнаты не меняем (кроме удаления игрока)
        if (room.getGamePhase() == GamePhase.GAME_OVER) {
            log.info("Game in room {} was already over. Player {} removed, no further game logic changes.", roomId, disconnectedPlayer.getName());
            return room;
        }

        // 2. Обрабатываем игровую ситуацию в зависимости от количества оставшихся игроков
        if (playersInRoom.isEmpty()) {
            // Комната стала пустой
            log.info("Room {} is now empty after player {} disconnected.", roomId, disconnectedPlayer.getName());
            room.setGamePhase(GamePhase.WAITING_FOR_PLAYERS); // Или GAME_OVER, если это более подходящее состояние для пустой комнаты после игры
            room.setWinner(null);
            room.setCurrentPlayerIndex(-1); // Нет текущего игрока
            // RoomService может позже иметь логику для удаления пустых неактивных комнат
        } else if (playersInRoom.size() < 2 && room.getGamePhase() != GamePhase.WAITING_FOR_PLAYERS) {
            // Игроков стало меньше двух (т.е. остался один или ноль), и игра шла (не была в ожидании)
            log.info("Less than 2 players left in room {} after disconnect. Game over.", roomId);
            room.setGamePhase(GamePhase.GAME_OVER);
            if (playersInRoom.size() == 1) {
                Player soleSurvivor = playersInRoom.get(0);
                room.setWinner(soleSurvivor);
                log.info("Player {} (name: {}) is the winner by default in room {}.", soleSurvivor.getId(), soleSurvivor.getName(), roomId);
            } else {
                room.setWinner(null); // Если 0 игроков
            }
            room.setCurrentPlayerIndex(-1);
        } else if (wasCurrentPlayer) {
            // Отключился текущий игрок, и в комнате есть еще как минимум один игрок для продолжения
            log.info("Current player {} (name: {}) disconnected from room {}. Passing turn.",
                    disconnectedPlayer.getId(), disconnectedPlayer.getName(), roomId);

            int newCurrentPlayerIndex = indexOfDisconnectedPlayer % playersInRoom.size();

            room.setCurrentPlayerIndex(newCurrentPlayerIndex);
            room.setGamePhase(GamePhase.PLAYER_SHIFT); // Новый текущий игрок начинает со сдвига
            log.info("Turn passed to player {} (name: {}) in room {} after disconnect. Phase set to PLAYER_SHIFT.",
                    room.getCurrentPlayer().getId(), room.getCurrentPlayer().getName(), roomId);
        } else {

            if (indexOfDisconnectedPlayer < room.getCurrentPlayerIndex()) {
                room.setCurrentPlayerIndex(room.getCurrentPlayerIndex() - 1);
            }
            if (room.getCurrentPlayerIndex() >= playersInRoom.size() || room.getCurrentPlayerIndex() < 0) {
                log.warn("currentPlayerIndex out of bounds ({}) after non-current player disconnect in room {}. Resetting to 0.", room.getCurrentPlayerIndex(), roomId);
                room.setCurrentPlayerIndex(0);
                if (room.getGamePhase() != GamePhase.WAITING_FOR_PLAYERS) {
                    room.setGamePhase(GamePhase.PLAYER_SHIFT);
                }
            }

            log.info("Non-current player {} (name: {}) disconnected from room {}. Game continues. Current turn: {} (name: {}).",
                    disconnectedPlayer.getId(), disconnectedPlayer.getName(), roomId,
                    room.getCurrentPlayer().getId(), room.getCurrentPlayer().getName());
        }
        return room;
    }

    public boolean  removePlayerFromRoom(UUID playerId, String roomId) {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return false;

        boolean playerRemoved = room.getPlayers().removeIf(player -> player.getId().equals(playerId));

        if (playerRemoved) {
            log.info("Player {} was successfully removed from the player list of room {}.", playerId, roomId);

            // Проверяем, не стала ли комната пустой
            if (room.getPlayers().isEmpty()) {
                roomService.removeRoom(roomId); // RoomService сам себя чистит
                return true; // Сообщаем, что комната была удалена
            }
        } else {
            log.warn("Attempted to remove player {} from room {}, but player was not found.", playerId, roomId);
        }
        return false;
    }
    private record Point(int x, int y) {
    }
}

