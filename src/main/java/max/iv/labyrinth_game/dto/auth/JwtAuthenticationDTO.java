package max.iv.labyrinth_game.dto.auth;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class JwtAuthenticationDTO {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";

    public JwtAuthenticationDTO(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
