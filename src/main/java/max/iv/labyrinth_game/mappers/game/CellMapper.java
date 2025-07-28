package max.iv.labyrinth_game.mappers.game;

import max.iv.labyrinth_game.dto.geme.CellDTO;
import max.iv.labyrinth_game.dto.geme.MarkerDTO;
import max.iv.labyrinth_game.dto.geme.TileDTO;
import max.iv.labyrinth_game.model.game.Cell;
import max.iv.labyrinth_game.model.game.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CellMapper {

    private final TileMapper tileMapper;
    private final MarkerMapper markerMapper;

    @Autowired
    public CellMapper(TileMapper tileMapper, MarkerMapper markerMapper) {
        this.tileMapper = tileMapper;
        this.markerMapper = markerMapper;
    }

    public CellDTO toDto(Cell cell, Player targetPlayer) {
        if (cell == null) {
            return null;
        }
        MarkerDTO markerDto = null;

        TileDTO tileDto = tileMapper.toDto(cell.getTile());

        if (cell.getActiveMarker() != null && targetPlayer.getTargetMarkerIds().contains(cell.getActiveMarker().getId())) {
            markerDto = markerMapper.toDto(cell.getActiveMarker());
        }

        return new CellDTO(
                cell.getX(),
                cell.getY(),
                cell.isStationary(),
                tileDto,
                markerDto
        );
    }
}
