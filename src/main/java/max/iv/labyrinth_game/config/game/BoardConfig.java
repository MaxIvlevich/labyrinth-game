package max.iv.labyrinth_game.config.game;

import java.util.List;

public record BoardConfig(
        int boardSize,
        List<BaseConfig> bases,
        List<StationaryTileConfig> stationaryTiles
) {}
