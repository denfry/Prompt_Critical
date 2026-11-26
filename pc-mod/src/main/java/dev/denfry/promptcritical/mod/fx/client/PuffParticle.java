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
    private final float size0;
    private final float alpha0;

    PuffParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean smoke) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.965f;
        this.gravity = smoke ? -0.0015f : -0.004f;
        this.hasPhysics = false;
        this.lifetime = 160 + random.nextInt(160);
        this.size0 = 0.8f + random.nextFloat() * 0.7f;
        this.quadSize = size0;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        float g = smoke ? 0.30f + random.nextFloat() * 0.12f : 0.86f + random.nextFloat() * 0.1f;
        setColor(g, g * (smoke ? 0.96f : 1f), g * (smoke ? 0.92f : 1f));
        this.alpha0 = smoke ? 0.75f : 0.55f;
        this.alpha = alpha0;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float a = (float) age / lifetime;
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

    public record Provider(SpriteSet sprites, boolean smoke) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new PuffParticle(level, x, y, z, vx, vy, vz, sprites, smoke);
        }
    }
}
