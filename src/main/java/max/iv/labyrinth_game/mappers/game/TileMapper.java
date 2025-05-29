package max.iv.labyrinth_game.mappers.game;

import max.iv.labyrinth_game.dto.geme.MarkerDTO;
import max.iv.labyrinth_game.dto.geme.TileDTO;
import max.iv.labyrinth_game.model.game.Tile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TileMapper {

    private final MarkerMapper markerMapper;

    @Autowired
    public TileMapper(MarkerMapper markerMapper) {
        this.markerMapper = markerMapper;
    }

    public TileDTO toDto(Tile tile) {
        if (tile == null) {
            return null;
        }
        MarkerDTO markerDto = markerMapper.toDto(tile.getMarker());
        return new TileDTO(
                tile.getType(),
                tile.getOrientation(),
                markerDto // markerDto будет null, если tile.getMarker() был null
        );
    }
}
