package cn.daoge.parkour.storage;

import cn.ElysianArena.ElyDataCore.ElyDataCore;
import cn.ElysianArena.ElyDataCore.api.generic.GenericDataManager;
import net.easecation.ghosty.recording.player.PlayerRecord;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ElyParkourRepository {
    private final GenericDataManager<ParkourRecordData> manager;
    private final Map<String, ParkourRecordData> cache = new HashMap<>();

    public ElyParkourRepository() {
        manager = ElyDataCore.getInstance().getDataAPI()
                .genericManager("parkour_records", "room", ParkourRecordData.class);
        manager.init();
    }

    public ParkourRecordData get(String room) {
        return cache.computeIfAbsent(room,
                key -> manager.getOrDefault(key, () -> new ParkourRecordData(key)));
    }

    public List<Map.Entry<String, Double>> getRanking(String room) {
        return get(room).scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(15)
                .toList();
    }

    public boolean submit(String room, String player, double time, PlayerRecord replay) {
        ParkourRecordData data = get(room);
        Double previous = data.scores.get(player);
        if (previous != null && previous <= time) {
            return false;
        }
        data.scores.put(player, time);
        List<Map.Entry<String, Double>> ranking = new ArrayList<>(data.scores.entrySet());
        ranking.sort(Comparator.comparingDouble(Map.Entry::getValue));
        if (ranking.size() > 15) {
            ranking.subList(15, ranking.size()).forEach(entry -> {
                data.scores.remove(entry.getKey());
                data.replays.remove(entry.getKey());
            });
        }
        boolean ranked = data.scores.containsKey(player);
        if (ranked && replay != null) {
            data.replays.put(player, Base64.getEncoder().encodeToString(replay.toBinary()));
        }
        manager.save(data);
        cache.put(room, data);
        return ranked;
    }

    public PlayerRecord getReplay(String room, String player) {
        String encoded = get(room).replays.get(player);
        return encoded == null ? null : PlayerRecord.fromBinary(Base64.getDecoder().decode(encoded));
    }

    public boolean hasReplays(String room) {
        return !get(room).replays.isEmpty();
    }

    public boolean deletePlayer(String room, String player) {
        ParkourRecordData data = get(room);
        List<String> scoreKeys = data.scores.keySet().stream()
                .filter(name -> name.equalsIgnoreCase(player))
                .toList();
        List<String> replayKeys = data.replays.keySet().stream()
                .filter(name -> name.equalsIgnoreCase(player))
                .toList();
        if (scoreKeys.isEmpty() && replayKeys.isEmpty()) return false;
        scoreKeys.forEach(data.scores::remove);
        replayKeys.forEach(data.replays::remove);
        manager.save(data);
        cache.put(room, data);
        return true;
    }
}
