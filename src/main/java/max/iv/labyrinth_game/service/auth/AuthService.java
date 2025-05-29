package max.iv.labyrinth_game.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.auth.JwtAuthenticationDTO;
import max.iv.labyrinth_game.dto.auth.JwtResponse;
import max.iv.labyrinth_game.dto.auth.LoginRequest;
import max.iv.labyrinth_game.dto.auth.SignupRequest;
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
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public JwtResponse loginUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.usernameOrEmail(),
                        loginRequest.password()
                ));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User userPrincipal = (User) authentication.getPrincipal();
        String emailForToken = userPrincipal.getEmail();
        JwtAuthenticationDTO jwtAuthDto = jwtUtils.generateAuthToken(emailForToken);

        List<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        return new JwtResponse(
                jwtAuthDto,
                userPrincipal.getId(),
                userPrincipal.getUsername(),
                roles
        );
    }

    @Transactional
    public User registerUser(SignupRequest signupRequest) {
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
        return userRepository.save(user);
    }
}
