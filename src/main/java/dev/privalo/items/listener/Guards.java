package dev.privalo.items.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Short-lived flags that stop actions from re-triggering their own events. */
public final class Guards {

    private final Set<UUID> areaBreaking = new HashSet<>();
    private final Set<UUID> actionDamage = new HashSet<>();
    private final Set<UUID> safeExplosion = new HashSet<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();

    public void areaBreaking(UUID player, boolean active) {
        if (active) {
            areaBreaking.add(player);
        } else {
            areaBreaking.remove(player);
        }
    }

    public boolean areaBreaking(UUID player) {
        return areaBreaking.contains(player);
    }

    public void actionDamage(UUID player, boolean active) {
        if (active) {
            actionDamage.add(player);
        } else {
            actionDamage.remove(player);
        }
    }

    public boolean actionDamage(UUID player) {
        return actionDamage.contains(player);
    }

    public void safeExplosion(UUID player, boolean active) {
        if (active) {
            safeExplosion.add(player);
        } else {
            safeExplosion.remove(player);
        }
    }

    public boolean safeExplosion(UUID player) {
        return safeExplosion.contains(player);
    }

    public void noFall(UUID player, long ticks) {
        long until = System.currentTimeMillis() + ticks * 50L;
        noFallUntil.merge(player, until, Math::max);
    }

    /** True (and consumed) while a NO_FALL / GRAPPLE window is active. */
    public boolean consumeNoFall(UUID player) {
        Long until = noFallUntil.get(player);
        if (until == null) {
            return false;
        }
        noFallUntil.remove(player);
        return until >= System.currentTimeMillis();
    }

    public void clear(UUID player) {
        areaBreaking.remove(player);
        actionDamage.remove(player);
        safeExplosion.remove(player);
        noFallUntil.remove(player);
    }
}
