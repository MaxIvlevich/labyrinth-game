package max.iv.labyrinth_game.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import max.iv.labyrinth_game.model.enums.PlayerAvatar;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Player {
    private UUID id; // WebSocket session ID
    private String name;
    private PlayerAvatar avatar;
    private int currentX;
    private int currentY;
    private Set<Integer> collectedMarkerIds; // ID собранных маркеров
    private Set<Integer> targetMarkerIds; // ID маркеров, которые нужно собрать этому игроку

    // Базовые координаты (где игрок начинает)
    private  Base base;


    public Player(UUID id, String name, PlayerAvatar avatar, Base base) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.base = base;
        this.currentX = base.x();
        this.currentY = base.y();
        this.collectedMarkerIds = new HashSet<>();
        this.targetMarkerIds = new HashSet<>();

    }

    public Player(UUID id, String name, Base base) {
        this.id = id;
        this.name = name;
        this.base = base;
    }

    public void collectMarker(Marker marker) {
        if (marker != null && targetMarkerIds != null && targetMarkerIds.contains(marker.getId())) {
            if (collectedMarkerIds == null) { // Дополнительная защита, если сет не был инициализирован
                collectedMarkerIds = new HashSet<>();
            }
            collectedMarkerIds.add(marker.getId());
        }
    }

    public boolean hasCollectedAllTargetMarkers() {
        if (targetMarkerIds == null || targetMarkerIds.isEmpty()) {
            return false;
        }
        if (collectedMarkerIds == null) {
            return false;
        }
        return collectedMarkerIds.containsAll(targetMarkerIds);
    }

    public void resetToMyBase() {
        if (this.base != null) {
            this.currentX = this.base.x();
            this.currentY = this.base.y();
        }
    }

    public boolean isBase(int x, int y) {
        return this.base.x() == x && this.base.y() == y;
    }
    public boolean isAtBase() {
        return this.currentX == base.x() && this.currentY == base.y();
    }
    public void updateBase(Base newBase) {
        this.base = newBase;
    }
    public boolean isReadyToWin() {
        return hasCollectedAllTargetMarkers() && isAtBase();
    }

    public void moveTo(int moveToX, int moveToY) {
        this.currentX = moveToX;
        this.currentY = moveToY;
    }
}
