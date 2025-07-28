package max.iv.labyrinth_game.model.game;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import max.iv.labyrinth_game.model.game.enums.PlayerAvatar;
import max.iv.labyrinth_game.model.game.enums.PlayerStatus;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Player {
    private UUID id;
    private String name;
    private PlayerAvatar avatar;
    private int currentX;
    private int currentY;
    private Set<Integer> collectedMarkerIds = new HashSet<>();; // ID собранных маркеров
    private Set<Integer> targetMarkerIds = new HashSet<>();; // ID маркеров, которые нужно собрать этому игроку

    // Базовые координаты (где игрок начинает)
    private  Base base;
    private PlayerStatus status = PlayerStatus.CONNECTED;


    public Player(UUID id, String name, PlayerAvatar avatar, Base base) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.base = base;
        this.currentX = base.x();
        this.currentY = base.y();
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

    public Integer getCurrentTargetMarkerId() {
        if (this.targetMarkerIds == null || this.targetMarkerIds.isEmpty()) {
            return null;
        }

        // Возвращаем первый ID из сета целей, который еще не в сете собранных.
        // stream().findFirst() вернет первый попавшийся, порядок не гарантирован для HashSet,
        // но для нашей задачи это не критично.
        return this.targetMarkerIds.stream()
                .filter(targetId -> !this.collectedMarkerIds.contains(targetId))
                .findFirst()
                .orElse(null); // Возвращаем null, если все цели уже собраны
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Проверяем, что объект не null и того же класса
        if (o == null || getClass() != o.getClass()) return false;
        // Приводим объект к нашему типу
        Player player = (Player) o;
        // Сравниваем игроков ТОЛЬКО по их уникальному ID
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        // Хэш-код должен вычисляться на основе тех же полей, что и в equals
        return Objects.hash(id);
    }
}
