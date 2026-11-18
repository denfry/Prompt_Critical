package dev.denfry.promptcritical.mod.worldgen;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Станция пробирного анализа {@code assay_station} (11 §11.4.2): кусок урановой смолки, приложенный к станции, даёт
 * изотопный состав урана. Природный — 0,720 % U-235; руда природного реактора — около 0,60 % и аномалия изотопов
 * неодима (продукты деления, текстом) — достижение {@code oklo}.
 */
public final class WorldFinds {
    /** Природная доля U-235, ppm атомов. */
    public static final int NATURAL_U235_PPM = 7200;
    /** Событие {@code sim_event}: анализ показал обеднённый уран; значение — доля U-235, %. */
    public static final String OKLO = "promptcritical:oklo";
    private static final String P = "message." + PromptCritical.MOD_ID + ".assay.";
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        m("hint", "Hold a piece of pitchblende and use the station", "Возьмите в руку кусок урановой смолки и кликните по станции");
        m("result", "U-235 %s %% (natural 0.720 %%), U-238 %s %%", "U-235 %s %% (природный 0,720 %%), U-238 %s %%");
        m("normal", "Isotope ratio is natural", "Изотопный состав природный");
        m("anomaly", "ANOMALY: uranium is depleted in U-235, and neodymium in the ore has the isotopes of fission products. "
                + "Here a chain reaction once ran in the ore itself", "АНОМАЛИЯ: уран обеднён по U-235, а в неодиме руды — изотопы осколков "
                + "деления. Когда-то в самой руде шла цепная реакция");
    }

    private WorldFinds() {
    }

    private static void m(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldFinds::onRightClick);
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!e.getLevel().getBlockState(e.getPos()).is(PcBlocks.ASSAY_STATION.get())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof ServerPlayer p)) return;
        assay(p, e.getItemStack());
    }

    /** Анализ куска в руке; {@code true} — показана аномалия. */
    public static boolean assay(ServerPlayer p, ItemStack stack) {
        if (!stack.is(PcItems.RAW_URANINITE.get())) {
            p.displayClientMessage(msg("hint"), true);
            return false;
        }
        int ppm = stack.getOrDefault(PcDataComponents.U235_PPM.get(), NATURAL_U235_PPM);
        double u235 = ppm / 1e4;
        p.displayClientMessage(msg("result", String.format(Locale.ROOT, "%.3f", u235), String.format(Locale.ROOT, "%.3f", 100 - u235 - 0.0055)), false);
        boolean depleted = ppm < NATURAL_U235_PPM - 300;
        p.displayClientMessage(depleted ? msg("anomaly").withStyle(ChatFormatting.GOLD) : msg("normal").withStyle(ChatFormatting.GRAY), false);
        if (depleted) SimAchievements.direct(p, OKLO, "u235_pct", u235);
        return depleted;
    }
}
