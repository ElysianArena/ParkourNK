package cn.daoge.parkour.display;

import cn.daoge.parkour.Parkour;
import cn.daoge.parkour.instance.IParkourInstance;
import cn.nukkit.Player;
import cn.nukkit.scoreboard.Scoreboard;
import cn.nukkit.utils.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

public class ParkourScoreboard {
    private final Config config;
    private final Map<Player, Scoreboard> boards = new HashMap<>();
    private final Map<Player, List<String>> renderedLines = new HashMap<>();

    public ParkourScoreboard(Config config) {
        this.config = config;
    }

    public void update(Player player, IParkourInstance instance, double time, int point) {
        Scoreboard board = getOrCreate(player);
        setLines(player, board, config.getStringList("lines"), instance, time, point);
    }

    public void showLobby(Player player) {
        Scoreboard board = getOrCreate(player);
        List<String> lines = config.getStringList("lobby-lines");
        if (lines.isEmpty()) {
            lines = List.of("§fWelcome to Parkour", "", "§7/pk send list");
        }
        setLines(player, board, lines, null, 0, 0);
    }

    public void refreshLobby() {
        boards.keySet().stream()
                .filter(player -> !Parkour.getInstance().getCurrentPlayingParkour().containsKey(player)
                        && !Parkour.getInstance().getReplayManager().isReplaying(player))
                .toList()
                .forEach(this::showLobby);
    }

    private Scoreboard getOrCreate(Player player) {
        return boards.computeIfAbsent(player, ignored -> {
            Scoreboard created = new Scoreboard(config.getString("title", "Parkour"),
                    Scoreboard.SortOrder.DESCENDING, Scoreboard.DisplaySlot.SIDEBAR);
            created.showTo(player);
            return created;
        });
    }

    private void setLines(Player player, Scoreboard board, List<String> lines, IParkourInstance instance, double time, int point) {
        VariableContext context = createContext(player, instance, time, point);
        List<String> rendered = lines.stream().map(line -> replaceVariables(line, context).replace('&', '§')).toList();
        if (rendered.equals(renderedLines.get(player))) return;
        renderedLines.put(player, rendered);
        board.holdUpdates();
        board.clear();
        for (int i = 0; i < rendered.size(); i++) {
            board.setScore(rendered.get(i) + "§" + Integer.toHexString(i), rendered.size() - i);
        }
        board.unholdUpdates();
    }

    private VariableContext createContext(Player player, IParkourInstance instance, double time, int point) {
        String room = instance == null ? "-" : instance.getData().name;
        String best = "-";
        String rank = "-";
        if (instance != null) {
            var data = Parkour.getInstance().getRepository().get(room);
            if (data.scores.containsKey(player.getName())) {
                best = String.format("%.3f", data.scores.get(player.getName()));
                var ranking = Parkour.getInstance().getRepository().getRanking(room);
                for (int i = 0; i < ranking.size(); i++) {
                    if (ranking.get(i).getKey().equals(player.getName())) {
                        rank = String.valueOf(i + 1);
                        break;
                    }
                }
            }
        }
        LocalDate date = LocalDate.now();
        return new VariableContext(player.getName(), room, String.format("%.3f", time), String.valueOf(point),
                instance == null ? "0" : String.valueOf(instance.getData().routePoints.size()), best, rank,
                String.valueOf(Parkour.getInstance().getServer().getOnlinePlayers().size()),
                String.valueOf(Parkour.getInstance().getCurrentPlayingParkour().size()),
                Parkour.getInstance().getReplayManager().isReplaying(player) ? "true" : "false",
                instance != null && instance.isPaused(player) ? "true" : "false",
                String.valueOf(date.getYear()), String.format("%02d", date.getMonthValue()),
                String.format("%02d", date.getDayOfMonth()));
    }

    private String replaceVariables(String line, VariableContext context) {
        return line.replace("{player}", context.player).replace("{room}", context.room)
                .replace("{time}", context.time).replace("{point}", context.point)
                .replace("{total}", context.total).replace("{best}", context.best)
                .replace("{rank}", context.rank).replace("{online}", context.online)
                .replace("{playing}", context.playing).replace("{replay}", context.replay)
                .replace("{pause}", context.pause).replace("{year}", context.year)
                .replace("{month}", context.month).replace("{day}", context.day);
    }

    private record VariableContext(String player, String room, String time, String point, String total,
                                   String best, String rank, String online, String playing, String replay,
                                   String pause, String year, String month, String day) {
    }

    public void hide(Player player) {
        renderedLines.remove(player);
        Scoreboard board = boards.remove(player);
        if (board != null) {
            board.hideFor(player);
        }
    }
}
