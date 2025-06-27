package max.iv.labyrinth_game.mappers.game;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.geme.BoardDTO;
import max.iv.labyrinth_game.dto.geme.PlayerDTO;
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
    public GameStateUpdateDTO toDto(GameRoom room) {
        if (room == null) {
            // Клиент должен быть готов обработать такой случай.
            return new GameStateUpdateDTO(
                    GameMessageType.GAME_STATE_UPDATE,
                    "ROOM_NOT_FOUND_OR_NULL",
                    "ROOM_NOT_FOUND_OR_NULL",
                    GamePhase.WAITING_FOR_PLAYERS,
                    null,
                    Collections.emptyList(),
                    null,
                    null,
                    null
            );
        }
        log.info("--- GameStateMapper.toDto ---");
        log.info("Room ID: " + room.getRoomId());

        List<Player> playersInRoom = room.getPlayers();
        log.info("Количество игроков в GameRoom: " + (playersInRoom != null ? playersInRoom.size() : "null"));
        // Конвертируем список игроков
        List<PlayerDTO> playerDTOs = Collections.emptyList();

        if (room.getPlayers() != null) {
            playerDTOs = room.getPlayers().stream()
                    .map(player-> {
                        // Логируем каждого игрока ПЕРЕД маппингом
                        log.info("  Маппим игрока: " + player.getName() + " (ID: " + player.getId() + ")");
                        PlayerDTO dto = playerMapper.toDto(player);
                        // Логируем результат маппинга
                        log.info("  Результат DTO: " + (dto != null ? dto.name() : "null"));
                        return dto;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }


        log.info("Итоговое количество PlayerDTO: " + playerDTOs.size());
        log.info("-----------------------------");
        // Конвертируем доску
        BoardDTO boardDTO = boardMapper.toDto(room.getBoard());

        // Определяем ID текущего игрока
        UUID currentPlayerId = null;
        if (room.getCurrentPlayer() != null) {
            currentPlayerId = room.getCurrentPlayer().getId();
        }

        UUID winnerId = null;
        String winnerName = null;
        if (room.getGamePhase() == GamePhase.GAME_OVER && room.getWinner() != null) {
            winnerId = room.getWinner().getId(); // Предполагаем, что Player.getId() возвращает UUID
            winnerName = room.getWinner().getName();
            currentPlayerId = null; // В конце игры нет "текущего" игрока для хода
        }

        return new GameStateUpdateDTO(
                GameMessageType.GAME_STATE_UPDATE, // Устанавливаем тип сообщения
                room.getRoomId(),
                room.getRoomName(),
                room.getGamePhase(),
                currentPlayerId,
                playerDTOs,
                boardDTO,
                winnerId,
                winnerName
        );
    }
}
