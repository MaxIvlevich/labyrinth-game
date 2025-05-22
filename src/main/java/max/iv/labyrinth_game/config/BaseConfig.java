package max.iv.labyrinth_game.config;

import java.util.List;
public record BaseConfig(int x, int y, List<String> exits, int playerIdRef) {}
