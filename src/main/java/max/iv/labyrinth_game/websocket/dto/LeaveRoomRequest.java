package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
public class LeaveRoomRequest extends BaseMessage{
    public LeaveRoomRequest() {
        super(GameMessageType.LEAVE_ROOM);
    }
}
