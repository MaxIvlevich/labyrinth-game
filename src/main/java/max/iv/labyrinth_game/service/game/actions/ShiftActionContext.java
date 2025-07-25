package max.iv.labyrinth_game.service.game.actions;

import lombok.Getter;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.enums.Direction;

import java.util.UUID;
@Getter
public class ShiftActionContext extends ActionContext{
    private final int shiftIndex;
    private final Direction shiftDirection;
    private final int newOrientation;
    public ShiftActionContext(GameRoom room, UUID playerId, int shiftIndex, Direction shiftDirection, int newOrientation) {
        super(room, playerId);
        this.shiftIndex = shiftIndex;
        this.shiftDirection = shiftDirection;
        this.newOrientation = newOrientation;
    }
}
