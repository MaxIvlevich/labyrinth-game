package max.iv.labyrinth_game.websocket.messageHandlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
@Slf4j
@Component
public class SessionManager{

    public void registerSession(WebSocketSession session) {

    }

    public void unregisterSession(WebSocketSession session) {

    }

    public void handleTransportError(WebSocketSession session, Throwable exception) {
    }
}
