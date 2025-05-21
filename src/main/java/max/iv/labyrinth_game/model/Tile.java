package max.iv.labyrinth_game.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import max.iv.labyrinth_game.model.enums.Direction;
import max.iv.labyrinth_game.model.enums.TileType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class Tile {
    private TileType type;
    private int orientation;
    private Marker marker;
    public Tile(TileType type, int orientation, Marker marker) {
        this.type = type;
        this.orientation = (type == TileType.STRAIGHT) ? orientation % 2 : orientation % 4; // Для прямого только 2 ориентации
        this.marker = marker;
    }
    public Tile(TileType type, int orientation) { // Конструктор без маркера
        this(type, orientation, null);
    }


    // Метод для получения открытых сторон в зависимости от типа и ориентации
    // Возвращает Set<Direction> для удобства проверки
    public Set<Direction> getOpenSides() {
        Set<Direction> open = new HashSet<>();
        switch (type) {
            case STRAIGHT -> {
                if (orientation == 0) { // Вертикальный
                    open.add(Direction.NORTH);
                    open.add(Direction.SOUTH);
                } else { // Горизонтальный (orientation == 1)
                    open.add(Direction.EAST);
                    open.add(Direction.WEST);
                }
            }
            case CORNER -> {
                // 0: N-E, 1: E-S, 2: S-W, 3: W-N
                if (orientation == 0) {
                    open.add(Direction.NORTH);
                    open.add(Direction.EAST);
                } else if (orientation == 1) {
                    open.add(Direction.EAST);
                    open.add(Direction.SOUTH);
                } else if (orientation == 2) {
                    open.add(Direction.SOUTH);
                    open.add(Direction.WEST);
                } else {
                    open.add(Direction.WEST);
                    open.add(Direction.NORTH);
                } // orientation == 3
            }
            case T_SHAPED -> {
                // 0: N-E-W (нет S), 1: N-E-S (нет W), 2: E-S-W (нет N), 3: N-S-W (нет E)
                if (orientation == 0) {
                    open.addAll(Arrays.asList(Direction.NORTH, Direction.EAST, Direction.WEST));
                } else if (orientation == 1) {
                    open.addAll(Arrays.asList(Direction.NORTH, Direction.EAST, Direction.SOUTH));
                } else if (orientation == 2) {
                    open.addAll(Arrays.asList(Direction.EAST, Direction.SOUTH, Direction.WEST));
                } else {
                    open.addAll(Arrays.asList(Direction.NORTH, Direction.SOUTH, Direction.WEST));
                } // orientation == 3
            }
        }
        return open;
    }

    // Вспомогательный метод для проверки, соединяется ли этот тайл с соседним в заданном направлении
    public boolean connectsTo(Direction direction) {
        return getOpenSides().contains(direction);
    }
}
