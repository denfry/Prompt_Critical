package dev.denfry.promptcritical.mod.fx.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Клуб пара или дыма: начинается с полутора-двух блоков, за жизнь (8–16 с) раздувается вчетверо и тает, скорость
 * гаснет трением воздуха; пар чуть всплывает, дым — почти нет. Один из четырёх рисунков клуба, повёрнутый случайно.
 */
public final class PuffParticle extends TextureSheetParticle {
    public enum Kind { STEAM, SMOKE, FIRE }

    private final float size0;
    private final float alpha0;
    private final Kind kind;

    PuffParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        boolean smoke = kind == Kind.SMOKE;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.965f;
        this.gravity = smoke ? -0.0015f : -0.004f;
        this.hasPhysics = false;
        this.lifetime = 160 + random.nextInt(160);
        this.size0 = kind == Kind.FIRE ? 1.2f + random.nextFloat() : 0.8f + random.nextFloat() * 0.7f;
        this.quadSize = size0;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        float g = smoke ? 0.30f + random.nextFloat() * 0.12f : 0.86f + random.nextFloat() * 0.1f;
        setColor(g, g * (smoke ? 0.96f : 1f), g * (smoke ? 0.92f : 1f));
        this.alpha0 = smoke ? 0.75f : 0.55f;
        this.alpha = alpha0;
        if (kind == Kind.FIRE) {
            // a burning gas front: short, swelling, glowing on its own
            this.lifetime = 18 + random.nextInt(22);
            this.gravity = -0.01f;
            this.friction = 0.9f;
            this.alpha = 0.95f;
            setColor(1f, 0.95f, 0.75f);
        }
        pickSprite(sprites);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return kind == Kind.FIRE ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        float a = (float) age / lifetime;
        if (kind == Kind.FIRE) {
            // yellow-white → orange → dark red as the gas burns out
            quadSize = size0 * (1 + 2.5f * (float) Math.sqrt(a));
            setColor(1f, Mth.lerp(a, 0.95f, 0.25f), Mth.lerp(Math.min(1, a * 2), 0.75f, 0.05f));
            alpha = 0.95f * (a < 0.4f ? 1 : 1 - (a - 0.4f) / 0.6f);
            return;
        }
        quadSize = size0 * (1 + 3 * (float) Math.sqrt(a));
        // fades in over the first tenth and out over the last half
        alpha = alpha0 * Math.min(1, a * 10) * (a < 0.5f ? 1 : 1 - (a - 0.5f) * 2);
        oRoll = roll;
        roll += 0.004f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new PuffParticle(level, x, y, z, vx, vy, vz, sprites, kind);
        }
    }
}
