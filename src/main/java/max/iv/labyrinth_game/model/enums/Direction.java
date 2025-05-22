package max.iv.labyrinth_game.model.enums;

public enum Direction {
    NORTH, EAST, SOUTH, WEST;
    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case SOUTH -> NORTH;
            case WEST -> EAST;
        };
    }

    public int getDx() {
        return switch (this) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
    }

    public int getDy() {
        return switch (this) {
            case SOUTH -> 1;
            case NORTH -> -1;
            default -> 0;
        };
    }
    public boolean isHorizontal() {
        return this == EAST || this == WEST;
    }

    public boolean isVertical() {
        return this == NORTH || this == SOUTH;
    }
}
