package max.iv.labyrinth_game.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username or email cannot be blank")
        String usernameOrEmail,

        @NotBlank(message = "Password cannot be blank")
        String password
) {
}
