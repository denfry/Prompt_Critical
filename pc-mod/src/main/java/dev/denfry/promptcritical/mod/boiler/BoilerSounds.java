package dev.denfry.promptcritical.mod.boiler;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Звуки котельной — свистки и пар Create (обязательная зависимость мода); без них — ванильное шипение. */
final class BoilerSounds {
    private BoilerSounds() {
    }

    /** Свист предохранительного клапана. */
    static SoundEvent whistle() {
        return lookup("create:whistle_high");
    }

    /** Низкий свист выплавленной пробки. */
    static SoundEvent whistleLow() {
        return lookup("create:whistle_low");
    }

    /** Шипение струи пара. */
    static SoundEvent steam() {
        return lookup("create:steam");
    }

    private static SoundEvent lookup(String id) {
        SoundEvent s = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(id));
        return s != null ? s : SoundEvents.FIRE_EXTINGUISH;
    }
}
