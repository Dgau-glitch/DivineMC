package org.bxteam.divinemc.util.tps;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TPSCalculator {
    public Long lastTick;
    public Long currentTick;
    private double allMissedTicks = 0;
    private final List<Double> tpsHistory = new CopyOnWriteArrayList<>();
    private static final int historyLimit = 40;

    public static final int MAX_TPS = 20;
    public static final int MIN_APPLICABLE_TPS = 18;
    public static final int FULL_TICK = 50;
    public static final int MAX_CATCHUP_TICKS_PER_TICK = 0;

    public TPSCalculator() {}

    public void doTick() {
        if (currentTick != null) {
            lastTick = currentTick;
        }

        currentTick = System.currentTimeMillis();
        addToHistory(getTPS());
        clearMissedTicks();
        missedTick();
    }

    private void addToHistory(double tps) {
        if (tpsHistory.size() >= historyLimit) {
            tpsHistory.remove(0);
        }

        tpsHistory.add(tps);
    }

    public long getMSPT() {
        return currentTick - lastTick;
    }

    public double getAverageTPS() {
        return tpsHistory.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(MAX_TPS);
    }

    public double getTPS() {
        if (lastTick == null) return MAX_TPS;
        if (getMSPT() <= 0) return MAX_TPS;

        double tps = 1000 / (double) getMSPT();
        if (tps > MAX_TPS) {
            return MAX_TPS;
        }
        return Math.max(1.0D, tps);
    }

    public void missedTick() {
        if (lastTick == null) return;

        long mspt = getMSPT() <= 0 ? 50 : getMSPT();
        double missedTicks = (mspt / (double) FULL_TICK) - 1;
        allMissedTicks += missedTicks <= 0 ? 0 : missedTicks;
    }

    public double getMostAccurateTPS() {
        return Math.max(MIN_APPLICABLE_TPS, Math.min(getTPS(), getAverageTPS()));
    }

    public double getAllMissedTicks() {
        return allMissedTicks;
    }

    private int pendingWholeMissedTicks() {
        return (int) Math.floor(allMissedTicks);
    }

    public int applicableMissedTicks() {
        return Math.min(MAX_CATCHUP_TICKS_PER_TICK, pendingWholeMissedTicks());
    }

    public void clearMissedTicks() {
        allMissedTicks -= pendingWholeMissedTicks();
    }

    public void resetMissedTicks() {
        allMissedTicks = 0;
    }
}
