package max.iv.labyrinth_game.websocket.dto;

import java.util.List;

public record  RoomListUpdateResponse (
        GameMessageType type,
        List<RoomSummaryDTO> rooms, // Комнаты на текущей странице
        int currentPage,            // Номер текущей страницы (0-based)
        long totalItems,            // Общее количество комнат
        int totalPages              // Общее количество страниц

){
    public RoomListUpdateResponse(List<RoomSummaryDTO> rooms, int currentPage, long totalItems, int totalPages) {
        this(GameMessageType.ROOM_LIST_UPDATE, rooms, currentPage, totalItems, totalPages);
    }

}
