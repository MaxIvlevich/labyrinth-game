package max.iv.labyrinth_game.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import max.iv.labyrinth_game.model.enums.Direction;

import java.util.Collections;
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
        if (tile != null) {
            return tile.getOpenSides();
        }
        return fixedOpenSides != null ? fixedOpenSides : Collections.emptySet();
    }

    public boolean connectsTo(Direction direction) {
        return getOpenSides().contains(direction);
    }

    public Marker getActiveMarker() {
        if (this.tile != null && this.tile.getMarker() != null) {
            return this.tile.getMarker();
        }
        return this.stationaryMarker;
    }

    public void removeActiveMarker() {
        if (this.tile != null && this.tile.getMarker() != null) {
            this.tile.setMarker(null); // Предполагаем, что у Tile есть setMarker(Marker marker)
        }
        else if (this.stationaryMarker != null) {
            this.stationaryMarker = null;
        }
    }
}
