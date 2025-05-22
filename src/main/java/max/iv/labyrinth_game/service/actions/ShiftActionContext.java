package max.iv.labyrinth_game.service.actions;

import lombok.Getter;
import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.enums.Direction;

import java.util.UUID;
@Getter
public class ShiftActionContext extends ActionContext{
    private final int shiftIndex;
    private final Direction shiftDirection;
    public ShiftActionContext(GameRoom room, UUID playerId, int shiftIndex, Direction shiftDirection) {
        super(room, playerId);
        this.shiftIndex = shiftIndex;
        this.shiftDirection = shiftDirection;
    }
}
