package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ErrorMessageResponse extends BaseMessage {

    private String message;
    public ErrorMessageResponse(String message) {
        super(GameMessageType.ERROR_MESSAGE);
        this.message = message;
    }
}
