package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreateRoomRequest extends BaseMessage {
    private int maxPlayers;

    public CreateRoomRequest(int maxPlayers) {
        super(GameMessageType.CREATE_ROOM);
        this.maxPlayers = maxPlayers;

    }
}
