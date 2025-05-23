package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RoomCreatedResponse extends BaseMessage{
    private String roomId;
    private UUID playerId;
    public RoomCreatedResponse(String roomId, UUID playerId) {
        super(GameMessageType.ROOM_CREATED);
        this.roomId = roomId;
        this.playerId = playerId;
    }
}
