package dev.standgen.stand;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Зберігає стенди всіх гравців у даних світу. */
public class StandManager extends PersistentState {
    private final Map<UUID, StandData> map = new HashMap<>();

    public static StandManager get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(StandManager::fromNbt, StandManager::new, "standgen_data");
    }

    public StandData get(UUID id) { return map.get(id); }

    public void put(UUID id, StandData d) { map.put(id, d); markDirty(); }

    public void remove(UUID id) { map.remove(id); markDirty(); }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, StandData> e : map.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("owner", e.getKey());
            c.put("data", e.getValue().toNbt());
            list.add(c);
        }
        nbt.put("stands", list);
        return nbt;
    }

    public static StandManager fromNbt(NbtCompound nbt) {
        StandManager m = new StandManager();
        NbtList list = nbt.getList("stands", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            m.map.put(c.getUuid("owner"), StandData.fromNbt(c.getCompound("data")));
        }
        return m;
    }
}
