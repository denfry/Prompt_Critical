package dev.denfry.promptcritical.mod.sound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Звуки, привязанные к слушателю (16 §16.4.4): треск дозиметра в руке игрока. Без позиции Sound Physics Remastered
 * их не обрабатывает (отдаёт среду по умолчанию), так что в шахте щелчок не получает реверберацию зала. Всё, чему нужны
 * окклюзия и реверберация, играется позиционно.
 */
public final class ListenerSounds {
    private ListenerSounds() {}

    public static void play(SoundEvent sound, SoundSource source, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), source, volume, pitch,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }
}
