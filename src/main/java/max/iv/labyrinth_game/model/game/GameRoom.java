package max.iv.labyrinth_game.model.game;

import lombok.Getter;
import lombok.Setter;
import max.iv.labyrinth_game.model.game.enums.GamePhase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class GameRoom {
    private final String roomId;
    private String roomName;
    private Board board;
    private List<Player> players;
    private int currentPlayerIndex = -1;
    private GamePhase gamePhase;
    private Player winner;
    private int maxPlayers;

    public GameRoom(int maxPlayers,String roomName) {
        this.roomId = UUID.randomUUID().toString().substring(0, 8);
        this.players = new ArrayList<>();
        this.gamePhase = GamePhase.WAITING_FOR_PLAYERS;
        this.maxPlayers = maxPlayers;
        this.roomName = roomName;
    }

    public Player getCurrentPlayer() {
        if (players.isEmpty() || currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) {
            return null;
        }
        return players.get(currentPlayerIndex);
    }

    public void addPlayer(Player player) {
        if (players.size() < maxPlayers) {
            this.players.add(player);
        }
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public void nextTurn() {
        if (players.isEmpty()) return;
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        this.gamePhase = GamePhase.PLAYER_SHIFT;
    }
    public int getCurrentPlayersCount(){
        return players.size();
    }
    public void requirePhase(GamePhase expectedPhase) {
        if (this.getGamePhase() != expectedPhase) {
            throw new IllegalStateException("Expected phase: " + expectedPhase + ", but was: " + this.getGamePhase());
        }
    }
}
