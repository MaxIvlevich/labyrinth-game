package max.iv.labyrinth_game.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Board {
    public static final int SIZE = 7;
    private final Cell[][] grid;
    @Setter private Tile extraTile; // Тайл, который сейчас "в руках" у игрока
    private List<Marker> allMarkersInGame; // Все маркеры, которые были сгенерированы для игры

    public Board() {
        this.grid = new Cell[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean isStationary = (x % 2 == 0 && y % 2 == 0);
                grid[y][x] = new Cell(x, y, isStationary);
            }
        }
        this.allMarkersInGame = new ArrayList<>();
    }

    public Cell getCell(int x, int y) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) {
            return null; // За пределами поля
        }
        return grid[y][x];
    }

    // Метод для проверки, находится ли ячейка в пределах поля
    public boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }
}
