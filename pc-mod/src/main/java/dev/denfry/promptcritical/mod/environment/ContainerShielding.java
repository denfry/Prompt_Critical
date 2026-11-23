package dev.denfry.promptcritical.mod.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.ShieldMaterial;
import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Собственный экран контейнера (R55): карта данных блока {@code promptcritical:container_shielding}. Материал —
 * имя {@link ShieldMaterial} (без учёта регистра), {@code cm} — толщина, см; слой стоит у самого источника, поверх
 * того, что набирает {@link ShieldBlocks#between}. Серверная карта, клиенту не нужна.
 */
public record ContainerShielding(String material, float cm) {
    public static final Codec<ContainerShielding> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("material").forGetter(ContainerShielding::material),
            Codec.FLOAT.fieldOf("cm").forGetter(ContainerShielding::cm)).apply(i, ContainerShielding::new));

    public static final DataMapType<Block, ContainerShielding> TYPE =
            DataMapType.builder(PromptCritical.id("container_shielding"), Registries.BLOCK, CODEC).build();

    public static void register(RegisterDataMapTypesEvent e) {
        e.register(TYPE);
    }

    /** Слой экрана; {@code null} — материал неизвестен или толщина не положительна (запись пропускается). */
    @Nullable
    public DoseRate.Layer layer() {
        if (cm <= 0) return null;
        try {
            ShieldMaterial m = ShieldMaterial.valueOf(material.toUpperCase(Locale.ROOT));
            return new DoseRate.Layer(m, cm, m.density);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
