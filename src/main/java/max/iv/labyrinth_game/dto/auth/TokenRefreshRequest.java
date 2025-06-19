package max.iv.labyrinth_game.dto.auth;

import jakarta.validation.constraints.NotBlank;


public record TokenRefreshRequest(
        @NotBlank
        String refreshToken
) {
}
