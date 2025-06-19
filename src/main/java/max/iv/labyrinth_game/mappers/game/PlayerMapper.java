package max.iv.labyrinth_game.mappers.game;

import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.geme.PlayerDTO;
import max.iv.labyrinth_game.model.game.Player;
import max.iv.labyrinth_game.model.game.enums.PlayerStatus;
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

        String avatarTypeName = null;
        if (player.getAvatar() != null) {
            avatarTypeName = player.getAvatar().name();
        }

        PlayerStatus status = player.getStatus();
        return new PlayerDTO(
                player.getId(),
                player.getName(),
                avatarTypeName,
                player.getCurrentX(),
                player.getCurrentY(),
                baseX,
                baseY,
                collectedMarkers,
                targetMarkers,
                status
        );
    }
}
