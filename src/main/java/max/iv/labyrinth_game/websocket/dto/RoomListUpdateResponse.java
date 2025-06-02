package max.iv.labyrinth_game.websocket.dto;

import java.util.List;

public record RoomListUpdateResponse(
        GameMessageType type,
        List<RoomInfoDTO> rooms,
        int currentPage,
        int totalPages,
        long totalElements,
        int pageSize
) {
    public RoomListUpdateResponse(List<RoomInfoDTO> rooms, PageInfo pageInfo) {
        this(GameMessageType.ROOM_LIST_UPDATE,
                rooms,
                pageInfo.currentPage(),
                pageInfo.totalPages(),
                pageInfo.totalElements(),
                pageInfo.pageSize());
    }
}
