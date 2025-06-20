package max.iv.labyrinth_game.exceptions.game;

import max.iv.labyrinth_game.exceptions.ErrorType;

public class GameLogicException extends RuntimeException{

    private final ErrorType errorType;

    public GameLogicException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
