package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RoomCreatedResponse extends BaseMessage{
    private String roomId;
    private String playerId; // ID игрока, который создал комнату (его внутренний ID, не сессии)
    public RoomCreatedResponse(String roomId, String playerId) {
        super(GameMessageType.ROOM_CREATED);
        this.roomId = roomId;
        this.playerId = playerId;
    }
}
