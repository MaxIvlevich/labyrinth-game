package max.iv.labyrinth_game.mappers;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.PlayerDTO;
import max.iv.labyrinth_game.model.Player;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
@Slf4j
@Component
public class PlayerMapper {
    public PlayerDTO toDto(Player player) {
        if (player == null) {
            return null;
        }
        int baseX = 0;
        int baseY = 0;
        if (player.getBase() != null) {
            baseX = player.getBase().x();
            baseY = player.getBase().y();
        } else {

            log.warn("Player {} (ID: {}) has a null base object during DTO mapping.", player.getName(), player.getId());
        }

        // Копируем сеты, чтобы DTO был независимым и для предотвращения NPE, если сеты в модели null
        Set<Integer> collectedMarkers = (player.getCollectedMarkerIds() != null) ?
                new HashSet<>(player.getCollectedMarkerIds()) :
                Collections.emptySet();

        Set<Integer> targetMarkers = (player.getTargetMarkerIds() != null) ?
                new HashSet<>(player.getTargetMarkerIds()) :
                Collections.emptySet();

        return new PlayerDTO(
                player.getId(),
                player.getName(),
                player.getColor(),
                player.getCurrentX(),
                player.getCurrentY(),
                baseX,
                baseY,
                collectedMarkers,
                targetMarkers
        );
    }
}
