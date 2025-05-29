package max.iv.labyrinth_game.controller.user;


import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.auth.JwtResponse;
import max.iv.labyrinth_game.dto.auth.LoginRequest;
import max.iv.labyrinth_game.dto.auth.MessageResponse;
import max.iv.labyrinth_game.dto.auth.SignupRequest;
import max.iv.labyrinth_game.service.auth.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            JwtResponse jwtResponse = authService.loginUser(loginRequest);
            return ResponseEntity.ok(jwtResponse);
        } catch (Exception e) {
            log.warn("Login failed for user {}: {}", loginRequest.usernameOrEmail(), e.getMessage());
            return ResponseEntity.status(401).body(new MessageResponse("Error: Invalid credentials."));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        try {
            authService.registerUser(signUpRequest);
            return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during user registration: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(new MessageResponse("Error: Could not register user due to an internal error."));
        }
    }
}
