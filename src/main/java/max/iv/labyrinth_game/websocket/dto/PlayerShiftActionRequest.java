package max.iv.labyrinth_game.websocket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import max.iv.labyrinth_game.model.game.enums.Direction;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerShiftActionRequest extends BaseMessage {
    @NotBlank(message = "Room ID cannot be blank")
    private String roomId;

    @Min(value = 1, message = "Shift index must be positive and odd")
    @Max(value = 5, message = "Shift index out of bounds for a 7x7 board")
    private int shiftIndex;

    @NotNull(message = "Shift direction cannot be null")
    private Direction shiftDirection;

    public PlayerShiftActionRequest(String roomId, int shiftIndex, Direction shiftDirection) {
        super(GameMessageType.PLAYER_ACTION_SHIFT);
        this.roomId = roomId;
        this.shiftIndex = shiftIndex;
        this.shiftDirection = shiftDirection;
    }
}
