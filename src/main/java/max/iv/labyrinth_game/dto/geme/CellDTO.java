package max.iv.labyrinth_game.dto.geme;

public record CellDTO(
        int x,
        int y,
        boolean stationary,
        TileDTO tile,
        MarkerDTO marker) {
}
