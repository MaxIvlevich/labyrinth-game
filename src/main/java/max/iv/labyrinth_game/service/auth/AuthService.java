package max.iv.labyrinth_game.service.auth;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.auth.JwtResponse;
import max.iv.labyrinth_game.dto.auth.LoginRequest;
import max.iv.labyrinth_game.dto.auth.SignupRequest;
import max.iv.labyrinth_game.dto.auth.TokenRefreshRequest;
import max.iv.labyrinth_game.exceptions.auth.TokenRefreshException;
import max.iv.labyrinth_game.model.user.RefreshToken;
import max.iv.labyrinth_game.model.user.User;
import max.iv.labyrinth_game.repository.user.UserRepository;
import max.iv.labyrinth_game.security.jwt.JwtUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AuthenticationManager authenticationManager, JwtUtils jwtUtils, UserRepository userRepository, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public JwtResponse loginUser(LoginRequest loginRequest) {
        log.info("AuthService: Attempting to authenticate user: {}", loginRequest.usernameOrEmail());
        log.info("AuthService: Password present: {}", loginRequest.password() != null && !loginRequest.password().isBlank());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.usernameOrEmail(),
                        loginRequest.password()
                ));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User userPrincipal = (User) authentication.getPrincipal();
        String accessToken = jwtUtils.generateJwtToken(userPrincipal.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userPrincipal.getId());

        List<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        return new JwtResponse(
                accessToken,
                refreshToken.getToken(),
                userPrincipal.getId(),
                userPrincipal.getUsername(),
                roles
        );
    }

    @Transactional
    public void registerUser(SignupRequest signupRequest) {
        if (userRepository.existsByUsername(signupRequest.username())) {
            throw new IllegalArgumentException("Error: Username is already taken!");
        }

        if (userRepository.existsByEmail(signupRequest.email())) {
            throw new IllegalArgumentException("Error: Email is already in use!");
        }

        User user = new User(
                signupRequest.username(),
                passwordEncoder.encode(signupRequest.password()),
                signupRequest.email()
        );
       userRepository.save(user);
    }
    public JwtResponse refreshToken(TokenRefreshRequest request) {
        // 1. Находим токен в БД и проверяем его валидность (срок годности)
        String requestRefreshToken = request.refreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtUtils.generateJwtToken(user.getEmail());
                    return new JwtResponse(token, requestRefreshToken, user.getId(), user.getUsername(), user.getRoles().stream().toList());
                })
                .orElseThrow(() -> new TokenRefreshException(requestRefreshToken, "Refresh token is not in database!"));
    }

    public void logoutUser(TokenRefreshRequest request) {
        // Просто делегируем вызов сервису рефреш-токенов
        refreshTokenService.deleteByToken(request.refreshToken());
    }
}
