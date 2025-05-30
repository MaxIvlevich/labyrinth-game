package max.iv.labyrinth_game.websocket.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetRoomListRequest extends BaseMessage{

    @Min(value = 0, message = "Page number cannot be negative.")
    private int pageNumber = 0; // Номер страницы (начиная с 0)

    @Min(value = 1, message = "Page size must be at least 1.")
    private int pageSize = 8;

    public GetRoomListRequest(int pageNumber, int pageSize) {
        super(GameMessageType.GET_ROOM_LIST_REQUEST);
        this.pageNumber = pageNumber;
        this.pageSize = Math.max(1, pageSize);
    }
}
