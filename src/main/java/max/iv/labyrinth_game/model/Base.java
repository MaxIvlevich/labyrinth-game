package max.iv.labyrinth_game.model;

import lombok.Data;
import max.iv.labyrinth_game.model.enums.Direction;

import java.util.Set;
public record Base(int x, int y, Set<Direction> exits) {
    public boolean isAtBase(int x, int y) {
        return this.x == x && this.y == y;
    }
}
