package dev.denfry.promptcritical.mod.sound.client;

import dev.denfry.promptcritical.mod.sound.PcSfx;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Петля работы машины: звучит, пока её поддерживают ({@link #keep} хотя бы раз в {@link #HOLD_TICKS} тиков), громкость и
 * высота плавно идут к заданным; перестали поддерживать — затухает за четверть секунды и останавливается. Петли
 * сшиты в art/sfx/generate.py без щелчка, поэтому повтор файла не слышен.
 */
final class MachineLoopSound extends AbstractTickableSoundInstance {
    /** Без подтверждения дольше — машина остановилась или ушла из обхода. */
    static final int HOLD_TICKS = 10;
    private static final float FADE = 0.2f;

    final BlockPos pos;
    final PcSfx sfx;
    private float targetVolume, targetPitch;
    private int sinceKeep;

    MachineLoopSound(PcSfx sfx, BlockPos pos, float volume, float pitch) {
        super(sfx.get(), SoundSource.BLOCKS, RandomSource.create(pos.asLong()));
        this.sfx = sfx;
        this.pos = pos;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.volume = 0.01f;
        this.pitch = pitch;
        keep(volume, pitch);
    }

    void keep(float volume, float pitch) {
        targetVolume = volume;
        targetPitch = Math.max(0.5f, Math.min(2f, pitch));
        sinceKeep = 0;
    }

    @Override
    public void tick() {
        if (++sinceKeep > HOLD_TICKS) targetVolume = 0;
        volume += (targetVolume - volume) * FADE;
        pitch += (targetPitch - pitch) * FADE;
        if (targetVolume == 0 && volume < 0.01f) stop();
    }

    boolean done() {
        return isStopped();
    }
}
