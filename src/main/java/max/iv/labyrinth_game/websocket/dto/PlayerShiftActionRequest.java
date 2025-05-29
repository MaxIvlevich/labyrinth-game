package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import max.iv.labyrinth_game.model.game.enums.Direction;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PlayerShiftActionRequest extends BaseMessage {

    private String roomId;
    private int shiftIndex;
    private Direction shiftDirection;

    public PlayerShiftActionRequest(String roomId, int shiftIndex, Direction shiftDirection) {
        super(GameMessageType.PLAYER_ACTION_SHIFT);
        this.roomId = roomId;
        this.shiftIndex = shiftIndex;
        this.shiftDirection = shiftDirection;
    }
}
