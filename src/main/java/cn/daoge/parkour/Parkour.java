package cn.daoge.parkour;

import cn.daoge.parkour.command.ParkourCommand;
import cn.daoge.parkour.config.ParkourData;
import cn.daoge.parkour.config.Lang;
import cn.daoge.parkour.display.ParkourScoreboard;
import cn.daoge.parkour.display.PacketArmorStand;
import cn.daoge.parkour.instance.IParkourInstance;
import cn.daoge.parkour.instance.ParkourInstance;
import cn.daoge.parkour.storage.JSONParkourStorage;
import cn.daoge.parkour.storage.ElyParkourRepository;
import cn.daoge.parkour.replay.ReplayManager;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerRespawnEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementButtonImageData;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Getter
public class Parkour extends PluginBase implements Listener {

    //todo: config control
    public static final int BACK_ITEM_ID = ItemID.COMPASS;
    public static final int INFO_ITEM_ID = ItemID.ENCHANTED_BOOK;
    public static final int PAUSE_ITEM_ID = ItemID.CLOCK;
    public static final int ESCAPE_ITEM_ID = ItemID.BED;

    @Getter
    protected static Parkour instance;
    protected Map<String, IParkourInstance> parkourInstanceMap = new HashMap<>();
    protected Map<Player, IParkourInstance> currentPlayingParkour = new HashMap<>();
    protected Path dataPath;
    protected Config scoreboardConfig;
    protected Lang lang;
    protected ElyParkourRepository repository;
    protected ReplayManager replayManager;
    protected ParkourScoreboard parkourScoreboard;

    {
        instance = this;
    }

    @Override
    public void onEnable() {
        saveResource("scoreboard.yml");
        saveResource("lang.yml");
        this.scoreboardConfig = new Config(getDataFolder() + "/scoreboard.yml", Config.YAML);
        this.lang = new Lang(new Config(getDataFolder() + "/lang.yml", Config.YAML));
        this.repository = new ElyParkourRepository();
        this.replayManager = new ReplayManager(this);
        this.parkourScoreboard = new ParkourScoreboard(scoreboardConfig);
        PacketArmorStand.initialize(this);
        this.dataPath = this.getDataFolder().toPath().resolve("instances");
        if (!Files.exists(this.dataPath)) {
            try {
                Files.createDirectories(this.dataPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        loadParkourInstance();
        Server.getInstance().getCommandMap().register("", new ParkourCommand("parkour"));
        Server.getInstance().getPluginManager().registerEvents(this, this);
        Server.getInstance().getOnlinePlayers().values().forEach(parkourScoreboard::showLobby);
        Server.getInstance().getScheduler().scheduleRepeatingTask(this, parkourScoreboard::refreshLobby, 20);
    }

    @Override
    public void onDisable() {
        for (IParkourInstance value : this.parkourInstanceMap.values()) {
            value.save();
            value.close();
        }
        replayManager.shutdown();
        PacketArmorStand.shutdown();
    }

    public void joinTo(Player player, IParkourInstance instance) {
        if (!instance.isComplete()) {
            player.sendMessage(lang.message("room_incomplete", "room", instance.getData().name));
            return;
        }
        this.currentPlayingParkour.put(player, instance);
        instance.join(player);
    }

    public void tpTo(Player player, IParkourInstance instance) {
        instance.tp(player);
    }

    public void quitFromParkour(Player player) {
        if (!currentPlayingParkour.containsKey(player)) return;
        IParkourInstance instance = this.currentPlayingParkour.remove(player);
        instance.quit(player);
    }

    public void sendParkourInfo(Player player, IParkourInstance instance) {
        ParkourData data = instance.getData();
        StringBuilder builder = new StringBuilder();
        builder.append(lang.text("form_ranking"));
        repository.getRanking(data.name).forEach(entry -> builder.append("§l[")
                .append(entry.getKey()).append("]: §b").append(entry.getValue()).append("s§f\n"));
        FormWindowSimple form = new FormWindowSimple(lang.text("form_info_title", "room", data.name), builder.toString());
        player.showFormWindow(form);
    }

    public void sendParkourInfoRoomList(Player player) {
        List<IParkourInstance> rooms = this.parkourInstanceMap.values().stream()
                .filter(IParkourInstance::isComplete)
                .sorted((first, second) -> first.getData().name.compareToIgnoreCase(second.getData().name))
                .toList();
        if (rooms.isEmpty()) {
            player.sendMessage(lang.message("room_list_empty"));
            return;
        }
        List<ElementButton> buttons = rooms.stream()
                .map(instance -> new ElementButton("§f" + instance.getData().name))
                .toList();
        FormWindowSimple form = new FormWindowSimple(lang.text("info_room_title"), "", buttons);
        form.addHandler((viewer, id) -> {
            if (form.getResponse() == null) return;
            sendParkourInfo(viewer, rooms.get(form.getResponse().getClickedButtonId()));
        });
        player.showFormWindow(form);
    }

    public void sendParkourListForm(Player player) {
        List<ElementButton> buttons = this.parkourInstanceMap.values()
                .stream()
                .filter(IParkourInstance::isComplete)
                .map(this::generateListButton)
                .toList();
        FormWindowSimple form = new FormWindowSimple(lang.text("form_list_title"), "", buttons);
        form.addHandler((player1, i) -> {
            if (form.getResponse() == null) return;
            ParkourElementButton clickedButton = (ParkourElementButton) form.getResponse().getClickedButton();
            tpTo(player1, clickedButton.instance);
        });
        player.showFormWindow(form);
    }

    public void addParkourInstance(IParkourInstance instance) {
        this.parkourInstanceMap.put(instance.getData().name, instance);
    }

    protected ElementButton generateListButton(IParkourInstance instance) {
        return new ParkourElementButton("§f§l" + instance.getData().name + "\n§bPlaying: " + instance.getPlayers().size(), new ElementButtonImageData("path", "textures/blocks/grass_side_carried.png"), instance);
    }

    protected void loadParkourInstance() {
        try (Stream<Path> walk = Files.walk(this.dataPath)) {
            for (Path instancePath : walk.filter(Files::isRegularFile).toList()) {
                IParkourInstance instance = createParkourInstance(instancePath);
                addParkourInstance(instance);
                this.getLogger().info("[§bParkour§r] Successfully load parkour instance §a" + instance.getData().name);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected IParkourInstance createParkourInstance(Path instancePath) {
        return new ParkourInstance(new JSONParkourStorage(instancePath));
    }

    @EventHandler
    protected void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom().floor();
        Location to = event.getTo().floor();
        if (!from.level.getName().equals(to.level.getName()) && currentPlayingParkour.containsKey(player)) {
            quitFromParkour(player);
            return;
        }
        if (!from.equals(to)) {
            if (!currentPlayingParkour.containsKey(player)) {
                parkourInstanceMap.forEach((name, instance) -> {
                    if (instance.isComplete() &&
                            !currentPlayingParkour.containsKey(player) &&
                            player.level.getName().equals(instance.getLevel().getName()) &&
                            instance.getData().start.floor().equals(to)) {
                        joinTo(player, instance);
                    }
                });
            } else {
                IParkourInstance currentPlaying = currentPlayingParkour.get(player);
                if (currentPlaying.isPaused(player)) {
                    return;
                }
                if (currentPlaying.getData().end.floor().equals(to)) {
                    currentPlayingParkour.remove(player);
                    currentPlaying.onReachEnd(player);
                    return;
                }
                for (Vector3 routePoint : currentPlaying.getData().routePoints) {
                    if (routePoint.floor().equals(to) && !currentPlaying.getLastPoint(player).floor().equals(to)) {
                        currentPlaying.onReachPoint(player, routePoint.floor().add(0.5, 0, 0.5));
                    }
                }
            }
        }
    }

    @EventHandler
    protected void onPlayerInteractItem(PlayerInteractEvent event) {
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
                && event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (replayManager.isReplaying(player)) {
            replayManager.control(player, player.getInventory().getItemInHand().getId());
            event.setCancelled();
            return;
        }
        IParkourInstance currentPlaying = currentPlayingParkour.get(player);
        if (currentPlaying == null) return;
        switch (player.getInventory().getItemInHand().getId()) {
            case BACK_ITEM_ID -> {
                Vector3 lastRoutePoint = currentPlaying.getLastPoint(player);
                player.teleport(lastRoutePoint);
            }
            case INFO_ITEM_ID -> sendParkourInfo(player, currentPlaying);
            case PAUSE_ITEM_ID -> currentPlaying.pause(player, !currentPlaying.isPaused(player));
            case ESCAPE_ITEM_ID -> quitFromParkour(player);
        }
    }

    @EventHandler
    protected void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (currentPlayingParkour.containsKey(player) || replayManager.isReplaying(player)) {
            event.setCancelled();
        }
    }

    @EventHandler
    protected void onPlayerJoin(PlayerJoinEvent event) {
        parkourScoreboard.showLobby(event.getPlayer());
    }

    @EventHandler
    protected void onPlayerQuit(PlayerQuitEvent event) {
        quitFromParkour(event.getPlayer());
        replayManager.stopReplay(event.getPlayer(), false);
        parkourScoreboard.hide(event.getPlayer());
    }

    @EventHandler
    protected void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        IParkourInstance currentPlaying = currentPlayingParkour.get(player);
        if (currentPlaying == null) return;
        Vector3 lastRoutePoint = currentPlaying.getLastPoint(player);
        event.setRespawnPosition(Position.fromObject(lastRoutePoint, player.level));
    }

    protected class ParkourElementButton extends ElementButton {

        @Getter
        @Setter
        protected transient IParkourInstance instance;

        public ParkourElementButton(String text) {
            super(text);
        }

        public ParkourElementButton(String text, ElementButtonImageData image) {
            super(text, image);
        }

        public ParkourElementButton(String text, ElementButtonImageData image, IParkourInstance instance) {
            super(text, image);
            this.instance = instance;
        }
    }
}
