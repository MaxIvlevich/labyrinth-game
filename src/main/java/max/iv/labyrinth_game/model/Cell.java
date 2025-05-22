package max.iv.labyrinth_game.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import max.iv.labyrinth_game.model.enums.Direction;

import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@AllArgsConstructor
public class Cell {
    private final int x;
    private final int y;
    private final boolean isStationary;
    private Tile tile;
    private Marker stationaryMarker;
    private Set<Direction> fixedOpenSides = new HashSet<>();

    public Cell(int x, int y, boolean isStationary) {
        this.x = x;
        this.y = y;
        this.isStationary = isStationary;
    }


    public Set<Direction> getOpenSides() {
        return tile != null ? tile.getOpenSides() : fixedOpenSides;
    }

    public boolean connectsTo(Direction direction) {
        return getOpenSides().contains(direction);
    }

    public Marker getActiveMarker() {
        return tile != null ? tile.getMarker() : stationaryMarker;
    }

    public void removeActiveMarker() {
        if (tile != null) tile.setMarker(null);
        else stationaryMarker = null;
    }
}
