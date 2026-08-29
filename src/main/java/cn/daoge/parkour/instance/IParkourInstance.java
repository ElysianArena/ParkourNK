package cn.daoge.parkour.instance;

import cn.daoge.parkour.config.ParkourData;
import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;

import java.util.Set;

public interface IParkourInstance {
    Level getLevel();

    ParkourData getData();

    void save();

    boolean isComplete();

    void join(Player player);

    void tp(Player player);

    void quit(Player player);

    boolean isPlaying(Player player);

    void pause(Player player, boolean pause);

    boolean isPaused(Player player);

    void onReachPoint(Player player, Vector3 point);

    void onReachEnd(Player player);

    Vector3 getLastPoint(Player player);

    double getTimeUsed(Player player);

    int getReachedPoints(Player player);

    void addRankingText(Position pos);

    void refreshPointMarkers();

    void refreshRankingDisplay();

    Set<Player> getPlayers();

    void close();
}
