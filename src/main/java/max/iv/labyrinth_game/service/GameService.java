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
import org.springframework.beans.factory.annotation.Autowired;
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
public class GameService {

    private final RoomService roomService;
    private final BoardSetupService boardSetupService;
    private final GameValidator gameValidator;
    private final Random random = new Random();


    @Autowired
    public GameService(RoomService roomService, BoardSetupService boardSetupService, GameValidator gameValidator) {
        this.roomService = roomService;
        this.boardSetupService = boardSetupService;
        this.gameValidator = gameValidator;
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






}

