package max.iv.labyrinth_game.dto.auth;

import java.util.List;
import java.util.UUID;

public record JwtResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UUID userId,
        String username,
        List<String> roles
) {
    public JwtResponse(String accessToken,String refreshToken, UUID userId, String username, List<String> roles) {
        this(
                accessToken,
                refreshToken,
                "Bearer",
                userId,
                username,
                roles
        );
    }
}
