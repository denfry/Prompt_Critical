package dev.denfry.promptcritical.mod.pa;

/**
 * Фразы громкой связи. Тексты — источник для озвучки ({@code art/voice/generate.py} разбирает
 * этот файл, поэтому одна константа — одна строка) и для субтитров.
 */
public enum PaLine {
    BOILER_PURGE_STARTED("boiler.purge_started", false, "Boiler. Furnace purge in progress.", "Котёл. Начата вентиляция топки."),
    BOILER_PURGE_COMPLETE("boiler.purge_complete", false, "Boiler. Purge complete. Ready to light off.", "Котёл. Вентиляция топки завершена. Розжиг разрешён."),
    BOILER_ON_LINE("boiler.on_line", false, "Boiler. Main flame proven. Boiler on line.", "Котёл. Основной факел устойчив. Котёл в работе."),
    BOILER_SHUTDOWN("boiler.shutdown", false, "Boiler shut down by the operator.", "Котёл остановлен оператором."),
    BOILER_SAFETY_VALVE("boiler.safety_valve", false, "Boiler. Safety valve lifted.", "Котёл. Сработал предохранительный клапан."),
    BOILER_IGNITER_FAILED("boiler.igniter_failed", true, "Attention! Boiler. Igniter failed to light. Main fuel trip.", "Внимание! Котёл. Запальник не зажёгся. Отсечка топлива."),
    BOILER_MAIN_FLAME_FAILED("boiler.main_flame_failed", true, "Attention! Boiler. Main flame failed to ignite. Main fuel trip.", "Внимание! Котёл. Основной факел не зажёгся. Отсечка топлива."),
    BOILER_FLAME_LOSS("boiler.flame_loss", true, "Attention! Boiler. Flame failure. Main fuel trip.", "Внимание! Котёл. Погасание факела. Отсечка топлива."),
    BOILER_FANS_LOST("boiler.fans_lost", true, "Attention! Boiler. Draft fans lost. Main fuel trip.", "Внимание! Котёл. Останов тягодутьевых машин. Отсечка топлива."),
    BOILER_DRUM_LEVEL_LOW("boiler.drum_level_low", true, "Alarm! Boiler. Drum level low low. Boiler tripped.", "Авария! Котёл. Упуск воды в барабане. Котёл остановлен защитой."),
    BOILER_DRUM_LEVEL_HIGH("boiler.drum_level_high", true, "Alarm! Boiler. Drum level high high. Boiler tripped.", "Авария! Котёл. Перепитка барабана. Котёл остановлен защитой."),
    BOILER_STEAM_PRESSURE_HIGH("boiler.steam_pressure_high", true, "Alarm! Boiler. Steam pressure high. Boiler tripped.", "Авария! Котёл. Давление пара выше допустимого. Котёл остановлен защитой."),
    BOILER_FURNACE_PRESSURE_HIGH("boiler.furnace_pressure_high", true, "Alarm! Boiler. Furnace pressure high. Boiler tripped.", "Авария! Котёл. Повышение давления в топке. Котёл остановлен защитой."),
    BOILER_FURNACE_PRESSURE_LOW("boiler.furnace_pressure_low", true, "Alarm! Boiler. Furnace draft too deep. Boiler tripped.", "Авария! Котёл. Глубокое разрежение в топке. Котёл остановлен защитой."),
    BOILER_FUEL_PRESSURE_LOW("boiler.fuel_pressure_low", true, "Alarm! Boiler. Fuel pressure low. Boiler tripped.", "Авария! Котёл. Давление топлива ниже допустимого. Котёл остановлен защитой."),
    BOILER_FLUE_CO_HIGH("boiler.flue_co_high", true, "Alarm! Boiler. Carbon monoxide high in the flue gas. Boiler tripped.", "Авария! Котёл. Угарный газ в уходящих газах. Котёл остановлен защитой."),
    BOILER_TUBE_LEAK("boiler.tube_leak", true, "Alarm! Boiler. Tube rupture. Boiler tripped.", "Авария! Котёл. Разрыв трубы. Котёл остановлен защитой."),
    GEN_SYNCHRONIZED("gen.synchronized", false, "Generator synchronized to the grid.", "Генератор включён в сеть."),
    GEN_OUT_OF_PHASE("gen.out_of_phase", true, "Attention! Generator closed out of phase. Shaft shock.", "Внимание! Несинхронное включение генератора. Удар на валу."),
    GEN_SHEAR_PIN("gen.shear_pin", true, "Attention! Out of phase closing. Shear pin broken.", "Внимание! Несинхронное включение. Срезной штифт разрушен."),
    GEN_POLE_SLIP("gen.pole_slip", true, "Alarm! Generator lost synchronism.", "Авария! Генератор вышел из синхронизма."),
    GEN_TRIPPED("gen.tripped", true, "Alarm! Generator tripped.", "Авария! Генератор отключён защитой."),
    GRID_FREQ_LOW("grid.freq_low", true, "Attention! Grid frequency low.", "Внимание! Частота в сети понижена."),
    GRID_FREQ_HIGH("grid.freq_high", true, "Attention! Grid frequency high.", "Внимание! Частота в сети повышена."),
    GRID_FREQ_NORMAL("grid.freq_normal", false, "Grid frequency back to normal.", "Частота в сети восстановлена.");

    /** Языки, для которых есть озвучка; остальным игрокам звучит первый. */
    public static final String[] VOICED = {"en_us", "ru_ru"};

    public final String key;
    /** Тревога: перед фразой звучит сигнал, а не гонг. */
    public final boolean alarm;
    public final String en;
    public final String ru;

    PaLine(String key, boolean alarm, String en, String ru) {
        this.key = key;
        this.alarm = alarm;
        this.en = en;
        this.ru = ru;
    }

    public String subtitleKey() {
        return "subtitles.promptcritical.pa." + key;
    }
}
