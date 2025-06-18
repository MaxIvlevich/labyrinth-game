package max.iv.labyrinth_game.websocket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreateRoomRequest extends BaseMessage {

    @Min(value = 2, message = "Maximum players must be at least 2")
    @Max(value = 4, message = "Maximum players cannot exceed 4")
    private int maxPlayers;
    private String name;

    public CreateRoomRequest(int maxPlayers,String name) {
        super(GameMessageType.CREATE_ROOM);
        this.maxPlayers = maxPlayers;
        this.name = name;

    }
}
