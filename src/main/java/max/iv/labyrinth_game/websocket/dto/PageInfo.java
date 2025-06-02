package max.iv.labyrinth_game.websocket.dto;

public record PageInfo(
        int currentPage,
        int pageSize,
        int totalPages,
        long totalElements
) {
}
