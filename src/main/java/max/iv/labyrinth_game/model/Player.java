package max.iv.labyrinth_game.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
@Getter
@Setter
public class Player {
    private String id; // WebSocket session ID или уникальный сгенерированный ID
    private String name;
    private String color; // Для отображения на фронтенде
    private int currentX;
    private int currentY;
    private Set<Integer> collectedMarkerIds; // ID собранных маркеров
    private Set<Integer> targetMarkerIds; // ID маркеров, которые нужно собрать этому игроку
    private boolean hasShiftedThisTurn; // Флаг, сдвигал ли игрок поле в этом ходу

    // Базовые координаты (где игрок начинает)
    private final int baseX;
    private final int baseY;


    public Player(String id, String name, String color, int baseX, int baseY) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.currentX = baseX;
        this.currentY = baseY;
        this.baseX = baseX;
        this.baseY = baseY;
        this.collectedMarkerIds = new HashSet<>();
        this.targetMarkerIds = new HashSet<>();
        this.hasShiftedThisTurn = false;
    }

    public void collectMarker(Marker marker) {
        if (marker != null && targetMarkerIds.contains(marker.getId())) {
            collectedMarkerIds.add(marker.getId());
        }
    }

    public boolean hasCollectedAllTargetMarkers() {
        return collectedMarkerIds.containsAll(targetMarkerIds);
    }
}
