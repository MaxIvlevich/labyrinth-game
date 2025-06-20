package max.iv.labyrinth_game.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import max.iv.labyrinth_game.exceptions.auth.ErrorType;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ErrorMessageResponse extends BaseMessage {

    private  String message;
    private  ErrorType errorType;
    public ErrorMessageResponse(String message) {
        super(GameMessageType.ERROR_MESSAGE);
        this.message = message;
        this.errorType = ErrorType.UNKNOWN_ERROR;
    }

    public ErrorMessageResponse(String message, ErrorType errorType) {
        super(GameMessageType.ERROR_MESSAGE);
        this.message = message;
        this.errorType = errorType;
    }
}
