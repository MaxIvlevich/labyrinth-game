package max.iv.labyrinth_game.websocket.mapper;

import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.websocket.dto.RoomSummaryDTO;
import org.springframework.stereotype.Component;

@Component
public class Mapper {

    private RoomSummaryDTO mapToRoomSummaryDTO(GameRoom room) {
        return new RoomSummaryDTO(
                room.getRoomId(),
                room.getRoomName(),
                room.getPlayers().size(),
                room.getMaxPlayers(),
                room.getGamePhase()
        );
    }
}
