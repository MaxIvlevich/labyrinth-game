package max.iv.labyrinth_game.mappers;

import max.iv.labyrinth_game.dto.BoardDTO;
import max.iv.labyrinth_game.dto.CellDTO;
import max.iv.labyrinth_game.dto.TileDTO;
import max.iv.labyrinth_game.model.Board;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BoardMapper {

    private final CellMapper cellMapper;
    private final TileMapper tileMapper;

    @Autowired
    public BoardMapper(CellMapper cellMapper, TileMapper tileMapper) {
        this.cellMapper = cellMapper;
        this.tileMapper = tileMapper;
    }
    public BoardDTO toDto(Board board) {
        if (board == null) {
            return null;
        }
        // Конвертируем сетку ячеек
        List<List<CellDTO>> gridDto = new ArrayList<>();
        for (int y = 0; y < board.getSize(); y++) {
            List<CellDTO> rowDto = new ArrayList<>();
            for (int x = 0; x < board.getSize(); x++) {
                // Используем cellMapper для конвертации каждой ячейки
                rowDto.add(cellMapper.toDto(board.getCell(x, y)));
            }
            gridDto.add(rowDto);
        }

        TileDTO extraTileDto = tileMapper.toDto(board.getExtraTile());

        return new BoardDTO(
                board.getSize(),
                gridDto,
                extraTileDto
        );
    }
}
