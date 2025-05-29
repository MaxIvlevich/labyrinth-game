package max.iv.labyrinth_game.service.game;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.game.Board;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.model.game.Tile;
import max.iv.labyrinth_game.model.game.enums.Direction;
import org.springframework.stereotype.Service;

import java.util.List;
@Slf4j
@Service
public class BoardShiftService {

    public void shiftBoard(Board board, int shiftIndex, Direction direction, List<Player> playersInGame) {
        if (shiftIndex % 2 == 0 || shiftIndex < 0 || shiftIndex >= board.getSize()) {
            log.warn("Attempted to shift invalid row/column index: {}. Index must be odd and within board bounds.", shiftIndex);
            throw new IllegalArgumentException("Cannot shift stationary row or column (index must be odd).");
        }
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            shiftColumn(board, shiftIndex, direction,playersInGame);
        } else if (direction == Direction.EAST || direction == Direction.WEST) {
            shiftRow(board, shiftIndex, direction,playersInGame);
        } else {
            log.error("Invalid shift direction provided: {}", direction);
            throw new IllegalArgumentException("Invalid shift direction: " + direction);
        }
    }

    private void shiftRow(Board board, int rowIndex, Direction direction,List<Player> playersInGame) {
        log.debug("Shifting row {} {} with players.", rowIndex, direction);
        Tile[] row = getLineTiles(board, true, rowIndex);
        int boardSize = board.getSize();
        Tile tempExtra = board.getExtraTile();
        Tile outgoing;
        if (direction == Direction.EAST) {
           outgoing = row[board.getSize() - 1];
            updatePlayerPositionsForRowShift(rowIndex, direction, boardSize, playersInGame, boardSize - 1, 0);
            for (int x = board.getSize() - 1; x > 0; x--) {
                board.getCell(x, rowIndex).setTile(row[x - 1]);
            }
            board.getCell(0, rowIndex).setTile(tempExtra);
        } else if (direction == Direction.WEST) {
            outgoing = row[0];
            updatePlayerPositionsForRowShift(rowIndex, direction, boardSize, playersInGame, 0, boardSize - 1);
            for (int x = 0; x < board.getSize() - 1; x++) {
                board.getCell(x, rowIndex).setTile(row[x + 1]);
            }
            board.getCell(board.getSize() - 1, rowIndex).setTile(tempExtra);
        } else {
            throw new IllegalArgumentException("Invalid row shift direction: " + direction);
        }

        board.setExtraTile(outgoing);
    }

    private void shiftColumn(Board board, int columnIndex, Direction direction,List<Player> playersInGame) {
        log.debug("Shifting column {} {}", columnIndex, direction);
        int boardSize = board.getSize();
        Tile[] column = getLineTiles(board, false, columnIndex);
        Tile tempExtra = board.getExtraTile();
        Tile outgoing;

        if (direction == Direction.SOUTH) {
            outgoing = column[board.getSize() - 1];
            updatePlayerPositionsForColumnShift(columnIndex, direction, boardSize, playersInGame, boardSize - 1, 0);
            for (int y = board.getSize() - 1; y > 0; y--) {
                board.getCell(columnIndex, y).setTile(column[y - 1]);
            }
            board.getCell(columnIndex, 0).setTile(tempExtra);
        } else if (direction == Direction.NORTH) {
            outgoing = column[0];
            updatePlayerPositionsForColumnShift(columnIndex, direction, boardSize, playersInGame, 0, boardSize - 1);
            for (int y = 0; y < board.getSize() - 1; y++) {
                board.getCell(columnIndex, y).setTile(column[y + 1]);
            }
            board.getCell(columnIndex, board.getSize() - 1).setTile(tempExtra);
        } else {
            throw new IllegalArgumentException("Invalid column shift direction: " + direction);
        }

        board.setExtraTile(outgoing);
    }
    // --- Логика обновления позиций игроков (новые приватные методы) ---

    private void updatePlayerPositionsForRowShift(int rowIndex, Direction direction, int boardSize, List<Player> playersInGame, int shiftedOutX, int insertedAtX) {
        for (Player player : playersInGame) {
            if (player.getCurrentY() == rowIndex) { // Игрок находится в сдвигаемом ряду
                if (player.getCurrentX() == shiftedOutX) { // Игрок на "выпадающем" тайле
                    player.moveTo(insertedAtX, rowIndex);
                    log.info("Player {} (ID: {}) wrapped in row {} from x={} to x={}",
                            player.getName(), player.getId(), rowIndex, shiftedOutX, insertedAtX);
                } else { // Игрок сдвигается вместе с рядом
                    if (direction == Direction.EAST) { // Сдвиг вправо
                        player.moveTo(player.getCurrentX() + 1, rowIndex);
                    } else { // Сдвиг влево (direction == Direction.WEST)
                        player.moveTo(player.getCurrentX() - 1, rowIndex);
                    }
                    log.trace("Player {} (ID: {}) moved with row {} to ({},{})",
                            player.getName(), player.getId(), rowIndex, player.getCurrentX(), player.getCurrentY());
                }
            }
        }
    }

    private void updatePlayerPositionsForColumnShift(int columnIndex, Direction direction, int boardSize, List<Player> playersInGame, int shiftedOutY, int insertedAtY) {
        for (Player player : playersInGame) {
            if (player.getCurrentX() == columnIndex) { // Игрок находится в сдвигаемой колонке
                if (player.getCurrentY() == shiftedOutY) { // Игрок на "выпадающем" тайле
                    player.moveTo(columnIndex, insertedAtY);
                    log.info("Player {} (ID: {}) wrapped in column {} from y={} to y={}",
                            player.getName(), player.getId(), columnIndex, shiftedOutY, insertedAtY);
                } else { // Игрок сдвигается вместе с колонкой
                    if (direction == Direction.SOUTH) { // Сдвиг вниз
                        player.moveTo(columnIndex, player.getCurrentY() + 1);
                    } else { // Сдвиг вверх (direction == Direction.NORTH)
                        player.moveTo(columnIndex, player.getCurrentY() - 1);
                    }
                    log.trace("Player {} (ID: {}) moved with column {} to ({},{})",
                            player.getName(), player.getId(), columnIndex, player.getCurrentX(), player.getCurrentY());
                }
            }
        }
    }
    private Tile[] getLineTiles(Board board, boolean isRow, int index) {
        int size = board.getSize();
        Tile[] tiles = new Tile[size];
        for (int i = 0; i < size; i++) {
            tiles[i] = isRow ? board.getCell(i, index).getTile() : board.getCell(index, i).getTile();
        }
        return tiles;
    }


}
