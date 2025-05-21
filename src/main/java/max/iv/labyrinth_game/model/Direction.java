package max.iv.labyrinth_game.model;

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
}
