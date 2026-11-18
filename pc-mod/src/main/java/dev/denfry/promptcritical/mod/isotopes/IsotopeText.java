package dev.denfry.promptcritical.mod.isotopes;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения изотопов и РИТЭГ (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class IsotopeText {
    private static final String P = "message.promptcritical.isotopes.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("capsule_failure", "CAPSULE FAILURE in the irradiation channel: %s °C, the target leaks into the channel",
                "РАЗГЕРМЕТИЗАЦИЯ КАПСУЛЫ в канале облучения: %s °C, мишень вышла в канал");
        put("camera_loaded", "Source loaded into the camera: %s", "Источник заряжен в дефектоскоп: %s");
        put("camera_unloaded", "Source taken out of the camera", "Источник извлечён из дефектоскопа");
        put("camera_empty", "The camera has no source: sneak + use with a sealed Ir-192 or Co-60 source in the off hand",
                "В дефектоскопе нет источника: Shift + ПКМ с закрытым источником Ir-192 или Co-60 во второй руке");
        put("camera_needs", "Put a pipe in the off hand and a film in the inventory", "Нужна труба во второй руке и плёнка в инвентаре");
        put("camera_exposure", "Source out · exposure %s %% of %s s · release to wind it back",
                "Источник выдвинут · экспозиция %s %% из %s с · отпустите, чтобы вернуть его");
        put("film_good", "Film developed: %s", "Плёнка проявлена: %s");
        put("film_under", "Film underexposed: nothing to see", "Плёнка недодержана: ничего не видно");
        put("film_over", "Film overexposed: black, nothing to see", "Плёнка передержана: чёрная, ничего не видно");
        put("film_clean", "weld is clean", "шов без дефектов");
        put("film_defect", "DEFECT in the weld: %s", "ДЕФЕКТ шва: %s");
        put("rtg_overtemp", "RTG overheating: hot junction %s °C", "Перегрев РИТЭГ: горячий спай %s °C");
        put("category", "IAEA category %s (A/D = %s)", "Категория МАГАТЭ %s (A/D = %s)");
        put("target", "%s, %s g · product %s · fluence %s cm⁻²", "%s, %s г · продукт %s · флюенс %s см⁻²");
        put("target_leaking", "Capsule failed: not sealed", "Капсула разгерметизирована");
        put("shielded", "Source in the container: %s GBq %s · %s at 1 m outside", "Источник в контейнере: %s ГБк %s · %s на 1 м снаружи");
        put("burnup", "Burnup %s MWd/t U", "Выгорание %s МВт·сут/т U");
        put("film_result_none", "Film unusable: %s", "Плёнка негодна: %s");
        put("film_under_short", "underexposed", "недодержка");
        put("film_over_short", "overexposed", "передержка");
        put("film_of", "Weld of a %s pipe: %s", "Шов трубы %s: %s");
        defect("NONE", "no defect", "нет дефекта");
        defect("LACK_OF_FUSION", "lack of fusion", "непровар");
        defect("POROSITY", "porosity", "поры");
        defect("HOOK_CRACK", "hook crack", "крючковая трещина");
        defect("LAMINATION", "lamination", "расслоение");
        defect("ECCENTRICITY", "wall eccentricity", "разностенность");
        defect("INCLUSION_LAP", "inclusion lap", "плена");
        defect("SHRINKAGE", "shrinkage cavity", "усадочная раковина");
        material("COBALT_METAL", "cobalt", "кобальт");
        material("IRIDIUM_METAL", "iridium", "иридий");
        material("NEPTUNIUM_OXIDE", "neptunium dioxide", "диоксид нептуния");
    }

    private IsotopeText() {
    }

    private static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    private static void defect(String kind, String en, String ru) {
        put("defect." + kind.toLowerCase(java.util.Locale.ROOT), en, ru);
    }

    private static void material(String m, String en, String ru) {
        put("material." + m.toLowerCase(java.util.Locale.ROOT), en, ru);
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static MutableComponent defect(String kind) {
        return msg("defect." + kind.toLowerCase(java.util.Locale.ROOT));
    }

    public static MutableComponent material(String m) {
        return msg("material." + m.toLowerCase(java.util.Locale.ROOT));
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }

    /** Подсказки предметов: категория МАГАТЭ, мишень капсулы, источник в контейнере, плёнка, выгорание блочков. */
    public static void registerTooltips() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) -> {
            if (e.getEntity() == null) return;
            var stack = e.getItemStack();
            var lines = e.getToolTip();
            long now = e.getEntity().level().getGameTime();
            var a = stack.get(dev.denfry.promptcritical.mod.environment.ActivityData.ACTIVITY.get());
            if (a != null) category(lines, a.inventory(now));
            var sh = stack.get(IsotopeComponents.SHIELDED_SOURCE.get());
            if (sh != null) {
                var inv = sh.inventory(now);
                lines.add(msg("shielded", fmt(inv.totalBq() / 1e9), String.join("+", sh.bq().keySet()),
                        dev.denfry.promptcritical.mod.environment.DosimeterItem.rate(
                                inv.doseRateSvH(1) * dev.denfry.promptcritical.mod.environment.RadiationService.CONTAINER_TRANSMISSION))
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                category(lines, inv);
            }
            var t = stack.get(IsotopeComponents.IRRADIATION_TARGET.get());
            if (t != null) {
                var m = t.materialOrNull();
                if (m != null && t.atoms().size() == m.chain.size()) {
                    double[] atoms = t.atomsAt(now);
                    lines.add(msg("target", material(m.name()), fmt(t.massKg() * 1000), m.product.name() + " " + fmt(m.activityBq(atoms, m.productLink) / 1e9)
                            + " GBq", fmt(t.fluence())).withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                if (!t.sealed()) lines.add(msg("target_leaking").withStyle(net.minecraft.ChatFormatting.RED));
            }
            var f = stack.get(IsotopeComponents.FILM_RESULT.get());
            if (f != null) {
                var q = dev.denfry.promptcritical.sim.isotopes.Radiography.Film.values()[Math.floorMod(f.quality(), 3)];
                if (q == dev.denfry.promptcritical.sim.isotopes.Radiography.Film.GOOD) {
                    var d = dev.denfry.promptcritical.sim.metallurgy.DefectKind.values()[Math.floorMod(f.defectKind(),
                            dev.denfry.promptcritical.sim.metallurgy.DefectKind.values().length)];
                    lines.add(msg("film_of", f.pipe(), RadiographyCameraItem.filmText(d)));
                } else {
                    lines.add(msg("film_result_none", msg(q == dev.denfry.promptcritical.sim.isotopes.Radiography.Film.UNDEREXPOSED
                            ? "film_under_short" : "film_over_short")).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                }
            }
            Double bu = stack.get(IsotopeComponents.BURNUP.get());
            if (bu != null) lines.add(msg("burnup", fmt(bu * 1000)).withStyle(net.minecraft.ChatFormatting.GRAY));
        });
    }

    private static void category(java.util.List<Component> lines, dev.denfry.promptcritical.sim.radiation.ActivityInventory inv) {
        double r = dev.denfry.promptcritical.sim.isotopes.IaeaCategory.ratio(inv);
        if (r <= 0) return;
        int c = dev.denfry.promptcritical.sim.isotopes.IaeaCategory.of(r);
        lines.add(msg("category", c, fmt(r)).withStyle(c <= 2 ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.YELLOW));
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3g", v);
    }
}
