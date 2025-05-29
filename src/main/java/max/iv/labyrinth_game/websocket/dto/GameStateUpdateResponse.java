package max.iv.labyrinth_game.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import max.iv.labyrinth_game.model.game.GameRoom;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameStateUpdateResponse extends BaseMessage {
    private String roomId;
    private GameRoom gameRoomSnapshot;
    {
        this.setType(GameMessageType.GAME_STATE_UPDATE);
    }
}
