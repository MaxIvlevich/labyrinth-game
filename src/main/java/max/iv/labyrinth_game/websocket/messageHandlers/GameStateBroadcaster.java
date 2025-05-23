package max.iv.labyrinth_game.websocket.messageHandlers;

import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
@Component
public class GameStateBroadcaster implements WebSocketMessageHandler{
    @Override
    public boolean supports(BaseMessage message) {
        return false;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {

    }

    public void broadcastGameStateToRoom(String roomId) {

    }
}
