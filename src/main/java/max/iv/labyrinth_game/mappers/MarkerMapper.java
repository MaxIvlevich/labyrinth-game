package max.iv.labyrinth_game.mappers;

import max.iv.labyrinth_game.dto.MarkerDTO;
import max.iv.labyrinth_game.model.Marker;
import org.springframework.stereotype.Component;

@Component
public class MarkerMapper {
    public MarkerDTO toDto(Marker marker) {
        if (marker == null) {
            return null;
        }
        return new MarkerDTO(marker.getId(),marker.getPlayerId());
    }
}
