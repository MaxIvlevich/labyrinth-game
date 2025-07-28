package max.iv.labyrinth_game.mappers.game;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.geme.BoardDTO;
import max.iv.labyrinth_game.dto.geme.PlayerDTO;
import max.iv.labyrinth_game.dto.geme.PointDTO;
import max.iv.labyrinth_game.model.game.GameRoom;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.model.game.enums.GamePhase;
import max.iv.labyrinth_game.websocket.dto.GameMessageType;
import max.iv.labyrinth_game.websocket.dto.GameStateUpdateDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GameStateMapper {

    private final PlayerMapper playerMapper;
    private final BoardMapper boardMapper;

    @Autowired

    public GameStateMapper(PlayerMapper playerMapper, BoardMapper boardMapper) {
        this.playerMapper = playerMapper;
        this.boardMapper = boardMapper;
    }
    public GameStateUpdateDTO toDto(GameRoom room, Player targetPlayer, Set<PointDTO> reachableCellsDto) {
        if (room == null || targetPlayer == null) {
            log.warn("GameStateMapper.toDto вызван с null room или targetPlayer.");
            return createInvalidStateDto();
        }

        log.debug("--- GameStateMapper.toDto for player: {} ---", targetPlayer.getName());

        // 1. Маппинг списка всех игроков
        List<PlayerDTO> playerDTOs = (room.getPlayers() != null)
                ? room.getPlayers().stream()
                .map(playerMapper::toDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
                : Collections.emptyList();

        // 2. Персонализированный маппинг доски (показывает маркеры только для targetPlayer)
        BoardDTO boardDTO = boardMapper.toDto(room.getBoard(), targetPlayer);

        // 3. Маппинг текущего игрока
        PlayerDTO currentPlayerDto = (room.getCurrentPlayer() != null)
                ? playerMapper.toDto(room.getCurrentPlayer())
                : null;

        // 4. Определение победителя
        UUID winnerId = null;
        String winnerName = null;
        if (room.getGamePhase() == GamePhase.GAME_OVER && room.getWinner() != null) {
            winnerId = room.getWinner().getId();
            winnerName = room.getWinner().getName();
        }

        // 5. Получение текущей цели игрока (для подсветки в UI)
        Integer currentTargetMarkerId = targetPlayer.getCurrentTargetMarkerId();

        // 6. Сборка финального DTO
        return new GameStateUpdateDTO(
                GameMessageType.GAME_STATE_UPDATE,
                room.getRoomId(),
                room.getRoomName(),
                room.getGamePhase(),
                currentPlayerDto,
                playerDTOs,
                boardDTO,
                winnerId,
                winnerName,
                reachableCellsDto,      // Передаем готовое множество
                currentTargetMarkerId   // Передаем ID цели
        );
    }

    private GameStateUpdateDTO createInvalidStateDto() {
        return new GameStateUpdateDTO(
                GameMessageType.GAME_STATE_UPDATE,
                "ROOM_NOT_FOUND",
                "Комната не найдена",
                GamePhase.WAITING_FOR_PLAYERS,
                null,
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
