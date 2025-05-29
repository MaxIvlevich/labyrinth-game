package max.iv.labyrinth_game.websocket.config;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.user.User;
import max.iv.labyrinth_game.security.jwt.JwtUtils;
import max.iv.labyrinth_game.security.service.UserDetailsServiceImpl;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class JwtAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService; // Для загрузки деталей пользователя

    // Ключи для атрибутов сессии (лучше использовать константы из SessionManager, если они там)
    public static final String USER_ID_ATTRIBUTE_KEY = "userId";
    public static final String USER_NAME_ATTRIBUTE_KEY = "userName";

    public JwtAuthHandshakeInterceptor(JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {
        log.debug("Attempting JWT authentication for WebSocket handshake...");

        String jwt = parseJwtFromRequest(request);

        if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
            String usernameOrEmail = jwtUtils.getEmailFromToken(jwt); // Предполагаем, что токен содержит email как subject

            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(usernameOrEmail);

                if (userDetails instanceof User authenticatedUser) {

                    attributes.put(USER_ID_ATTRIBUTE_KEY, authenticatedUser.getId());
                    attributes.put(USER_NAME_ATTRIBUTE_KEY, authenticatedUser.getUsername());

                    log.info("WebSocket handshake authorized for user: {} (ID: {}). Attributes set.",
                            authenticatedUser.getUsername(), authenticatedUser.getId());
                    return true;
                } else {
                    log.warn("User details loaded for {} is not an instance of our User class. Handshake rejected.", usernameOrEmail);
                    return false;
                }
            } catch (Exception e) {
                log.error("Error loading user details during WebSocket handshake for subject {}: {}", usernameOrEmail, e.getMessage());
                return false;
            }
        } else {
            if (jwt == null) {
                log.warn("No JWT found in WebSocket handshake request. Handshake rejected.");
            } else {
                log.warn("Invalid JWT in WebSocket handshake request. Handshake rejected. Token: {}", jwt);
            }
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception == null) {
            log.debug("WebSocket handshake completed successfully.");
        } else {
            log.error("WebSocket handshake failed after processing: {}", exception.getMessage());
        }
    }

    private String parseJwtFromRequest(ServerHttpRequest request) {

        List<String> authorizationHeader = request.getHeaders().get("Authorization");
        if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
            String headerAuth = authorizationHeader.get(0);
            if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
                log.debug("Found JWT in Authorization header.");
                return headerAuth.substring(7);
            }
        }

        // Способ 2: Из query параметра (проще для стандартного JS WebSocket клиента)
        // URL будет выглядеть так: ws://localhost:8080/game?token=YOUR_TOKEN
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String tokenFromQuery = servletRequest.getServletRequest().getParameter("token");
            if (StringUtils.hasText(tokenFromQuery)) {
                log.debug("Found JWT in 'token' query parameter.");
                return tokenFromQuery;
            }
        }

        // Способ 3: Некоторые клиенты могут передавать токен через Sec-WebSocket-Protocol
        // List<String> protocolHeader = request.getHeaders().get("Sec-WebSocket-Protocol");
        // if (protocolHeader != null && !protocolHeader.isEmpty()) {
        //     // Здесь нужно будет парсить протокол, если он содержит токен, например "jwt, YOUR_TOKEN"
        //     // Это более сложный и менее стандартный способ для простой передачи токена.
        // }

        log.trace("JWT not found in common places (Authorization header or 'token' query parameter).");
        return null;
    }
}
