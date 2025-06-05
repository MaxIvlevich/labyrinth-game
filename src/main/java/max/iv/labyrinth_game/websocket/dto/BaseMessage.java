package max.iv.labyrinth_game.websocket.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({ // Перечисляем все возможные подтипы
        @JsonSubTypes.Type(value = CreateRoomRequest.class, name = "CREATE_ROOM"),
        @JsonSubTypes.Type(value = JoinRoomRequest.class, name = "JOIN_ROOM"),
        @JsonSubTypes.Type(value = PlayerShiftActionRequest.class, name = "PLAYER_ACTION_SHIFT"),
        @JsonSubTypes.Type(value = PlayerMoveActionRequest.class, name = "PLAYER_ACTION_MOVE"),
        @JsonSubTypes.Type(value = ErrorMessageResponse.class, name = "ERROR_MESSAGE"),
        @JsonSubTypes.Type(value = RoomCreatedResponse.class, name = "ROOM_CREATED"),
        @JsonSubTypes.Type(value = GameStateUpdateResponse.class, name = "GAME_STATE_UPDATE"),
        @JsonSubTypes.Type(value = GetRoomListRequest.class, name = "GET_ROOM_LIST_REQUEST"),
        @JsonSubTypes.Type(value = RoomListUpdateResponse.class, name = "ROOM_LIST_UPDATE"),
})
public class BaseMessage {
    private GameMessageType type;

    public BaseMessage(GameMessageType type) {
        this.type = type;
    }
}
