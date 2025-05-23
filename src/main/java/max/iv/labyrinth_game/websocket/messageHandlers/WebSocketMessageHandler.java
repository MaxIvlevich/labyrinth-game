package max.iv.labyrinth_game.websocket.messageHandlers;

import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import org.springframework.web.socket.WebSocketSession;

public interface WebSocketMessageHandler {
    boolean supports(BaseMessage message);
    void handle(WebSocketSession session, BaseMessage message) throws Exception;
}
