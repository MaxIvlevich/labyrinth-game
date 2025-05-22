package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JoinRoomRequest extends BaseMessage{
    private String roomId;
    private String playerName;
    private String playerColor; // Пока строка, можно сделать enum

    public JoinRoomRequest(String roomId, String playerName, String playerColor) {
        super(GameMessageType.JOIN_ROOM);
        this.roomId = roomId;
        this.playerName = playerName;
        this.playerColor = playerColor;
    }
}
