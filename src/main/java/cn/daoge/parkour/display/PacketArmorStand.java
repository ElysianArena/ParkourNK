package cn.daoge.parkour.display;

import cn.daoge.parkour.Parkour;
import cn.nukkit.Player;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.item.EntityArmorStand;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.RemoveEntityPacket;
import cn.nukkit.network.protocol.types.EntityLink;
import cn.nukkit.network.protocol.types.PropertySyncData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PacketArmorStand {
    private static final List<PacketArmorStand> ACTIVE = new ArrayList<>();
    private static boolean initialized;

    private final long entityId = allocateEntityId();
    private final Level level;
    private final Position position;
    private final String text;
    private final Set<UUID> viewers = new HashSet<>();
    private boolean closed;

    private PacketArmorStand(Level level, Position position, String text) {
        this.level = level;
        this.position = position;
        this.text = text.replace('&', '§');
    }

    public static void initialize(Parkour plugin) {
        if (initialized) return;
        initialized = true;
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, PacketArmorStand::syncAll, 20);
    }

    public static PacketArmorStand spawn(Level level, Position position, String text) {
        PacketArmorStand stand = new PacketArmorStand(level, position, text);
        ACTIVE.add(stand);
        stand.sync();
        return stand;
    }

    public static void shutdown() {
        new ArrayList<>(ACTIVE).forEach(PacketArmorStand::close);
        ACTIVE.clear();
        initialized = false;
    }

    private static void syncAll() {
        new ArrayList<>(ACTIVE).forEach(PacketArmorStand::sync);
    }

    private void sync() {
        if (closed) return;
        Set<UUID> onlineInLevel = new HashSet<>();
        for (Player player : level.getPlayers().values()) {
            if (!player.isOnline()) continue;
            onlineInLevel.add(player.getUniqueId());
            if (viewers.add(player.getUniqueId())) sendSpawn(player);
        }
        viewers.removeIf(uuid -> {
            if (onlineInLevel.contains(uuid)) return false;
            Parkour.getInstance().getServer().getPlayer(uuid)
                    .filter(Player::isOnline)
                    .ifPresent(this::sendRemove);
            return true;
        });
    }

    private void sendSpawn(Player player) {
        long flags = (1L << Entity.DATA_FLAG_NO_AI)
                | (1L << Entity.DATA_FLAG_CAN_SHOW_NAMETAG)
                | (1L << Entity.DATA_FLAG_ALWAYS_SHOW_NAMETAG)
                | (1L << Entity.DATA_FLAG_IMMOBILE)
                | (1L << Entity.DATA_FLAG_SILENT);
        EntityMetadata metadata = new EntityMetadata()
                .putLong(Entity.DATA_FLAGS, flags)
                .putFloat(Entity.DATA_BOUNDING_BOX_HEIGHT, 0.01f)
                .putFloat(Entity.DATA_BOUNDING_BOX_WIDTH, 0.01f)
                .putFloat(Entity.DATA_SCALE, 0.01f)
                .putLong(Entity.DATA_LEAD_HOLDER_EID, -1)
                .putByte(Entity.DATA_ALWAYS_SHOW_NAMETAG, 1)
                .putString(Entity.DATA_NAMETAG, text)
                ;
        AddEntityPacket packet = new AddEntityPacket();
        packet.entityUniqueId = entityId;
        packet.entityRuntimeId = entityId;
        packet.type = EntityArmorStand.NETWORK_ID;
        packet.id = "minecraft:armor_stand";
        packet.x = (float) position.x;
        packet.y = (float) position.y;
        packet.z = (float) position.z;
        packet.metadata = metadata;
        packet.attributes = new Attribute[]{
                Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(100).setValue(100)
        };
        packet.links = new EntityLink[0];
        packet.properties = new PropertySyncData(new int[0], new float[0]);
        player.dataPacket(packet);
    }

    private static synchronized long allocateEntityId() {
        return Entity.entityCount++;
    }

    private void sendRemove(Player player) {
        RemoveEntityPacket packet = new RemoveEntityPacket();
        packet.eid = entityId;
        player.dataPacket(packet);
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (UUID uuid : new HashSet<>(viewers)) {
            Parkour.getInstance().getServer().getPlayer(uuid)
                    .filter(Player::isOnline)
                    .ifPresent(this::sendRemove);
        }
        viewers.clear();
        ACTIVE.remove(this);
    }
}
