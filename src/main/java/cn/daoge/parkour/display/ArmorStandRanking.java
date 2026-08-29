package cn.daoge.parkour.display;

import cn.daoge.parkour.Parkour;
import cn.daoge.parkour.config.LevelVector3;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ArmorStandRanking {
    private final Parkour plugin;
    private final String room;
    private final List<PacketArmorStand> entities = new ArrayList<>();
    private String lastContent;

    public ArmorStandRanking(Parkour plugin, String room) {
        this.plugin = plugin;
        this.room = room;
    }

    public void refresh(List<LevelVector3> positions) {
        double spacing = plugin.getScoreboardConfig().getDouble("ranking.line-spacing", 0.28);
        String title = plugin.getScoreboardConfig().getString("ranking.title", "§b§l{room} Top 15");
        String format = plugin.getScoreboardConfig().getString("ranking.line", "§e#{rank} §f{player} §a{time}s");
        List<Map.Entry<String, Double>> ranking = plugin.getRepository().getRanking(room);
        String content = positions.toString() + '|' + spacing + '|' + title + '|' + format + '|' + ranking;
        if (content.equals(lastContent)) return;
        close();
        lastContent = content;
        for (LevelVector3 base : positions) {
            Level level = plugin.getServer().getLevelByName(base.getLevelName());
            if (level == null && plugin.getServer().loadLevel(base.getLevelName())) {
                level = plugin.getServer().getLevelByName(base.getLevelName());
            }
            if (level == null) continue;
            spawn(level, new Position(base.x, base.y, base.z, level), title.replace("{room}", room));
            for (int i = 0; i < ranking.size(); i++) {
                Map.Entry<String, Double> entry = ranking.get(i);
                String line = format.replace("{rank}", String.valueOf(i + 1))
                        .replace("{player}", entry.getKey())
                        .replace("{time}", String.format("%.3f", entry.getValue()));
                spawn(level, new Position(base.x, base.y - ((i + 1) * spacing), base.z, level), line);
            }
        }
    }

    private void spawn(Level level, Position position, String text) {
        entities.add(PacketArmorStand.spawn(level, position, text));
    }

    public void close() {
        new ArrayList<>(entities).forEach(PacketArmorStand::close);
        entities.clear();
        lastContent = null;
    }
}
