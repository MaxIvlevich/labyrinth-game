package max.iv.labyrinth_game.websocket.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME, // Используем имя типа из поля "type"
        include = JsonTypeInfo.As.PROPERTY, // Поле "type" будет свойством в JSON
        property = "type" // Имя поля, которое определяет тип
)
@JsonSubTypes({ // Перечисляем все возможные подтипы
        @JsonSubTypes.Type(value = CreateRoomRequest.class, name = "CREATE_ROOM"),
        @JsonSubTypes.Type(value = JoinRoomRequest.class, name = "JOIN_ROOM"),
        @JsonSubTypes.Type(value = PlayerShiftActionRequest.class, name = "PLAYER_ACTION_SHIFT"),
        @JsonSubTypes.Type(value = PlayerMoveActionRequest.class, name = "PLAYER_ACTION_MOVE"),
        @JsonSubTypes.Type(value = ErrorMessageResponse.class, name = "ERROR_MESSAGE"),
        @JsonSubTypes.Type(value = RoomCreatedResponse.class, name = "ROOM_CREATED"),
        @JsonSubTypes.Type(value = GameStateUpdateResponse.class, name = "GAME_STATE_UPDATE"),
        // ... и GameStateUpdateDTO, GameOverMessageDTO когда они будут созданы
})
public class BaseMessage {
    private GameMessageType type; // Тип сообщения

    public BaseMessage(GameMessageType type) {
        this.type = type;
    }
}
