package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.grid.FeInterface;
import dev.denfry.promptcritical.sim.grid.FeParity;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Граница комплекса с Forge Energy (07 §7.7, 16 §16.7.1, R56): курс джоулей на FE с учётом паритета SU↔FE и паспорт
 * интерфейса по ключам конфигурации. Физика комплекса в ваттах; меняется только цена FE на границе.
 */
public final class FeEnergy {
    /** Моды-мосты вала Create и FE с курсом 480 FE/т на 16 384 SU (Crafts & Additions, New Age). */
    public static final List<String> BRIDGE_MODS = List.of("createaddition", "create_new_age");

    private FeEnergy() {
    }

    public static boolean bridgeLoaded() {
        ModList mods = ModList.get();
        return mods != null && BRIDGE_MODS.stream().anyMatch(mods::isLoaded);
    }

    /** Джоулей на FE на границе: при мосте и {@code fe_su_parity = auto} — 2·W_PER_SU, иначе {@code j_per_fe}. */
    public static double jPerFe() {
        if (ServerConfig.FE_SU_PARITY.get() == ServerConfig.FeSuParity.AUTO && bridgeLoaded())
            return FeParity.parityJPerFe(ServerConfig.W_PER_SU.get());
        return ServerConfig.J_PER_FE.get();
    }

    /** Номинал варианта интерфейса: 0 — малый, 1 — стандартный, 2 — большой, Вт. */
    public static double ratedW(int variant) {
        List<? extends Double> r = ServerConfig.GRID_FE_RATINGS_W.get();
        double[] fallback = {1e5, 1e6, 1e7};
        return variant >= 0 && variant < r.size() ? r.get(variant) : fallback[Math.max(0, Math.min(2, variant))];
    }

    public static FeInterface.Spec spec(int variant) {
        return new FeInterface.Spec(ratedW(variant), ServerConfig.GRID_FE_EFFICIENCY.get(), ServerConfig.GRID_FE_F_MIN.get(),
                ServerConfig.GRID_FE_F_MAX.get(), ServerConfig.GRID_FE_V_MIN.get(), ServerConfig.GRID_FE_V_MAX.get());
    }

    /** При старте сервера: усиления петель SU↔FE по курсам загруженного моста, предупреждение, если петля прибыльна. */
    public static void onServerStarted(ServerStartedEvent event) {
        if (!bridgeLoaded()) return;
        double w = ServerConfig.W_PER_SU.get(), j = jPerFe(), eta = ServerConfig.GRID_FE_EFFICIENCY.get();
        double g = FeParity.forwardGain(w, j, eta, FeParity.BRIDGE_MOTOR_FE_T_PER_SU);
        double gr = FeParity.reverseGain(w, j, eta, FeParity.BRIDGE_GENERATOR_FE_T_PER_SU);
        String gains = String.format(Locale.ROOT, "G = %.3f, G' = %.3f at %.0f J/FE and %.0f W/SU", g, gr, j, w);
        if (g > 1 || gr > 1)
            PromptCritical.LOGGER.warn("SU-FE bridge loaded and an energy loop gains energy ({}); set energy.fe_su_parity = auto "
                    + "or align the bridge rates (16 §16.7.1)", gains);
        else PromptCritical.LOGGER.info("SU-FE bridge loaded, both energy loops lose energy ({})", gains);
    }
}
