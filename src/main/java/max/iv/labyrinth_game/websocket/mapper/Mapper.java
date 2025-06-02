package max.iv.labyrinth_game.websocket.mapper;

import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.websocket.dto.RoomInfoDTO;
import org.springframework.stereotype.Component;

@Component
public class Mapper {

    private RoomInfoDTO mapToRoomSummaryDTO(GameRoom room) {
        return new RoomInfoDTO(
                room.getRoomId(),
                room.getRoomName(),
                room.getPlayers().size(),
                room.getMaxPlayers(),
                room.getGamePhase()
        );
    }
}
