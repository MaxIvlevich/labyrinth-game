package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerMoveActionRequest extends BaseMessage{

    private String roomId;
    private int targetX;
    private int targetY;

    public PlayerMoveActionRequest(String roomId, int targetX, int targetY) {
        super(GameMessageType.PLAYER_ACTION_MOVE);
        this.roomId = roomId;
        this.targetX = targetX;
        this.targetY = targetY;
    }
}
