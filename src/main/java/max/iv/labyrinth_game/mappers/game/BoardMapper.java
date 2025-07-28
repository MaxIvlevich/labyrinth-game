package max.iv.labyrinth_game.mappers.game;

import max.iv.labyrinth_game.dto.geme.BoardDTO;
import max.iv.labyrinth_game.dto.geme.CellDTO;
import max.iv.labyrinth_game.dto.geme.TileDTO;
import max.iv.labyrinth_game.model.game.Board;
import max.iv.labyrinth_game.model.game.Player;
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
    public BoardDTO toDto(Board board, Player targetPlayer) {
        if (board == null) {
            return null;
        }
        // Конвертируем сетку ячеек
        List<List<CellDTO>> gridDto = new ArrayList<>();
        for (int y = 0; y < board.getSize(); y++) {
            List<CellDTO> rowDto = new ArrayList<>();
            for (int x = 0; x < board.getSize(); x++) {
                // Используем cellMapper для конвертации каждой ячейки
                rowDto.add(cellMapper.toDto(board.getCell(x, y),targetPlayer));
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
