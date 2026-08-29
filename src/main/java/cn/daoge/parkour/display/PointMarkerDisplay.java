package cn.daoge.parkour.display;

import cn.daoge.parkour.Parkour;
import cn.daoge.parkour.config.ParkourData;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;

import java.util.ArrayList;
import java.util.List;

public class PointMarkerDisplay {
    private final Parkour plugin;
    private final List<PacketArmorStand> entities = new ArrayList<>();

    public PointMarkerDisplay(Parkour plugin) {
        this.plugin = plugin;
    }

    public void refresh(ParkourData data, Level level) {
        close();
        if (!plugin.getScoreboardConfig().getBoolean("point-markers.enabled", true) || level == null) return;
        if (data.start != null) {
            spawn(level, data.start, plugin.getScoreboardConfig().getString("point-markers.start", "§a§lSTART"));
        }
        for (int i = 0; i < data.routePoints.size(); i++) {
            String text = plugin.getScoreboardConfig().getString("point-markers.checkpoint", "§e§lCHECKPOINT {index}")
                    .replace("{index}", String.valueOf(i + 1));
            spawn(level, data.routePoints.get(i), text);
        }
        if (data.end != null) {
            spawn(level, data.end, plugin.getScoreboardConfig().getString("point-markers.end", "§c§lFINISH"));
        }
    }

    private void spawn(Level level, Vector3 point, String text) {
        double offset = plugin.getScoreboardConfig().getDouble("point-markers.height-offset", 1.5);
        Position position = new Position(point.x, point.y + offset, point.z, level);
        entities.add(PacketArmorStand.spawn(level, position, text));
    }

    public void close() {
        new ArrayList<>(entities).forEach(PacketArmorStand::close);
        entities.clear();
    }
}
