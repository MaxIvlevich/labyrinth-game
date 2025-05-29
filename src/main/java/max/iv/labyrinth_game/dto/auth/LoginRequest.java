package max.iv.labyrinth_game.dto.auth;

public record LoginRequest(
        String usernameOrEmail,
        String password
) {
}
