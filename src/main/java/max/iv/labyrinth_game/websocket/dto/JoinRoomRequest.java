package max.iv.labyrinth_game.websocket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class JoinRoomRequest extends BaseMessage{
    @NotBlank(message = "Room ID cannot be blank")
    @Size(min = 1, max = 50)
    private String roomId;

    public JoinRoomRequest(String roomId) {
        super(GameMessageType.JOIN_ROOM);
        this.roomId = roomId;
    }
}
