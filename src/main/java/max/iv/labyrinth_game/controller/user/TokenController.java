package max.iv.labyrinth_game.controller.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import max.iv.labyrinth_game.dto.auth.JwtResponse;
import max.iv.labyrinth_game.dto.auth.MessageResponse;
import max.iv.labyrinth_game.dto.auth.TokenRefreshRequest;
import max.iv.labyrinth_game.security.jwt.JwtUtils;
import max.iv.labyrinth_game.service.auth.AuthService;
import max.iv.labyrinth_game.service.auth.RefreshTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {
    private final AuthService authService;

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        JwtResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logoutUser(@Valid @RequestBody TokenRefreshRequest request) {
        authService.logoutUser(request);
        return ResponseEntity.ok(new MessageResponse("Log out successful!"));
    }
}
