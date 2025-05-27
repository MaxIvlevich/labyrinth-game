package max.iv.labyrinth_game.dto;

import java.util.Set;
import java.util.UUID;

public record PlayerDTO(
        UUID id,
        String name,
        String avatarType,
        //String avatarDisplayName,
        //String avatarImageName,
        //String avatarColorHex,
        int currentX,
        int currentY,
        int baseX,
        int baseY,
        Set<Integer> collectedMarkerIds,
        Set<Integer> targetMarkerIds
) {
}
