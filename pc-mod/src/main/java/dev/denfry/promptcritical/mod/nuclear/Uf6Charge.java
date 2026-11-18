package dev.denfry.promptcritical.mod.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Содержимое баллона UF₆: масса гексафторида, кг, и атомные доли U-235, U-234 и U-236 в уране (U-236 — только у
 * регенерированного урана; необязательное поле, старые баллоны читаются с нулём). Природный уран — 0,711 % и
 * 0,0054 % (Benedict, Pigford, Levi, «Nuclear Chemical Engineering», 2nd ed., 1981, табл. 12.1).
 */
public record Uf6Charge(double uf6Kg, double u235, double u234, double u236) {
    public static final double NATURAL_U235 = 0.00711, NATURAL_U234 = 0.000054;
    public static final Uf6Charge EMPTY = new Uf6Charge(0, 0, 0, 0);
    /** Атомная масса U-236, г/моль. */
    public static final double M236 = 236.0456;
    /** Атомные массы изотопов урана и фтора, г/моль. */
    public static final double M235 = 235.0439, M234 = 234.0410, M238 = 238.0508, M_F = 18.9984;

    public static final Codec<Uf6Charge> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("uf6_kg").forGetter(Uf6Charge::uf6Kg),
            Codec.DOUBLE.fieldOf("u235").forGetter(Uf6Charge::u235),
            Codec.DOUBLE.fieldOf("u234").forGetter(Uf6Charge::u234),
            Codec.DOUBLE.optionalFieldOf("u236", 0.0).forGetter(Uf6Charge::u236)).apply(i, Uf6Charge::new));
    public static final StreamCodec<ByteBuf, Uf6Charge> STREAM = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Uf6Charge::uf6Kg, ByteBufCodecs.DOUBLE, Uf6Charge::u235, ByteBufCodecs.DOUBLE, Uf6Charge::u234,
            ByteBufCodecs.DOUBLE, Uf6Charge::u236, Uf6Charge::new);

    public Uf6Charge {
        if (!(uf6Kg >= 0) || !(u235 >= 0) || !(u234 >= 0) || !(u236 >= 0) || u235 + u234 + u236 > 1)
            throw new IllegalArgumentException("bad UF6 charge: " + uf6Kg + " kg, U-235 " + u235 + ", U-234 " + u234 + ", U-236 " + u236);
    }

    /** Уран без U-236 (природный и обогащённый из природного). */
    public Uf6Charge(double uf6Kg, double u235, double u234) {
        this(uf6Kg, u235, u234, 0);
    }

    public static Uf6Charge natural(double uf6Kg) {
        return new Uf6Charge(uf6Kg, NATURAL_U235, NATURAL_U234);
    }

    public boolean empty() {
        return uf6Kg <= 0;
    }

    /** Средняя атомная масса урана такого состава, г/моль. */
    public static double uraniumMolarMass(double u235, double u234) {
        return uraniumMolarMass(u235, u234, 0);
    }

    public static double uraniumMolarMass(double u235, double u234, double u236) {
        return u235 * M235 + u234 * M234 + u236 * M236 + (1 - u235 - u234 - u236) * M238;
    }

    /** Моли урана (= моли UF₆). */
    public double moles() {
        return uf6Kg * 1000 / (uraniumMolarMass(u235, u234, u236) + 6 * M_F);
    }

    /** Масса урана, кг. */
    public double uraniumKg() {
        return moles() * uraniumMolarMass(u235, u234, u236) / 1000;
    }

    /** Смешение: массы складываются, доли изотопов усредняются по молям урана. */
    public Uf6Charge plus(Uf6Charge o) {
        if (o.empty()) return this;
        if (empty()) return o;
        double a = moles(), b = o.moles();
        return new Uf6Charge(uf6Kg + o.uf6Kg, (u235 * a + o.u235 * b) / (a + b), (u234 * a + o.u234 * b) / (a + b),
                (u236 * a + o.u236 * b) / (a + b));
    }

    /** Часть того же состава массой {@code kg} (не больше имеющегося). */
    public Uf6Charge part(double kg) {
        return new Uf6Charge(Math.max(0, Math.min(kg, uf6Kg)), u235, u234, u236);
    }

    public Uf6Charge minus(double kg) {
        double left = uf6Kg - kg;
        return left <= 1e-9 ? EMPTY : new Uf6Charge(left, u235, u234, u236);
    }
}
