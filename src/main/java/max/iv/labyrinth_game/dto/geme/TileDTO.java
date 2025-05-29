package max.iv.labyrinth_game.dto.geme;

import max.iv.labyrinth_game.model.game.enums.TileType;

public record TileDTO(
        TileType type,
        int orientation,
        MarkerDTO marker)
{}
