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
    public JwtResponse(JwtAuthenticationDTO jwtAuthDto, UUID userId, String username, List<String> roles) {
        this(
                jwtAuthDto.getAccessToken(),
                jwtAuthDto.getRefreshToken(),
                "Bearer",
                userId,
                username,
                roles
        );
    }
}
