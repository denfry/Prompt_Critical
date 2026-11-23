package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Флаг профиля licensing мира: берётся из {@code licensing.enabled} один раз, при первой загрузке мира, и дальше не
 * меняется — переключить профиль на живом мире нельзя (спецификация профиля, §7).
 */
public final class LicensingWorldData extends SavedData {
    private static final String NAME = PromptCritical.MOD_ID + "_licensing";
    private boolean enabled;

    private static LicensingWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        LicensingWorldData d = new LicensingWorldData();
        d.enabled = tag.getBoolean("enabled");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    /**
     * Флаг мира; если его ещё нет — решает {@link #decide} и записывает решение. Мир, сохранённый до профиля, флага не
     * имеет, но время в нём уже идёт: он остаётся без licensing, иначе профиль включился бы посреди игры, а прежний
     * прогресс маршрута в реестр не попал бы.
     */
    public static boolean resolve(MinecraftServer server, boolean configValue) {
        return resolve(server.overworld(), configValue);
    }

    /** То же по уровню: флаг хранится в верхнем мире (при его загрузке он ещё может не быть в списке сервера). */
    public static boolean resolve(ServerLevel level, boolean configValue) {
        ServerLevel overworld = level.dimension() == Level.OVERWORLD ? level : level.getServer().overworld();
        var storage = overworld.getDataStorage();
        LicensingWorldData d = storage.get(new SavedData.Factory<>(LicensingWorldData::new, LicensingWorldData::load, null), NAME);
        if (d != null) return d.enabled;
        d = new LicensingWorldData();
        d.enabled = decide(configValue, overworld.getGameTime());
        d.setDirty();
        storage.set(NAME, d);
        return d.enabled;
    }

    /** Профиль берётся из конфига только новым миром (игровое время ещё 0). */
    public static boolean decide(boolean configValue, long gameTime) {
        return configValue && gameTime == 0;
    }
}
