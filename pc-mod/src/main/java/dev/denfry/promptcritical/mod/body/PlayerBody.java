package dev.denfry.promptcritical.mod.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Тело игрока в мире: профиль и признак «подтверждён». Пока игрок не подтвердил профиль, действует опорный
 * ({@link BodyProfile#REFERENCE}) и при входе открывается экран выбора.
 */
public record PlayerBody(BodyProfile profile, boolean confirmed) {
    public static final PlayerBody UNSET = new PlayerBody(BodyProfile.REFERENCE, false);

    /** Профиль из сохранения проходит те же границы, что и пакет: старое или правленое руками NBT не ломает мир. */
    public static final Codec<BodyProfile> PROFILE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("sex").forGetter(p -> p.sex().name()),
            Codec.INT.fieldOf("height_cm").forGetter(BodyProfile::heightCm),
            Codec.INT.fieldOf("mass_kg").forGetter(BodyProfile::massKg),
            Codec.INT.fieldOf("age").forGetter(BodyProfile::ageYears)
    ).apply(i, (sex, h, m, a) -> BodyProfile.clamped(sex(sex), h, m, a)));

    public static final Codec<PlayerBody> CODEC = RecordCodecBuilder.create(i -> i.group(
            PROFILE_CODEC.fieldOf("profile").forGetter(PlayerBody::profile),
            Codec.BOOL.fieldOf("confirmed").forGetter(PlayerBody::confirmed)
    ).apply(i, PlayerBody::new));

    /** Профиль в пакете: пол — порядковый номер, остальное — целые; сервер прижимает значения к диапазонам. */
    public static final StreamCodec<ByteBuf, BodyProfile> PROFILE_STREAM = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.sex().ordinal(),
            ByteBufCodecs.VAR_INT, BodyProfile::heightCm,
            ByteBufCodecs.VAR_INT, BodyProfile::massKg,
            ByteBufCodecs.VAR_INT, BodyProfile::ageYears,
            (sex, h, m, a) -> BodyProfile.clamped(sex(sex), h, m, a));

    public PlayerBody {
        if (profile == null) throw new IllegalArgumentException("profile");
    }

    static BodyProfile.Sex sex(int ordinal) {
        BodyProfile.Sex[] all = BodyProfile.Sex.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : BodyProfile.Sex.MALE;
    }

    static BodyProfile.Sex sex(String name) {
        for (BodyProfile.Sex s : BodyProfile.Sex.values()) if (s.name().equals(name)) return s;
        return BodyProfile.Sex.MALE;
    }
}
