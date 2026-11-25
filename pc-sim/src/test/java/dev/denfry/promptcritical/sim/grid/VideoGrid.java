package dev.denfry.promptcritical.sim.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Frequency of the 210 MW island of {@link GridProtectionTest#underFrequencyLoadSheddingSavesTheIsland} when one
 * of its three 100 MW machines drops off, for the reactor video (scenario.VideoData): the frequency falls, the
 * underfrequency stages shed load one by one, and the two machines left hold the island.
 */
public final class VideoGrid {
    private VideoGrid() {
    }

    public static String ufls() {
        GridProtectionTest.UflsIsland s = new GridProtectionTest.UflsIsland();
        double dt = GridProtectionTest.DT, tripAt = 2;
        List<String> pts = new ArrayList<>();
        List<String> sheds = new ArrayList<>();
        double[] now = new double[1];
        GridEventSink sink = new GridEventSink() {
            @Override
            public void loadSwitched(int load, boolean connected) {
                if (!connected) sheds.add(String.format(Locale.ROOT, "%.2f", now[0]));
            }
        };
        double fMin = 50;
        for (int k = 0; k <= (int) (40 / dt); k++) {
            now[0] = k * dt;
            if (k == (int) (tripAt / dt)) s.g.setBreaker(s.genBreakers.get(2), false);
            double f = s.step();
            fMin = Math.min(fMin, f);
            s.g.drainEvents(sink);
            pts.add(String.format(Locale.ROOT, "[%.2f,%.4f,%d]", now[0], f, sheds.size()));
        }
        return String.format(Locale.ROOT, "{\"tripAt\":%.1f,\"fMin\":%.3f,\"shedAt\":[%s],\"cols\":[\"t\",\"hz\",\"stagesShed\"],\"rows\":[",
                tripAt, fMin, String.join(",", sheds)) + String.join(",", pts) + "]}";
    }
}
