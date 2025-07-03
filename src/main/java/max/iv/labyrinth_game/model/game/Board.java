package max.iv.labyrinth_game.model.game;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Board {
    private final int size;
    private final Cell[][] grid;
    @Setter private Tile extraTile;
    private List<Marker> allMarkersInGame;

    public Board(int boardSize) {
        if (boardSize <= 0 || boardSize % 2 == 0) {
            throw new IllegalArgumentException("Board size must be a positive odd number. Received: " + boardSize);
        }
        this.size = boardSize;
        this.grid = new Cell[this.size][this.size];
        for (int y = 0; y < this.size; y++) {
            for (int x = 0; x < this.size; x++) {
                // Логика определения стационарных ячеек (где x и y четные)
                boolean isStationary = (x % 2 == 0 && y % 2 == 0);
                grid[y][x] = new Cell(x, y, isStationary);
            }
        }
        this.allMarkersInGame = new ArrayList<>(); // Инициализируем
    }

    public Cell getCell(int x, int y) {
        if (x < 0 || x >= this.size || y < 0 || y >= this.size) {
            return null;
        }
        return grid[y][x];
    }

    // Метод для проверки, находится ли ячейка в пределах поля
    public boolean isValidCoordinate(int x, int y) {
        return x < 0 || x >= size || y < 0 || y >= size;
    }
    public List<Cell> getAllCells() {
        List<Cell> allCells = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                allCells.add(getCell(x, y));
            }
        }
        return allCells;
    }
}
