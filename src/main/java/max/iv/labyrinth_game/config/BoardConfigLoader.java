package max.iv.labyrinth_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Getter
@Setter
        //@Component
public class BoardConfigLoader {

    @Value("classpath:board-layout.json")
    private Resource configFile;

    private BoardConfig boardConfig;

    @PostConstruct
    public void loadConfig() {
        ObjectMapper objectMapper = new ObjectMapper();

        if (configFile == null || !configFile.exists()) {
            log.error("Board configuration file resource is null or does not exist: {}",
                    (configFile != null ? configFile.getDescription() : "null resource"));
            throw new RuntimeException("Board configuration file resource is missing.");
        }
        try (InputStream inputStream = configFile.getInputStream()) {
            this.boardConfig = objectMapper.readValue(inputStream, BoardConfig.class);
            log.info("Board configuration loaded successfully from: {}", configFile.getFilename());
            validateConfig(this.boardConfig);
        } catch (IOException e) {
            log.error("Failed to load board configuration from: {}", configFile.getFilename(), e);
            throw new RuntimeException("Failed to load board configuration", e);
        }
    }

    public BoardConfig getBoardConfig() {
        if (this.boardConfig == null) {
            // Этого не должно случиться, если @PostConstruct отработал или была ошибка
            log.error("BoardConfig requested before loading or after a loading failure.");
            throw new IllegalStateException("Board configuration is not available.");
        }
        return this.boardConfig;
    }

    private void validateConfig(BoardConfig config) {
        // Пример валидации:
        if (config == null) {
            throw new IllegalArgumentException("BoardConfig cannot be null.");
        }
        if (config.boardSize() <= 0 || config.boardSize() % 2 == 0) {
            throw new IllegalArgumentException("Invalid board size in config: " + config.boardSize());
        }
        if (config.bases() == null || config.bases().isEmpty()) {
            throw new IllegalArgumentException("Bases configuration is missing or empty.");
        }
        // ... другие проверки (количество баз, корректность координат, типы тайлов и т.д.)
        log.info("Board configuration validated successfully.");
    }
}
