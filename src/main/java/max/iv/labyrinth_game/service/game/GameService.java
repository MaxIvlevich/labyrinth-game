package max.iv.labyrinth_game.service.game;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.exceptions.ErrorType;
import max.iv.labyrinth_game.exceptions.game.GameLogicException;
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

    private final List<PlayerAvatar> avatars = PlayerAvatar.getAllAvatars();

    @Autowired
    public GameService(RoomService roomService, BoardSetupService boardSetupService, GameValidator gameValidator,
                       BoardShiftService boardShiftService) {
        this.roomService = roomService;
        this.boardSetupService = boardSetupService;
        this.gameValidator = gameValidator;
        this.boardShiftService = boardShiftService;

    }

    // @PostConstruct
            // private void initializeActionHandlers() {
        //     shiftActionHandlers = new EnumMap<>(GamePhase.class);
        //     shiftActionHandlers.put(GamePhase.PLAYER_SHIFT, GameService::handleShiftInShiftPhaseLogic);
        //     shiftActionHandlers.put(GamePhase.PLAYER_MOVE, (ctx, service) -> {
            //         log.warn("Attempted SHIFT action in {} phase for room {}", ctx.getRoom().getGamePhase(), ctx.getRoom().getRoomId());
            //         throw new IllegalStateException("Cannot perform shift action in phase: " + ctx.getRoom().getGamePhase());
            //     });
        //     moveActionHandlers = new EnumMap<>(GamePhase.class);
        //     moveActionHandlers.put(GamePhase.PLAYER_MOVE, GameService::handleMoveInMovePhaseLogic);
        // }

    public GameRoom startGame(String roomId) {
        log.info("startGame started");
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

    public void  performShiftAction(ShiftActionContext context) {
        GameRoom gameRoom = context.getRoom();
        Player currentPlayer = gameRoom.getCurrentPlayer();
        gameValidator.validatePlayerTurn(gameRoom, context.getPlayerId());
        log.info("Player {} (ID: {}) performing SHIFT in room {}: index {}, direction {}",
                currentPlayer.getName(), context.getPlayerId(), gameRoom.getRoomId(),
                context.getShiftIndex(), context.getShiftDirection());

        // 3. Выполняем основную логику сдвига
        try {
            // Делегируем сдвиг доски специализированному сервису
            boardShiftService.shiftBoard(gameRoom.getBoard(), context.getShiftIndex(),
                    context.getShiftDirection(), gameRoom.getPlayers());
        } catch (IllegalArgumentException e) {
            // Если boardShiftService обнаружил невалидные данные, "заворачиваем" ошибку
            throw new GameLogicException(e.getMessage(), ErrorType.INVALID_SHIFT);
        }

        // 4. После успешного сдвига меняем фазу игры на перемещение
        gameRoom.setGamePhase(GamePhase.PLAYER_MOVE);
        log.info("Room {} phase changed to PLAYER_MOVE for player {}",
                gameRoom.getRoomId(), currentPlayer.getName());
    }

    public void performMoveAction(MoveActionContext context) {
        GameRoom room = context.getRoom();
        Player currentPlayer = room.getCurrentPlayer();
        Board board = room.getBoard();
        int targetX = context.getTargetX();
        int targetY = context.getTargetY();

        gameValidator.validatePlayerTurn(room, context.getPlayerId());
        log.info("Player {} (ID: {}) performing MOVE in room {}: to ({},{})",
                currentPlayer.getName(), context.getPlayerId(), room.getRoomId(),
                targetX, targetY);
        if (currentPlayer.getCurrentX() != targetX || currentPlayer.getCurrentY() != targetY) {
            // Проверяем, можно ли дойти до этой клетки
            if (!canMoveTo(board, currentPlayer, targetX, targetY)) {
                log.warn("Player {} cannot move from ({},{}) to ({},{}). Path not found.",
                        currentPlayer.getName(),  currentPlayer.getCurrentX(), currentPlayer.getCurrentY(),
                        targetX, targetY);
                // Бросаем наше кастомное исключение с кодом ошибки
                throw new GameLogicException("Cannot move to the specified cell. No valid path.", ErrorType.INVALID_MOVE);
            }
            // Перемещаем игрока
            currentPlayer.moveTo(targetX, targetY);
            // Проверяем и собираем маркер
            collectMarkerIfPresent(board, currentPlayer);
        } else {
            log.info("Player {} chose not to move (stayed at ({},{})) in room {}",
                    currentPlayer.getName(),  currentPlayer.getCurrentX(), currentPlayer.getCurrentY(), room.getRoomId());
        }

        // 4. Проверяем, не победил ли игрок
        if (checkWinCondition(currentPlayer)) {
            room.setWinner(currentPlayer);
            room.setGamePhase(GamePhase.GAME_OVER);
            log.info("Player {} WON the game in room {}!", currentPlayer.getName(), room.getRoomId());
            // Возвращаемся, так как ход передавать не нужно
            return;
        }

        // 5. Если никто не победил, завершаем ход и передаем его следующему
        endTurn(room);
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
        gameValidator.validateRoomForJoin(room);
        UUID newPlayerId = newPlayer.getId();

        // --- НАЧАЛО ОТЛАДОЧНЫХ ЛОГОВ ---
        log.info("----------------- DEBUG: addPlayerToRoom_START -----------------");
        log.info("Trying to add player: ID={}, Name={}", newPlayerId, newPlayer.getName());
        log.info("Current players in room (before add): {}", room.getPlayers().size());
        // Выводим ID всех игроков, которые уже есть в комнате
        room.getPlayers().forEach(p -> log.info(" -> Existing player in list: ID={}, Name={}", p.getId(), p.getName()));

        // Проверяем, есть ли игрок с таким ID уже в списке
        boolean alreadyInRoom = room.getPlayers().stream()
                .anyMatch(p -> p.getId().equals(newPlayerId));

        log.info("Check 'alreadyInRoom' result: {}", alreadyInRoom);
        // --- КОНЕЦ ОТЛАДОЧНЫХ ЛОГОВ -

        if (alreadyInRoom) {
            // Игрок уже в комнате! Это не ошибка, а переподключение.
            room.getPlayers().stream()
                    .filter(p -> p.getId().equals(newPlayerId))
                    .findFirst()
                    .ifPresent(player -> {
                        log.info("Player {} ({}) is re-joining/already in room {}. Ensuring status is CONNECTED.",
                                player.getName(), newPlayerId, roomId);
                        player.setStatus(PlayerStatus.CONNECTED);
                    });
        } else {
            // Если игрока нет, это новый участник. Добавляем его.
            log.info("New player {} ({}) is joining the room {}.",
                    newPlayer.getName(), newPlayerId, roomId);
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
        }
        log.info("Current players in room (after add): {}", room.getPlayers().size());
        room.getPlayers().forEach(p -> log.info(" -> Final player in list: ID={}, Name={}", p.getId(), p.getName()));
        log.info("----------------- DEBUG: addPlayerToRoom_END -----------------");
        return room;
    }

    public void  handlePlayerDisconnect(UUID disconnectedPlayerId, String roomId) {
        log.info("handlePlayerDisconnect Player id  {}  room {}.", disconnectedPlayerId, roomId);

        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return; // Комнаты уже нет, ничего не делаем

        Player disconnectedPlayer = room.getPlayers().stream()
                .filter(p -> p.getId().equals(disconnectedPlayerId)).findFirst().orElse(null);

        if (disconnectedPlayer == null || disconnectedPlayer.getStatus() == PlayerStatus.DISCONNECTED) {
            return; // Игрока нет или он уже помечен как отключенный
        }

        log.info("Player {} marked as DISCONNECTED in room {}.", disconnectedPlayer.getName(), roomId);
        disconnectedPlayer.setStatus(PlayerStatus.DISCONNECTED);

        // Проверяем, не нужно ли завершить игру, ТОЛЬКО если все отключились
        checkAndHandleGameEndOnDisconnect(room);

        // Передаем ход, если отключился текущий игрок
        handleTurnAfterDisconnect(room, disconnectedPlayer);
    }

    private boolean permanentlyRemovePlayer(UUID playerId, String roomId) {

        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return false;

        boolean removed = room.getPlayers().removeIf(p -> p.getId().equals(playerId));

        if (removed) {
            log.info("Player {} has been physically removed from room {}.", playerId, roomId);

            // После физического удаления проверяем, не закончилась ли игра
            checkAndHandleGameEndConditions(room);

            if (room.getPlayers().isEmpty()) {
                log.info("Room {} is now empty and will be removed.", roomId);
                roomService.removeRoom(roomId);
                return true; // Комната была удалена
            }
        }
        return false;
    }
    private void checkAndHandleGameEndConditions(GameRoom room) {
        if (room.getGamePhase() == GamePhase.WAITING_FOR_PLAYERS) {
            return; // Никогда не завершаем игру в фазе ожидания
        }

        List<Player> activePlayers = room.getPlayers().stream()
                .filter(p -> p.getStatus() == PlayerStatus.CONNECTED)
                .toList();

        // Завершаем игру, если активных игроков осталось меньше 2
        if (activePlayers.size() < 2) {
            log.info("Less than 2 active players left in room {}. Game over.", room.getRoomId());
            room.setGamePhase(GamePhase.GAME_OVER);
            room.setCurrentPlayerIndex(-1);
            if (activePlayers.size() == 1) {
                room.setWinner(activePlayers.get(0));
            } else {
                room.setWinner(null);
            }
        }
    }

    // Этот метод вызывается ТОЛЬКО при дисконнекте, он НЕ завершает игру
    private void checkAndHandleGameEndOnDisconnect(GameRoom room) {
        if (room.getGamePhase() == GamePhase.WAITING_FOR_PLAYERS) {
            return;
        }
        boolean allPlayersDisconnected = room.getPlayers().stream()
                .allMatch(p -> p.getStatus() == PlayerStatus.DISCONNECTED);
        if(allPlayersDisconnected) {
            log.info("All players in room {} are disconnected. Marking game as over.", room.getRoomId());
            room.setGamePhase(GamePhase.GAME_OVER);
        }
    }

    private void handleTurnAfterDisconnect(GameRoom room, Player disconnectedPlayer) {

        Player currentPlayerBeforeDisconnect = room.getCurrentPlayer();

        // Если текущего игрока не было (фаза WAITING), или отключился не он, ничего не делаем с ходом.
        if (currentPlayerBeforeDisconnect == null || !currentPlayerBeforeDisconnect.equals(disconnectedPlayer)) {
            log.info("Turn state not changed. Current player was not the one who disconnected, or game hasn't started.");
            return;
        }

        // Если мы дошли сюда, значит, отключился текущий игрок в активной фазе.
        log.info("Current player {} disconnected. Passing turn to the next active player.", disconnectedPlayer.getName());

        int startIndex = room.getCurrentPlayerIndex();
        for (int i = 1; i <= room.getPlayers().size(); i++) {
            int nextIndex = (startIndex + i) % room.getPlayers().size();
            Player nextPlayer = room.getPlayers().get(nextIndex);

            if (nextPlayer.getStatus() == PlayerStatus.CONNECTED) {
                room.setCurrentPlayerIndex(nextIndex);
                room.setGamePhase(GamePhase.PLAYER_SHIFT);
                log.info("Turn passed to player {}.", nextPlayer.getName());
                return;
            }
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

