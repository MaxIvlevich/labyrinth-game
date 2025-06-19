package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReconnectToRoomRequest extends BaseMessage {
    private String roomId;
    public ReconnectToRoomRequest() {
        super(GameMessageType.RECONNECT_TO_ROOM);
    }
}
