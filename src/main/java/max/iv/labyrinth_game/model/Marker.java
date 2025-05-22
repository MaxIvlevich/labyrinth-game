package max.iv.labyrinth_game.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Marker {
    private int id;
    private UUID playerId;

    public Marker(int id, UUID playerId) {
        this.id = id;
        this.playerId = playerId;
    }

    public Marker(int i) {

    }
}
