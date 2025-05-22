package max.iv.labyrinth_game.model;

import lombok.Getter;
import lombok.Setter;
import max.iv.labyrinth_game.model.enums.Direction;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class Player {
    private UUID id; // WebSocket session ID
    private String name;
    private String color; // Для отображения на фронтенде
    private int currentX;
    private int currentY;
    private Set<Integer> collectedMarkerIds; // ID собранных маркеров
    private Set<Integer> targetMarkerIds; // ID маркеров, которые нужно собрать этому игроку

    // Базовые координаты (где игрок начинает)
    private  Base base;


    public Player(UUID id, String name, String color, Base base) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.base = base;
        this.currentX = base.x();
        this.currentY = base.y();

    }

    public void collectMarker(Marker marker) {
        if (marker != null && targetMarkerIds.contains(marker.getId())) {
            collectedMarkerIds.add(marker.getId());
        }
    }

    public boolean hasCollectedAllTargetMarkers() {
        return collectedMarkerIds.containsAll(targetMarkerIds);
    }

    public void resetToMyBase() {
        this.currentX = this.base.x();
        this.currentY = this.base.y();
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
