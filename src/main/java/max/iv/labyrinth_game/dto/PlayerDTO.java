package max.iv.labyrinth_game.dto;

import java.util.Set;
import java.util.UUID;

public record PlayerDTO(
        UUID id,
        String name,
        String color,
        int currentX,
        int currentY,
        int baseX,
        int baseY,
        Set<Integer> collectedMarkerIds,
        Set<Integer> targetMarkerIds
) {
}
