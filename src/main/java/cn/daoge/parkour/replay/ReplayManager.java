package cn.daoge.parkour.replay;

import cn.daoge.parkour.Parkour;
import cn.daoge.parkour.instance.IParkourInstance;
import cn.nukkit.Player;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Position;
import net.easecation.ghosty.GhostyPlugin;
import net.easecation.ghosty.playback.PlayerPlaybackEngine;
import net.easecation.ghosty.recording.PlayerRecordEngine;
import net.easecation.ghosty.recording.player.PlayerRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReplayManager {
    public static final int REWIND_ITEM_ID = ItemID.COMPASS;
    public static final int FORWARD_ITEM_ID = ItemID.CLOCK;
    public static final int EXIT_ITEM_ID = ItemID.BED;

    private final Parkour plugin;
    private final Map<Player, PlayerRecordEngine> recordings = new HashMap<>();
    private final Map<Player, ReplaySession> sessions = new HashMap<>();

    public ReplayManager(Parkour plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, this::tick, 1);
    }

    public void startRecording(Player player) {
        stopRecording(player);
        PlayerRecordEngine engine = new PlayerRecordEngine(player);
        engine.setRecording(true);
        recordings.put(player, engine);
        GhostyPlugin.getInstance().recordingPlayerEngines.put(player, engine);
    }

    public PlayerRecord stopRecording(Player player) {
        PlayerRecordEngine engine = recordings.remove(player);
        if (engine == null) return null;
        GhostyPlugin.getInstance().recordingPlayerEngines.remove(player);
        return engine.stopRecord();
    }

    public void openRoomList(Player player) {
        List<IParkourInstance> rooms = plugin.getParkourInstanceMap().values().stream()
                .filter(IParkourInstance::isComplete)
                .filter(instance -> plugin.getRepository().hasReplays(instance.getData().name))
                .sorted((first, second) -> first.getData().name.compareToIgnoreCase(second.getData().name))
                .toList();
        if (rooms.isEmpty()) {
            player.sendMessage(plugin.getLang().message("replay_rooms_empty"));
            return;
        }
        List<ElementButton> buttons = rooms.stream()
                .map(instance -> new ElementButton("§f" + instance.getData().name))
                .toList();
        FormWindowSimple form = new FormWindowSimple(plugin.getLang().text("replay_room_title"), "", buttons);
        form.addHandler((viewer, id) -> {
            if (form.getResponse() == null) return;
            openReplayList(viewer, rooms.get(form.getResponse().getClickedButtonId()));
        });
        player.showFormWindow(form);
    }

    public void openReplayList(Player player, IParkourInstance instance) {
        var data = plugin.getRepository().get(instance.getData().name);
        List<Map.Entry<String, Double>> ranking = plugin.getRepository().getRanking(instance.getData().name).stream()
                .filter(entry -> data.replays.containsKey(entry.getKey()))
                .toList();
        if (ranking.isEmpty()) {
            player.sendMessage(plugin.getLang().message("replay_not_found"));
            return;
        }
        List<ElementButton> buttons = ranking.stream()
                .map(entry -> new ElementButton("§f" + entry.getKey() + "\n§a" + String.format("%.3fs", entry.getValue())))
                .toList();
        FormWindowSimple form = new FormWindowSimple("§bReplay | §f" + instance.getData().name, "", buttons);
        form.addHandler((viewer, id) -> {
            if (form.getResponse() == null) return;
            int index = form.getResponse().getClickedButtonId();
            startReplay(viewer, instance, ranking.get(index).getKey());
        });
        player.showFormWindow(form);
    }

    public void startReplay(Player player, IParkourInstance instance, String replayPlayer) {
        stopReplay(player, false);
        PlayerRecord record = plugin.getRepository().getReplay(instance.getData().name, replayPlayer);
        if (record == null) {
            player.sendMessage(plugin.getLang().message("replay_not_found"));
            return;
        }
        int oldGamemode = player.getGamemode();
        player.teleport(Position.fromObject(instance.getData().start, instance.getLevel()));
        player.setGamemode(Player.SPECTATOR);
        player.getInventory().clearAll();
        setControlItem(player, 1, REWIND_ITEM_ID, plugin.getLang().text("item_rewind"));
        setControlItem(player, 5, FORWARD_ITEM_ID, plugin.getLang().text("item_forward"));
        setControlItem(player, 8, EXIT_ITEM_ID, plugin.getLang().text("item_replay_exit"));
        PlayerPlaybackEngine engine = new PlayerPlaybackEngine(record, instance.getLevel(), new ArrayList<>(List.of(player)));
        engine.setOnStopDo(() -> stopReplay(player, true));
        engine.resume();
        sessions.put(player, new ReplaySession(instance, engine, oldGamemode, player));
        player.sendMessage(plugin.getLang().message("replay_start", "player", replayPlayer));
        player.sendMessage(plugin.getLang().message("replay_controls"));
    }

    private void setControlItem(Player player, int slot, int id, String name) {
        Item item = Item.get(id);
        item.setCustomName(name);
        item.setItemLockMode(Item.ItemLockMode.LOCK_IN_SLOT);
        player.getInventory().setItem(slot, item);
    }

    public boolean isReplaying(Player player) {
        return sessions.containsKey(player);
    }

    public void control(Player player, int itemId) {
        ReplaySession session = sessions.get(player);
        if (session == null) return;
        if (itemId == REWIND_ITEM_ID) session.engine.backward(200);
        if (itemId == FORWARD_ITEM_ID) session.engine.forward(200);
        if (itemId == EXIT_ITEM_ID) stopReplay(player, false);
    }

    public void stopReplay(Player player, boolean finished) {
        ReplaySession session = sessions.remove(player);
        if (session == null) return;
        if (!session.engine.isStopped()) session.engine.stopPlayback();
        player.setGamemode(session.oldGamemode);
        player.getInventory().clearAll();
        player.teleport(Position.fromObject(session.instance.getData().tpPos, session.instance.getLevel()));
        player.sendMessage(plugin.getLang().message(finished ? "replay_end" : "replay_exit"));
    }

    public void shutdown() {
        new ArrayList<>(recordings.keySet()).forEach(this::stopRecording);
        new ArrayList<>(sessions.keySet()).forEach(player -> stopReplay(player, false));
    }

    private void tick() {
        recordings.values().forEach(PlayerRecordEngine::onTick);
        new ArrayList<>(sessions.values()).forEach(session -> {
            if (!session.engine.isStopped()) {
                session.engine.onTick();
                restrictVisibility(session);
            }
        });
    }

    private void restrictVisibility(ReplaySession session) {
        if (session.engine.getNPC() == null) return;
        for (Player viewer : session.instance.getLevel().getPlayers().values()) {
            if (viewer.equals(session.viewer)) {
                session.engine.getNPC().spawnTo(viewer);
            } else {
                session.engine.getNPC().hideFrom(viewer);
            }
        }
    }

    private record ReplaySession(IParkourInstance instance, PlayerPlaybackEngine engine, int oldGamemode,
                                 Player viewer) {
    }
}
