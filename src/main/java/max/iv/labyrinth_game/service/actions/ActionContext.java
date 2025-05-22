package max.iv.labyrinth_game.service.actions;

import lombok.Getter;
import max.iv.labyrinth_game.model.GameRoom;
import max.iv.labyrinth_game.model.Player;

import java.util.UUID;
@Getter
public abstract class ActionContext {
    protected final GameRoom room;
    protected final UUID playerId;

    public ActionContext(GameRoom room, UUID playerId) {
        this.room = room;
        this.playerId = playerId;
    }
    public Player getCurrentPlayer() {
        return room.getCurrentPlayer();
    }
}
