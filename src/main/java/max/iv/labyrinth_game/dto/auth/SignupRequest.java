package max.iv.labyrinth_game.dto.auth;

import java.util.List;
import java.util.UUID;

public record SignupRequest(
        String username,
        String email,
        String password
) {

}
