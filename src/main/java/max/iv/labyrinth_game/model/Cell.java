package max.iv.labyrinth_game.model;

import lombok.Getter;
import lombok.Setter;
import max.iv.labyrinth_game.model.enums.Direction;

import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
public class Cell {
    private final int x;
    private final int y;
    private boolean isStationary;
    private Tile tile; // null для стационарных ячеек, которые просто "пространство"
    // или для стационарных, которые имеют свою структуру (например, угловая база)
    private Marker stationaryMarker; // Маркер, если он стационарно находится в этой ячейке (не на подвижном тайле)

    // Для стационарных ячеек, которые не являются тайлами, но имеют соединения
    private Set<Direction> fixedOpenSides; // Если isStationary и tile == null, описывает выходы

    public Cell(int x, int y, boolean isStationary) {
        this.x = x;
        this.y = y;
        this.isStationary = isStationary;
        this.fixedOpenSides = new HashSet<>(); // По умолчанию закрыто
    }

    public Marker getActiveMarker() {
        if (tile != null && tile.getMarker() != null) {
            return tile.getMarker();
        }
        return stationaryMarker;
    }

    public void removeActiveMarker() {
        if (tile != null && tile.getMarker() != null) {
            tile.setMarker(null);
        } else if (stationaryMarker != null) {
            stationaryMarker = null;
        }
    }

    // Возвращает открытые стороны для этой ячейки (либо от тайла, либо фиксированные)
    public Set<Direction> getOpenSides() {
        if (tile != null) {
            return tile.getOpenSides();
        }
        if (isStationary && fixedOpenSides != null) {
            return fixedOpenSides;
        }
        return new HashSet<>(); // По умолчанию нет открытых сторон
    }
    public boolean connectsTo(Direction direction) {
        return getOpenSides().contains(direction);
    }
}
