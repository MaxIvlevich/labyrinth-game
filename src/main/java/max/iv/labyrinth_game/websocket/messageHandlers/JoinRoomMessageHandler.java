package max.iv.labyrinth_game.websocket.messageHandlers;

import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import org.springframework.web.socket.WebSocketSession;

public class JoinRoomMessageHandler implements WebSocketMessageHandler{
    @Override
    public boolean supports(BaseMessage message) {
        return false;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {

    }
}
