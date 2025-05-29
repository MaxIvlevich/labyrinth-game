package max.iv.labyrinth_game.model.game;

import max.iv.labyrinth_game.model.game.enums.Direction;

import java.util.Set;
public record Base(int x, int y, Set<Direction> exits) {
    public boolean isAtBase(int x, int y) {
        return this.x == x && this.y == y;
    }
}
