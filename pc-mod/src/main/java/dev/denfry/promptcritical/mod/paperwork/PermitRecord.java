package dev.denfry.promptcritical.mod.paperwork;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.sim.grid.WorkPermit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Заполненный бланк наряда-допуска ({@link WorkPermit}): номер, аппарат, на котором создан разрыв, кто и когда выдал,
 * допустил к работе и закрыл наряд. Пустые имена — графа ещё не заполнена.
 *
 * @param number    номер по журналу учёта нарядов
 * @param stage     стадия наряда
 * @param target    аппарат (контроллер блока) в измерении
 * @param apparatus название аппарата
 * @param issuer    выдавший наряд
 * @param day       сутки выдачи
 * @param minute    минута выдачи
 * @param admittedBy допускающий; пусто — бригада не допущена
 * @param closedBy  закрывший наряд; пусто — открыт
 * @param duplicate закрыт по второму экземпляру (первый утерян)
 */
public record PermitRecord(int number, WorkPermit.Stage stage, GlobalPos target, Component apparatus, String issuer, long day,
        int minute, String admittedBy, String closedBy, boolean duplicate) {
    private static final Codec<WorkPermit.Stage> STAGE = Codec.STRING.xmap(WorkPermit.Stage::valueOf, WorkPermit.Stage::name);
    public static final Codec<PermitRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("number").forGetter(PermitRecord::number),
            STAGE.fieldOf("stage").forGetter(PermitRecord::stage),
            GlobalPos.CODEC.fieldOf("target").forGetter(PermitRecord::target),
            ComponentSerialization.CODEC.fieldOf("apparatus").forGetter(PermitRecord::apparatus),
            Codec.STRING.fieldOf("issuer").forGetter(PermitRecord::issuer),
            Codec.LONG.fieldOf("day").forGetter(PermitRecord::day),
            Codec.INT.fieldOf("minute").forGetter(PermitRecord::minute),
            Codec.STRING.optionalFieldOf("admitted_by", "").forGetter(PermitRecord::admittedBy),
            Codec.STRING.optionalFieldOf("closed_by", "").forGetter(PermitRecord::closedBy),
            Codec.BOOL.optionalFieldOf("duplicate", false).forGetter(PermitRecord::duplicate))
            .apply(i, PermitRecord::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PermitRecord> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public BlockPos pos() {
        return target.pos();
    }

    public PermitRecord admitted(String by) {
        return new PermitRecord(number, WorkPermit.Stage.ADMITTED, target, apparatus, issuer, day, minute, by, closedBy, duplicate);
    }

    public PermitRecord closed(String by) {
        return new PermitRecord(number, WorkPermit.Stage.CLOSED, target, apparatus, issuer, day, minute, admittedBy, by, duplicate);
    }
}
