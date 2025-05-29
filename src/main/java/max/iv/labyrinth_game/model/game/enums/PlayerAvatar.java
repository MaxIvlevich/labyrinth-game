package max.iv.labyrinth_game.model.game.enums;

import java.util.List;

public enum PlayerAvatar {
    KNIGHT("Рыцарь", "knight.png", "#C0C0C0"),
    MAGE("Маг", "mage.png", "#0000FF"),
    ARCHER("Лучник", "archer.png", "#008000"),
    ROGUE("Разбойник", "rogue.png", "#808080");


    private final String displayName;  // Имя, которое можно показать пользователю
    private final String imageName;    // Имя файла изображения (путь будет на фронтенде)
    private final String defaultColorHex; // Дефолтный цвет, если картинка не загрузится или для фона

    PlayerAvatar(String displayName, String imageName, String defaultColorHex) {
        this.displayName = displayName;
        this.imageName = imageName;
        this.defaultColorHex = defaultColorHex;
    }
     public static List<PlayerAvatar> getAllAvatars() {
         return List.of(values());
     }
}
