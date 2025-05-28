package max.iv.labyrinth_game.user.security.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class JwtAuthenticationDTO {
    private String accessToken;  // Переименовал token в accessToken для ясности
    private String refreshToken;
    private String tokenType = "Bearer"; // Обычно добавляют тип токена

    public JwtAuthenticationDTO(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }


}
