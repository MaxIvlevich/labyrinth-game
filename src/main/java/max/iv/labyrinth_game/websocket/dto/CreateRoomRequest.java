package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CreateRoomRequest extends BaseMessage {
    private int maxPlayers;


    public CreateRoomRequest(int maxPlayers, String playerName) {
        super(GameMessageType.CREATE_ROOM);
        this.maxPlayers = maxPlayers;

    }
}
