package max.iv.labyrinth_game.websocket.messageHandlers;

import max.iv.labyrinth_game.websocket.dto.BaseMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
@Component
public class CreateRoomMessageHandler implements WebSocketMessageHandler{

    private final SessionManager sessionManager;
    private final GameStateBroadcaster gameStateBroadcaster;

    public CreateRoomMessageHandler(SessionManager sessionManager, GameStateBroadcaster gameStateBroadcaster) {
        this.sessionManager = sessionManager;
        this.gameStateBroadcaster = gameStateBroadcaster;
    }

    @Override
    public boolean supports(BaseMessage message) {
        return false;
    }

    @Override
    public void handle(WebSocketSession session, BaseMessage message) throws Exception {


    }
}
