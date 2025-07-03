package max.iv.labyrinth_game.service.game.actions;

import lombok.Getter;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.Player;

import java.util.UUID;
@Getter
public abstract class ActionContext {
    protected final GameRoom room;
    protected final UUID playerId;

    public ActionContext(GameRoom room, UUID playerId) {
        this.room = room;
        this.playerId = playerId;
    }
}
