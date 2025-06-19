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
import max.iv.labyrinth_game.model.game.enums.PlayerStatus;
import max.iv.labyrinth_game.service.game.actions.MoveActionContext;
import max.iv.labyrinth_game.service.game.actions.ShiftActionContext;
import max.iv.labyrinth_game.websocket.events.lobby.LobbyRoomListNeedsUpdateEvent;
import max.iv.labyrinth_game.websocket.events.lobby.RoomStateNeedsBroadcastEvent;
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
        log.info("Handling status change for disconnected player ID {} in room ID {}", disconnectedPlayerId, roomId);
        GameRoom room = roomService.getRoom(roomId); // Предполагаем, что getRoom бросает исключение, если комната не найдена

        Optional<Player> playerOpt = room.getPlayers().stream()
                .filter(p -> p.getId().equals(disconnectedPlayerId))
                .findFirst();

        if (playerOpt.isEmpty()) {
            log.warn("Player ID {} not found in room {} during disconnect handling.", disconnectedPlayerId, roomId);
            return room; // Ничего не делаем, если игрока и так нет
        }

        Player disconnectedPlayer = playerOpt.get();

        // Если игра уже завершена, ничего не меняем, просто выходим
        if (room.getGamePhase() == GamePhase.GAME_OVER) {
            log.info("Game in room {} was already over. No logic changes for disconnect of player {}.", roomId, disconnectedPlayer.getName());
            return room;
        }

        // 1. Главное изменение: меняем статус вместо удаления
        disconnectedPlayer.setStatus(PlayerStatus.DISCONNECTED);
        log.info("Player {} (name: {}) marked as DISCONNECTED in room {}.",
                disconnectedPlayer.getId(), disconnectedPlayer.getName(), roomId);

        // 2. Проверяем, не закончилась ли игра после этого отключения
        boolean gameEnded = checkAndHandleGameEndConditions(room);

        // 3. Если игра не закончилась, разбираемся с передачей хода
        if (!gameEnded) {
            handleTurnAfterDisconnect(room, disconnectedPlayer);
        }
        return room;
    }

    private boolean checkAndHandleGameEndConditions(GameRoom room) {

        List<Player> activePlayers = room.getPlayers().stream()
                .filter(p -> p.getStatus() == PlayerStatus.CONNECTED)
                .toList();
        boolean wasGameActive = room.getGamePhase().isActivePlayPhase();
        // Сценарий 1: Остался один игрок (или ноль). Игра окончена.
        if (activePlayers.size() < 2 && wasGameActive) {
            log.info("Less than 2 active players left in room {}. Game over.", room.getRoomId());
            room.setGamePhase(GamePhase.GAME_OVER);
            room.setCurrentPlayerIndex(-1); // Хода больше нет

            if (activePlayers.size() == 1) {
                Player winner = activePlayers.get(0);
                room.setWinner(winner);
                log.info("Player {} is the winner by default in room {}.", winner.getName(), room.getRoomId());
            } else {
                room.setWinner(null); // Победителя нет, если все отключились
            }
            return true; // Игра завершена
        }

        // Сценарий 2: Все игроки в комнате отключились.
        boolean allDisconnected = room.getPlayers().stream().allMatch(p -> p.getStatus() == PlayerStatus.DISCONNECTED);
        if(allDisconnected && wasGameActive){
            log.info("All players in room {} are disconnected. Setting phase to GAME_OVER.", room.getRoomId());
            room.setGamePhase(GamePhase.GAME_OVER);
            room.setWinner(null);
            room.setCurrentPlayerIndex(-1);
            return true; // Игра завершена
        }

        return false;
    }

    private void handleTurnAfterDisconnect(GameRoom room, Player disconnectedPlayer) {
        boolean wasCurrentPlayer = room.getCurrentPlayer() != null && room.getCurrentPlayer().getId().equals(disconnectedPlayer.getId());

        if (wasCurrentPlayer) {
            log.info("Current player {} disconnected. Passing turn to the next active player.", disconnectedPlayer.getName());

            int startIndex = room.getCurrentPlayerIndex();
            // Ищем по кругу, начиная со следующего за отключившимся
            for (int i = 1; i <= room.getPlayers().size(); i++) {
                int nextIndex = (startIndex + i) % room.getPlayers().size();
                Player nextPlayer = room.getPlayers().get(nextIndex);

                if (nextPlayer.getStatus() == PlayerStatus.CONNECTED) {
                    room.setCurrentPlayerIndex(nextIndex);
                    room.setGamePhase(GamePhase.PLAYER_SHIFT); // Новый игрок всегда начинает со сдвига
                    log.info("Turn passed to player {} (name: {}) in room {}.",
                            nextPlayer.getId(), nextPlayer.getName(), room.getRoomId());
                    return; // Ход передан, выходим
                }
            }
        }
        // Индексы не сдвинулись, ход остается у того же игрока.
        else {
            log.info("Non-current player {} disconnected. Game continues. Current turn belongs to {}.",
                    disconnectedPlayer.getName(), room.getCurrentPlayer().getName());
        }
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

