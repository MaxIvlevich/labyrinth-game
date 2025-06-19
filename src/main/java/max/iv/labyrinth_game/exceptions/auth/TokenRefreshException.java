package max.iv.labyrinth_game.exceptions.auth;

public class TokenRefreshException extends IllegalArgumentException{
    String token;
    String msg;

    public TokenRefreshException(String token, String msg) {
        super(String.format("Failed for token [%s]: %s", token, msg));
    }
}
