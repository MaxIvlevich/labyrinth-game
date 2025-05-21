package max.iv.labyrinth_game.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Marker {
    private int id;
    private String playerId;
    private String description;
}
