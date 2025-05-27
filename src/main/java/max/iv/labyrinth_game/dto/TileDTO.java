package max.iv.labyrinth_game.dto;

import max.iv.labyrinth_game.model.enums.TileType;

public record TileDTO(
        TileType type,
        int orientation,
        MarkerDTO marker)
{}
