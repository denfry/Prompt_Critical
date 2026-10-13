package dev.denfry.promptcritical.sim.archfixture;

import net.minecraft.core.BlockPos;

/** Нарушитель правила noMinecraft. */
public final class LeakyMinecraftUser {
    public int height(BlockPos pos) {
        return pos.y();
    }
}
