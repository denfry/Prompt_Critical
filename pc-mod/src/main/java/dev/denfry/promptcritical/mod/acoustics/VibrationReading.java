package dev.denfry.promptcritical.mod.acoustics;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.acoustics.MachineClass;
import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.RandomSource;

/**
 * Замер виброметра, сервер → клиент: открыть экран прибора. Значения уже с погрешностью прибора.
 *
 * @param rpm      скорость вала машины, об/мин
 * @param powerKw  мощность на валу, кВт
 * @param noiseDb  уровень шума в точке замера, дБ(А) (NaN — тихо)
 */
public record VibrationReading(float rpm, float powerKw, MachineClass machineClass, float overall, float bandLowHz,
        List<Line> lines, float aHf, float bearingTempK, float noiseDb) implements CustomPacketPayload {
    public static final Type<VibrationReading> TYPE = new Type<>(PromptCritical.id("vibration_reading"));
    /** Погрешность прибора: класс 5 % от показания (04a §4a.8.1). */
    public static final double ERROR = 0.05;
    static final int MAX_LINES = 16;

    public record Line(VibrationSpectrum.Band band, float hz, float vRms) {
    }

    public static final StreamCodec<ByteBuf, VibrationReading> STREAM_CODEC = StreamCodec.of(VibrationReading::write,
            VibrationReading::read);

    public VibrationReading {
        lines = List.copyOf(lines);
    }

    private static void write(ByteBuf b, VibrationReading r) {
        b.writeFloat(r.rpm);
        b.writeFloat(r.powerKw);
        b.writeByte(r.machineClass.ordinal());
        b.writeFloat(r.overall);
        b.writeFloat(r.bandLowHz);
        b.writeByte(r.lines.size());
        for (Line l : r.lines) {
            b.writeByte(l.band.ordinal());
            b.writeFloat(l.hz);
            b.writeFloat(l.vRms);
        }
        b.writeFloat(r.aHf);
        b.writeFloat(r.bearingTempK);
        b.writeFloat(r.noiseDb);
    }

    private static VibrationReading read(ByteBuf b) {
        float rpm = b.readFloat(), kw = b.readFloat();
        MachineClass c = MachineClass.values()[Math.floorMod(b.readByte(), MachineClass.values().length)];
        float overall = b.readFloat(), low = b.readFloat();
        int n = Math.min(b.readUnsignedByte(), MAX_LINES);
        List<Line> lines = new ArrayList<>(n);
        VibrationSpectrum.Band[] bands = VibrationSpectrum.Band.values();
        for (int i = 0; i < n; i++) lines.add(new Line(bands[Math.floorMod(b.readByte(), bands.length)], b.readFloat(), b.readFloat()));
        return new VibrationReading(rpm, kw, c, overall, low, lines, b.readFloat(), b.readFloat(), b.readFloat());
    }

    /** Замер спектра прибором: каждая величина — с погрешностью ±{@link #ERROR}. */
    public static VibrationReading measure(VibrationSpectrum s, double powerW, double noiseDb, RandomSource rnd) {
        List<Line> lines = new ArrayList<>();
        for (VibrationSpectrum.Line l : s.lines()) lines.add(new Line(l.band(), (float) l.hz(), (float) (l.vRms() * err(rnd))));
        return new VibrationReading((float) s.shaftRpm(), (float) (powerW / 1000), s.machineClass(),
                (float) (s.overallRms() * err(rnd)), (float) s.bandLowHz(), lines, (float) (s.aHf() * err(rnd)),
                (float) s.bearingTempK(), Double.isFinite(noiseDb) ? (float) noiseDb : Float.NaN);
    }

    private static double err(RandomSource rnd) {
        return 1 + ERROR * (2 * rnd.nextDouble() - 1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
