package cn.daoge.parkour.storage;

import cn.ElysianArena.ElyDataCore.api.generic.DataEntity;

import java.util.HashMap;
import java.util.Map;

public class ParkourRecordData implements DataEntity {
    public String room;
    public Map<String, Double> scores = new HashMap<>();
    public Map<String, String> replays = new HashMap<>();

    public ParkourRecordData() {
    }

    public ParkourRecordData(String room) {
        this.room = room;
    }

    @Override
    public String getId() {
        return room;
    }
}
