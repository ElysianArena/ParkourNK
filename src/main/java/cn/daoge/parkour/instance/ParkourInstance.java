package cn.daoge.parkour.instance;

import cn.daoge.parkour.Parkour;
import cn.daoge.parkour.config.LevelVector3;
import cn.daoge.parkour.config.ParkourData;
import cn.daoge.parkour.display.ArmorStandRanking;
import cn.daoge.parkour.display.PointMarkerDisplay;
import cn.daoge.parkour.storage.IParkourStorage;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.weather.EntityLightning;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.PluginTask;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
public class ParkourInstance implements IParkourInstance {

    protected ParkourData data;
    protected IParkourStorage storage;
    protected Map<Player, PlayingData> playerMap = new HashMap<>();
    protected ArmorStandRanking rankingDisplay;
    protected PointMarkerDisplay pointMarkerDisplay;

    public ParkourInstance(IParkourStorage storage) {
        this.storage = storage;
        this.data = storage.read();
        if (this.data == null) {
            this.data = new ParkourData();
        }
        if (this.data.routePoints == null) {
            this.data.routePoints = new java.util.ArrayList<>();
        }
        if (this.data.ranking == null) {
            this.data.ranking = new java.util.HashMap<>();
        }
        if (this.data.rankingTextPos == null) {
            this.data.rankingTextPos = new java.util.ArrayList<>();
        }
        if (!this.data.rankingTextPos.isEmpty()) {
            refreshRanking();
        }
        this.pointMarkerDisplay = new PointMarkerDisplay(Parkour.getInstance());
        refreshPointMarkers();
        Server.getInstance().getScheduler().scheduleRepeatingTask(new RefreshTask(), 2);
        Server.getInstance().getScheduler().scheduleRepeatingTask(new RankingRefreshTask(), 200);
    }

    @Override
    public Level getLevel() {
        return getLevel(this.data.levelName);
    }

    protected Level getLevel(String levelName) {
        if (levelName == null || levelName.isBlank()) return null;
        Server server = Server.getInstance();
        return server.isLevelLoaded(levelName)
                ? server.getLevelByName(levelName)
                : (server.loadLevel(levelName) ? server.getLevelByName(levelName) : null);
    }

    @Override
    public void save() {
        this.storage.save(this.data);
    }

    @Override
    public boolean isComplete() {
        return this.getLevel() != null &&
                this.data.start != null &&
                this.data.end != null &&
                this.data.tpPos != null;
    }

    @Override
    public void join(Player player) {
        if (!player.floor().equals(this.data.start.floor()) || !player.level.equals(getLevel()))
            player.teleport(Position.fromObject(this.data.start, getLevel()));
        setParkourItems(player);
        this.playerMap.put(player, new PlayingData());
        Parkour.getInstance().getReplayManager().startRecording(player);
        player.sendMessage(Parkour.getInstance().getLang().message("join", "room", this.data.name));
    }

    @Override
    public void tp(Player player) {
        player.teleport(Position.fromObject(this.data.tpPos, getLevel()));
        player.sendMessage(Parkour.getInstance().getLang().message("teleport", "room", this.data.name));
    }

    @Override
    public void quit(Player player) {
        player.teleport(Position.fromObject(this.data.tpPos, getLevel()));
        player.getInventory().clearAll();
        this.playerMap.remove(player);
        Parkour.getInstance().getReplayManager().stopRecording(player);
        Parkour.getInstance().getParkourScoreboard().showLobby(player);
        player.sendMessage(Parkour.getInstance().getLang().message("quit", "room", this.data.name));
    }

    @Override
    public boolean isPlaying(Player player) {
        return this.playerMap.containsKey(player);
    }

    @Override
    public void pause(Player player, boolean pause) {
        PlayingData playingData = this.playerMap.get(player);
        long now = System.nanoTime();
        playingData.paused = pause;
        if (pause) {
            playingData.updateTime(now);
            playingData.pausedAtNanos = now;
            playingData.pausedLoc = player.getLocation();
            player.sendMessage(Parkour.getInstance().getLang().message("pause"));
        } else {
            if (playingData.pausedAtNanos > 0) {
                playingData.pausedNanos += now - playingData.pausedAtNanos;
                playingData.pausedAtNanos = 0;
            }
            player.teleport(playingData.pausedLoc);
            playingData.pausedLoc = null;
            player.sendMessage(Parkour.getInstance().getLang().message("resume"));
        }
    }

    @Override
    public boolean isPaused(Player player) {
        return this.playerMap.get(player).paused;
    }

    @Override
    public void onReachPoint(Player player, Vector3 point) {
        this.playerMap.get(player).lastPoint = point;
        this.playerMap.get(player).reachedPoints++;
        player.sendMessage(Parkour.getInstance().getLang().message("checkpoint"));
        player.level.addSound(point, Sound.RANDOM_LEVELUP);
    }

    @Override
    public void onReachEnd(Player player) {
        PlayingData playingData = this.playerMap.get(player);
        playingData.updateTime(System.nanoTime());
        String formattedTime = String.format("%.3f", playingData.timeUsed);
        player.sendMessage(Parkour.getInstance().getLang().message("finish", "room", this.data.name, "time", formattedTime));
        for (Player other : player.level.getPlayers().values())
            if (other != player) other.sendMessage(Parkour.getInstance().getLang().message("finish_broadcast",
                    "player", player.getName(), "room", this.data.name, "time", formattedTime));
        player.getInventory().clearAll();
        double time = Double.parseDouble(formattedTime);
        Parkour.getInstance().getRepository().submit(this.data.name, player.getName(), time,
                Parkour.getInstance().getReplayManager().stopRecording(player));
        refreshRanking();
        Parkour.getInstance().getParkourScoreboard().showLobby(player);
        spawnLightning(player);
        this.playerMap.remove(player);
    }

    @Override
    public Vector3 getLastPoint(Player player) {
        return this.playerMap.get(player).lastPoint;
    }

    @Override
    public double getTimeUsed(Player player) {
        PlayingData playingData = playerMap.get(player);
        return playingData == null ? 0 : playingData.timeUsed;
    }

    @Override
    public int getReachedPoints(Player player) {
        PlayingData playingData = playerMap.get(player);
        return playingData == null ? 0 : playingData.reachedPoints;
    }

    @Override
    public void addRankingText(Position pos) {
        this.data.rankingTextPos.add(new LevelVector3(pos.getFloorX() + 0.5, pos.getFloorY() + 0.5, pos.getFloorZ() + 0.5, pos.level.getName()));
        refreshRanking();
        save();
    }

    @Override
    public Set<Player> getPlayers() {
        return this.playerMap.keySet();
    }

    protected void spawnLightning(Player player) {
        EntityLightning entity = new EntityLightning(player.getChunk(), EntityLightning.getDefaultNBT(player.clone()));
        entity.setEffect(false);
        entity.spawnToAll();
    }

    protected void refreshRanking() {
        if (rankingDisplay == null) {
            rankingDisplay = new ArmorStandRanking(Parkour.getInstance(), this.data.name);
        }
        rankingDisplay.refresh(this.data.rankingTextPos);
    }

    @Override
    public void refreshRankingDisplay() {
        refreshRanking();
    }

    protected void setParkourItems(Player player) {
        BaseInventory inventory = player.getInventory();
        inventory.clearAll();
        Item back = Item.get(Parkour.BACK_ITEM_ID);
        Item info = Item.get(Parkour.INFO_ITEM_ID);
        Item pause = Item.get(Parkour.PAUSE_ITEM_ID);
        Item escape = Item.get(Parkour.ESCAPE_ITEM_ID);
        back.setItemLockMode(Item.ItemLockMode.LOCK_IN_SLOT);
        info.setItemLockMode(Item.ItemLockMode.LOCK_IN_SLOT);
        pause.setItemLockMode(Item.ItemLockMode.LOCK_IN_SLOT);
        escape.setItemLockMode(Item.ItemLockMode.LOCK_IN_SLOT);
        back.setCustomName(Parkour.getInstance().getLang().text("item_back"));
        info.setCustomName(Parkour.getInstance().getLang().text("item_info"));
        pause.setCustomName(Parkour.getInstance().getLang().text("item_pause"));
        escape.setCustomName(Parkour.getInstance().getLang().text("item_escape"));
        inventory.setItem(1, back);
        inventory.setItem(3, info);
        inventory.setItem(5, pause);
        inventory.setItem(7, escape);
    }

    //记录游玩信息，例如上一个路径点，是否暂停等
    public class PlayingData {
        public boolean paused = false;
        public Vector3 lastPoint = getData().start;
        public Location pausedLoc;
        public double timeUsed = 0;//second, millisecond precision
        public final long startedAtNanos = System.nanoTime();
        public long pausedAtNanos = 0;
        public long pausedNanos = 0;
        public int reachedPoints = 0;

        public void updateTime(long now) {
            long effectiveNanos = now - startedAtNanos - pausedNanos;
            if (pausedAtNanos > 0) effectiveNanos -= now - pausedAtNanos;
            timeUsed = Math.max(0, effectiveNanos / 1_000_000_000d);
        }
    }

    public class RefreshTask extends PluginTask<Parkour> {

        public RefreshTask() {
            super(Parkour.getInstance());
        }

        @Override
        public void onRun(int i) {
            long now = System.nanoTime();
            playerMap.forEach((player, playingData) -> {
                playingData.updateTime(now);
                Parkour.getInstance().getParkourScoreboard().update(player, ParkourInstance.this,
                        playingData.timeUsed, playingData.reachedPoints);
                player.sendActionBar((playingData.paused ? "§e----Pausing----\n§r" : "") +
                        "Time Used: §a" + String.format("%.3f", playingData.timeUsed), 0, 1, 0);
            });
        }
    }

    public class RankingRefreshTask extends PluginTask<Parkour> {
        public RankingRefreshTask() {
            super(Parkour.getInstance());
        }

        @Override
        public void onRun(int currentTick) {
            refreshRanking();
        }
    }

    @Override
    public void refreshPointMarkers() {
        if (pointMarkerDisplay == null) return;
        pointMarkerDisplay.refresh(data, getLevel());
    }

    @Override
    public void close() {
        if (rankingDisplay != null) rankingDisplay.close();
        if (pointMarkerDisplay != null) pointMarkerDisplay.close();
    }
}
