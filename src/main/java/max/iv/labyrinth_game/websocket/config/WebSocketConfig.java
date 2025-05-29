package max.iv.labyrinth_game.websocket.config;

import max.iv.labyrinth_game.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final GameWebSocketHandler gameWebSocketHandler;
    private final JwtAuthHandshakeInterceptor jwtAuthHandshakeInterceptor;

    @Autowired
    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler, JwtAuthHandshakeInterceptor jwtAuthHandshakeInterceptor) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.jwtAuthHandshakeInterceptor = jwtAuthHandshakeInterceptor;
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/game")
                .addInterceptors(jwtAuthHandshakeInterceptor)
                .setAllowedOrigins("*");

    }
}
