package max.iv.labyrinth_game.service.game.actions;

import lombok.Getter;
import max.iv.labyrinth_game.model.game.GameRoom;

import java.util.UUID;

@Getter
public class MoveActionContext  extends ActionContext{
    private final int targetX;
    private final int targetY;

    public MoveActionContext(GameRoom room, UUID playerId, int targetX, int targetY) {
        super(room, playerId);
        this.targetX = targetX;
        this.targetY = targetY;
    }
}
