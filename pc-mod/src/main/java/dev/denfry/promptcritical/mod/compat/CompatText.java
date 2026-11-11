package dev.denfry.promptcritical.mod.compat;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Plant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Тексты интеграций (просмотрщик рецептов, подсказка при наведении): ключ → {английский, русский} для datagen. */
public final class CompatText {
    private static final String JEI = "jei." + PromptCritical.MOD_ID + ".";
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        plant(Plant.COAL_PREP, "Coal Preparation", "Углеобогащение");
        plant(Plant.POWER, "Boiler House", "Котельная");
        plant(Plant.WATER, "Water Treatment", "Водоподготовка");
        plant(Plant.FOUNDRY, "Foundry and Machine Shop", "Литейный и механический цех");
        plant(Plant.COKE, "Coke and By-Products", "Коксохимическое производство");
        plant(Plant.ELECTRODES, "Electrode Shop", "Электродное производство");
        plant(Plant.IRON, "Sinter Plant and Blast Furnace", "Аглофабрика и доменный цех");
        plant(Plant.STEEL, "Steel Shop", "Сталеплавильный цех");
        plant(Plant.ROLLING, "Rolling and Pipe Mills", "Прокатный и трубный цех");
        plant(Plant.AIR, "Oxygen Plant", "Кислородная станция");
        plant(Plant.MINE, "Mine", "Шахта");

        LANG.put("config.jade.plugin_" + PromptCritical.MOD_ID + ".hide_fluids", new String[] {
                "Hide tank levels of Prompt Critical machines", "Скрыть уровни баков машин Prompt Critical"});
        add("amount_by_mode", "Amount depends on the operating mode; shown nominally",
                "Количество зависит от режима; показано условно");
        add("info.raw_coal", "Mined from coal seams underground. Each piece carries a coal assay: volatiles, ash, "
                + "moisture and sulfur decide where it can go.",
                "Добывается из угольных пластов под землёй. У каждого куска — анализ угля: летучие, зола, влага и сера "
                + "решают, куда он годится.");

        process("coal_jig", "Run-of-mine coal is split by density into washed coal, middlings and refuse; the cut is set "
                + "with a Create wrench. Make-up water by pipe or bucket.",
                "Рядовой уголь делится по плотности на концентрат, промпродукт и породу; плотность разделения — ключом "
                + "Create. Подпиточная вода — трубой или ведром.");
        process("fire_tube_boiler", "Burns solid fuel on the grate and raises steam for the steam engine on a Create "
                + "shaft. Ash leaves as boiler slag; hard feed water builds scale.",
                "Сжигает твёрдое топливо на решётке и поднимает пар для паровой машины на валу Create. Зола уходит "
                + "шлаком; жёсткая питательная вода даёт накипь.");
        process("water_tube_boiler", "Fires coal dust or plant gas through burners under BMS interlocks and feeds "
                + "superheated steam to steam engines. Purge, igniter trial and main fuel are run from the panel.",
                "Сжигает угольную пыль или газ завода в горелках под защитами БМС и даёт перегретый пар паровым "
                + "машинам. Продувка, запальник и основное топливо — со щита.");
        process("deaerator", "Heats softened water with drum steam of a nearby I1 boiler to boiling and strips oxygen: "
                + "boiler feedwater at 104 °C.",
                "Греет умягчённую воду паром из барабана ближнего котла I1 до кипения и удаляет кислород: питательная "
                + "вода 104 °C.");
        process("water_clarifier", "Lime softening and settling of raw water. Needs burnt lime.",
                "Известкование и осветление сырой воды. Нужна известь.");
        process("sand_filter", "Removes suspended matter after the clarifier. Backwash with Shift + right click "
                + "with an empty hand.",
                "Убирает взвесь после осветлителя. Промывка — Shift + ПКМ пустой рукой.");
        process("ion_exchange_column", "Sodium cation exchange removes hardness. Regenerate the resin with salt.",
                "Na-катионирование снимает жёсткость. Смолу регенерируют солью.");
        process("molding_station", "Jolts moist foundry sand around a pattern on a Create shaft: one rammed sand mold "
                + "per cycle. The pattern is not consumed. Water 5 mB per sand item.",
                "Встряхиванием на валу Create уплотняет влажную смесь вокруг модели: за цикл одна форма. Модель не "
                + "расходуется. Вода — 5 мБ на предмет смеси.");
        process("cupola_furnace", "Charges of iron and coke, 100 kg per item, blown by a blower on a Create shaft. "
                + "Iron taps itself into a rammed mold at the foot. Redstone keeps the taphole closed.",
                "Колоша — металл и кокс по 100 кг на предмет, дутьё — воздуходувкой на валу Create. Чугун сам "
                + "выходит в форму у подножия. Сигнал красного камня держит лётку закрытой.");
        process("crucible_furnace", "Melts a copper, bronze or aluminium ingot (100 kg) over coal and pours it into the "
                + "mold in front. Redstone holds the crucible.",
                "Плавит слиток меди, бронзы или алюминия (100 кг) на угле и выливает в форму перед печью. Сигнал "
                + "красного камня держит тигель.");
        process("shakeout_grate", "Knocks cooled molds out on a Create shaft: the casting with its spec and 90 % of the "
                + "sand back.",
                "Выбивает остывшие формы на валу Create: отливка с паспортом и 90 % формовочной смеси обратно.");
        process("machine_tool", "Turns or drills a casting; the cutting tool wears with use.",
                "Точит или сверлит отливку; резец изнашивается.");
        process("casting_impeller", "A turned impeller casting without defects crafts into a pump impeller.",
                "Обточенная отливка рабочего колеса без дефектов — колесо насоса.");
        process("casting_bearing", "A turned bronze bearing housing crafts into two bearing shells. Porous, short-run "
                + "or chilled castings are rejected.",
                "Обточенный бронзовый корпус подшипника — два вкладыша. Пористая, с недоливом или отбелом отливка не "
                + "годится.");
        process("coke_oven", "Cokes the coal charge for hours of heating from the flues. Coke quality follows the coal "
                + "assay; gas goes to the collector main and the exhauster.",
                "Коксует загрузку часами нагрева из простенков. Качество кокса — по анализу угля; газ — в "
                + "газосборник и эксгаустер.");
        process("pitch_coke", "Coal tar pitch charged into an oven gives pitch coke, the filler for electrodes.",
                "Пек в печи даёт пековый кокс — наполнитель электродов.");
        process("quench_tower", "Hot coke is wet-quenched: 0.45 kg of water boils off per kg, coke leaves with 4 % "
                + "moisture.",
                "Горячий кокс тушат водой: 0,45 кг воды на кг уходит паром, кокс выходит с влагой 4 %.");
        process("byproduct_plant", "Cooler and precipitator catch tar, the saturator binds ammonia with sulfuric acid, "
                + "the scrubber takes benzol with wash oil. What is not caught stays in the gas.",
                "Холодильник и электрофильтр ловят смолу, сатуратор связывает аммиак серной кислотой, скруббер "
                + "берёт бензол поглотительным маслом. Неуловленное уходит с газом.");
        process("electrode_extruder", "Mixes pitch coke with pitch at the binder share set on the panel (25–30 %) and "
                + "presses a green electrode.",
                "Смешивает пековый кокс с пеком по доле связующего со щита (25–30 %) и прессует сырой электрод.");
        process("baking_furnace", "Bakes green electrodes on plant gas: 24 h above 800 °C. Unloading hot cracks them.",
                "Обжигает сырые электроды на газе: 24 ч выше 800 °C. Горячая выгрузка их трескает.");
        process("magnetic_separator", "Four pieces of magnetite per cycle: 85 % of the iron goes to concentrate "
                + "(66 % Fe), the rest to tailings. Hematite is not magnetic.",
                "Четыре куска магнетита за цикл: 85 % железа — в концентрат (66 % Fe), остальное — в хвосты. Гематит "
                + "не магнитен.");
        process("sinter_strand", "Ten items of ore or concentrate per cycle with coke breeze (45–70 kg/t) and flux "
                + "for the basicity set on the panel; ignited with plant gas.",
                "Десять предметов руды или концентрата за цикл, коксовая мелочь (45–70 кг/т) и известняк на "
                + "основность со щита; зажигание — газом завода.");
        process("blast_furnace", "Burden of sinter or ore, coke and flux through the top; hot blast from stoves. "
                + "The drill opens the taphole, the mud gun closes it with 2 taphole clay; iron runs to a ladle or "
                + "sand, slag to granulation.",
                "Шихта — агломерат или руда, кокс и известняк через засыпной аппарат; дутьё — из воздухонагревателей. "
                + "Бур вскрывает лётку, пушка забивает её 2 ед. глины; чугун — в ковш или на песок, шлак — в "
                + "грануляцию.");
        process("slag_granulator", "Liquid slag from the runner is quenched by a water jet into granulated slag.",
                "Жидкий шлак из жёлоба гасится струёй воды в гранулированный шлак.");
        process("bof_vessel", "Tilt forward to charge hot metal, scrap and lime; blow oxygen through the lance upright; "
                + "tilt back to tap steel into the ladle. Converter gas goes to its tank.",
                "Наклон вперёд — завалка чугуна, лома и извести; продувка кислородом через фурму в вертикали; наклон "
                + "назад — выпуск стали в ковш. Конвертерный газ — в бак.");
        process("ladle_station", "Ferroalloys deoxidize and alloy the steel in the ladle, argon through the plug stirs "
                + "and cools it. The panel shows the grade you will cast.",
                "Ферросплавы раскисляют и легируют сталь в ковше, аргон через пробку перемешивает и остужает. Щит "
                + "показывает марку при разливке.");
        process("ingot_mold", "A ladle above teems steel into the mold; the solid ingot comes out as hot steel ingots "
                + "and head crop as scrap. A refractory brick hot top cuts the crop from 17 % to 12 %.",
                "Ковш над изложницей разливает сталь; застывший слиток выходит горячими слитками, головная обрезь — "
                + "ломом. Огнеупорная надставка снижает обрезь с 17 % до 12 %.");
        process("reheating_furnace", "Heats ingots and billets to the setpoint (1150–1250 °C) and pushes them out onto "
                + "the roller table. Above 1300 °C the steel burns.",
                "Греет слитки и заготовки до уставки (1150–1250 °C) и выдаёт на рольганг. Выше 1300 °C — пережог.");
        process("rolling_mill_stand", "Each pass through the rolls with the gap set on the panel reduces the bar; the "
                + "product (billet, plate, rod, beam) follows the passes. Overload shears the pin: an iron nugget "
                + "is a new pin.",
                "Каждый пропуск через валки с зазором со щита обжимает раскат; продукт (заготовка, лист, пруток, "
                + "балка) — по пропускам. Перегруз срезает штифт: железный самородок — новый штифт.");
        process("piercing_mill", "A hot billet or ingot (1200–1250 °C) is pierced on a mandrel into seamless pipe, "
                + "1 m per piece, size and purpose from the panel.",
                "Горячая заготовка или слиток (1200–1250 °C) прошивается на оправке в бесшовную трубу по 1 м; "
                + "типоразмер и назначение — со щита.");
        process("pipe_welder", "Forms plate into a tube and welds the seam by high frequency; off-optimum speed or "
                + "power leaves hidden weld defects.",
                "Формует лист в трубу и варит шов ТВЧ; скорость и мощность не в оптимуме — скрытые дефекты шва.");
        process("heat_treatment", "Heats a charge of rolled steel or pipe to the setpoint, then quenches it in the "
                + "tank or cools it in air (tempering, normalizing, stress relief).",
                "Греет садку проката или труб до уставки, затем закалка в баке или охлаждение на воздухе (отпуск, "
                + "нормализация, снятие напряжений).");
        process("hydrotest_bench", "A cooled pipe is filled with water (20 mB) and pressed to 0.9 of yield: a burst pipe "
                + "is scrap, a sound one is marked HYDRO.",
                "Остывшую трубу заполняют водой (20 мБ) и нагружают до 0,9 предела текучести: лопнувшая — в лом, "
                + "выдержавшая — с отметкой HYDRO.");
        process("air_separator", "The rectification column splits air from the main compressor into nitrogen, oxygen "
                + "and argon; liquids go to a cryogenic tank. Near the coke plant the air carries acetylene.",
                "Ректификационная колонна делит воздух от основного компрессора на азот, кислород и аргон; жидкие "
                + "продукты — в криобак. У коксохима воздух несёт ацетилен.");
        process("mine_drainage", "Pumps mine water out of the sump to keep the workings dry.",
                "Откачивает шахтную воду из водосборника, чтобы не затопило выработки.");
        process("degasification", "Draws methane from the seam through boreholes before mining; the gas is fuel for "
                + "the plant.",
                "Отсасывает метан из пласта скважинами до выемки; газ — топливо завода.");
        process("recording_gauge", "Writes the measured value onto chart paper over time; the recorded chart is read in the chart "
                + "viewer.",
                "Пишет измеряемую величину на диаграммную бумагу во времени; записанную диаграмму читают в просмотрщике.");
    }

    private CompatText() {
    }

    private static void plant(Plant plant, String en, String ru) {
        LANG.put(plant.titleKey(), new String[] {en, ru});
    }

    private static void process(String id, String en, String ru) {
        LANG.put(JEI + "process." + id, new String[] {en, ru});
    }

    private static void add(String key, String en, String ru) {
        LANG.put(JEI + key, new String[] {en, ru});
    }
}
