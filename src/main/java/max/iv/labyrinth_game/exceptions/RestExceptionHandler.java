package max.iv.labyrinth_game.exceptions;

import max.iv.labyrinth_game.exceptions.auth.TokenRefreshException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(value = TokenRefreshException.class)
    public ResponseEntity<Object> handleTokenRefreshException(TokenRefreshException ex) {

        Map<String, String> body = Map.of(
                "status", "error",
                "message", ex.getMessage()
        );

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    /**
     * Обработчик для IllegalArgumentException (.
     * Возвращает статус 400 Bad Request.
     */
    @ExceptionHandler(value = IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> body = Map.of(
                "status", "error",
                "message", ex.getMessage()
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }
}
