package max.iv.labyrinth_game.dto.geme;

import java.util.List;

public record BoardDTO (
        int size,
        List<List<CellDTO>> grid,
        TileDTO extraTile
) {
}
