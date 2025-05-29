package max.iv.labyrinth_game.config.game;

import java.util.List;
public record BaseConfig(int x, int y, List<String> exits, int playerIdRef) {}
