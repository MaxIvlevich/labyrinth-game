package max.iv.labyrinth_game.service.game.actions;

import max.iv.labyrinth_game.exceptions.ErrorType;
import max.iv.labyrinth_game.exceptions.game.GameLogicException;
import max.iv.labyrinth_game.model.game.enums.GamePhase;
import max.iv.labyrinth_game.service.game.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.function.BiConsumer;
@Service
public class GameActionDispatcher {
    private final GameService gameService;
    private final EnumMap<GamePhase, BiConsumer<ShiftActionContext, GameService>> shiftActionHandlers;
    private final EnumMap<GamePhase, BiConsumer<MoveActionContext, GameService>> moveActionHandlers;

    @Autowired
    public GameActionDispatcher(GameService gameService) {
        this.gameService = gameService;
        this.shiftActionHandlers = new EnumMap<>(GamePhase.class);
        this.moveActionHandlers = new EnumMap<>(GamePhase.class);
        initializeHandlers();
    }
    private void initializeHandlers() {
        // --- ЛОГИКА ПРОВЕРКИ  ---

        // Разрешаем сдвиг только в фазе PLAYER_SHIFT
        shiftActionHandlers.put(GamePhase.PLAYER_SHIFT, (ctx, service) -> service.performShiftAction(ctx));

        // Разрешаем ход только в фазе PLAYER_MOVE
        moveActionHandlers.put(GamePhase.PLAYER_MOVE, (ctx, service) -> service.performMoveAction(ctx));
    }

    public void dispatchShiftAction(ShiftActionContext context) {
        GamePhase currentPhase = context.getRoom().getGamePhase();
        BiConsumer<ShiftActionContext, GameService> handler = shiftActionHandlers.get(currentPhase);

        if (handler != null) {
            handler.accept(context, gameService);
        } else {
            throw new GameLogicException("Cannot perform SHIFT action in phase: " + currentPhase,
                    ErrorType.INVALID_PHASE_FOR_ACTION);
        }
    }

    public void dispatchMoveAction(MoveActionContext context) {
        GamePhase currentPhase = context.getRoom().getGamePhase();
        BiConsumer<MoveActionContext, GameService> handler = moveActionHandlers.get(currentPhase);

        if (handler != null) {
            handler.accept(context, gameService);
        } else {
            throw new GameLogicException("Cannot perform MOVE action in phase: " + currentPhase,
                    ErrorType.INVALID_PHASE_FOR_ACTION);
        }
    }
}
