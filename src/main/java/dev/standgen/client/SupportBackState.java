package dev.standgen.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Клієнтський стан: у яких гравців ввімкнено стенд-на-спині (форма Підтримка) і які в нього кольори. */
public final class SupportBackState {
    public record Data(int c1, int c2, int stage, int style, int kind) {}

    private static final Map<UUID, Data> MAP = new ConcurrentHashMap<>();

    private SupportBackState() {}

    public static void set(UUID id, Data d) {
        if (d == null) MAP.remove(id); else MAP.put(id, d);
    }

    public static Data get(UUID id) { return MAP.get(id); }

    public static void clear() { MAP.clear(); }
}
