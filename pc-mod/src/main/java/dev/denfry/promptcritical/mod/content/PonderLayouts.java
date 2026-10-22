package dev.denfry.promptcritical.mod.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Сцены Ponder эпохи I1 и автоматики (11 §11.1.4, сцены 28–31 и 36): раскладки схем ({@code assets/promptcritical/ponder/
 * <сцена>.nbt} пишет datagen) и тексты сцен на двух языках. Схема — плита из бетона на y = 0 и установка над ней;
 * сцены показывают её по частям и меняют состояния блоков. Классы общие: раскладку читает datagen (сервер), сцены —
 * клиент.
 */
public final class PonderLayouts {
    public static final String COKE = "coke_battery_cycle", BLAST_FURNACE = "blast_furnace_tap",
            SYNC = "generator_synchronization", FREQUENCY = "grid_frequency", RELAY = "relay_ladder";

    /** Схема сцены: размер плиты {@code size}×{@code size}, высота {@code height}, блоки над плитой (состояние строкой). */
    public record Layout(String scene, int size, int height, Map<BlockPos, String> blocks) {
    }

    /** Заголовок и реплики сцены: {en, ru}; порядок реплик — порядок вызовов в сцене (ключи text_1…). */
    public record Texts(String[] header, List<String[]> lines) {
        public String en(int line) {
            return lines.get(line - 1)[0];
        }
    }

    public static final Map<String, Layout> LAYOUTS = new LinkedHashMap<>();
    public static final Map<String, Texts> TEXTS = new LinkedHashMap<>();

    // --- коксовая батарея: простенки x = 1, 3, 5, 7, камеры x = 2, 4, 6, вдоль z 2…6, высота 3; газосборник на y = 4
    public static final int COKE_Z0 = 2, COKE_Z1 = 6;
    public static final BlockPos COKE_DOOR = new BlockPos(4, 2, 7);
    // --- домна: кольцо 5×5 (x, z 2…6), шахта y 1…7, лётка на юге, фурмы на y = 2, жёлоб на юг от лётки
    public static final BlockPos BF_TAPHOLE = new BlockPos(4, 1, 6), BF_TOP = new BlockPos(4, 8, 4);
    public static final BlockPos[] BF_TUYERES = {new BlockPos(2, 2, 4), new BlockPos(6, 2, 4), new BlockPos(4, 2, 2)};
    public static final BlockPos[] BF_RUNNER = {new BlockPos(4, 1, 7), new BlockPos(4, 1, 8)};
    // --- генератор: стенка щита z = 0, синхроноскоп на ней, генератор с валом Create, ячейка КРУ
    public static final BlockPos SYNC_GENERATOR = new BlockPos(3, 1, 4), SYNC_SCOPE = new BlockPos(3, 2, 1),
            SYNC_CELL = new BlockPos(1, 1, 4), SYNC_EXCITER = new BlockPos(2, 1, 4);
    // --- частота: генератор и два потребителя 3×3×3 с модулями АЧР
    public static final BlockPos FREQ_GENERATOR = new BlockPos(2, 1, 4);
    public static final BlockPos[] FREQ_CONSUMERS = {new BlockPos(6, 1, 2), new BlockPos(6, 1, 6)};
    // --- релейная лестница на стенке z = 0: блок питания, СТОП, ПУСК, K1, K2, лампа
    public static final BlockPos RL_PSU = new BlockPos(1, 2, 1), RL_STOP = new BlockPos(2, 2, 1), RL_START = new BlockPos(3, 2, 1),
            RL_K1 = new BlockPos(4, 2, 1), RL_K2 = new BlockPos(5, 2, 1), RL_LAMP = new BlockPos(6, 2, 1);

    static {
        Map<BlockPos, String> b = new LinkedHashMap<>();
        for (int x = 1; x <= 7; x++) {
            for (int z = COKE_Z0; z <= COKE_Z1; z++)
                for (int y = 1; y <= 3; y++) b.put(new BlockPos(x, y, z), x % 2 == 1 ? pc("coke_battery_wall") : pc("coke_oven"));
            b.put(new BlockPos(x, 4, 4), pc("gas_collector_main"));
        }
        add(COKE, 9, 6, b);

        b = new LinkedHashMap<>();
        for (int y = 1; y <= 7; y++)
            for (int x = 2; x <= 6; x++)
                for (int z = 2; z <= 6; z++) {
                    if (x != 2 && x != 6 && z != 2 && z != 6) continue;
                    b.put(new BlockPos(x, y, z), pc("blast_furnace_shell"));
                }
        b.put(BF_TAPHOLE, pc("blast_furnace_taphole") + "[facing=south,open=false]");
        b.put(BF_TUYERES[0], pc("blast_furnace_tuyere") + "[facing=west]");
        b.put(BF_TUYERES[1], pc("blast_furnace_tuyere") + "[facing=east]");
        b.put(BF_TUYERES[2], pc("blast_furnace_tuyere") + "[facing=north]");
        b.put(BF_TOP, pc("blast_furnace_top"));
        for (BlockPos p : BF_RUNNER) b.put(p, pc("iron_runner"));
        add(BLAST_FURNACE, 9, 10, b);

        b = new LinkedHashMap<>();
        for (int x = 0; x <= 6; x++) for (int y = 1; y <= 3; y++) b.put(new BlockPos(x, y, 0), pc("painted_concrete_two_tone"));
        b.put(SYNC_SCOPE, pc("instrument_synchroscope") + "[facing=south]");
        b.put(SYNC_GENERATOR, pc("shaft_generator") + "[facing=west]");
        b.put(SYNC_EXCITER, pc("generator_exciter") + "[facing=west]");
        for (int x = 4; x <= 6; x++) b.put(new BlockPos(x, 1, 4), "create:shaft[axis=x]");
        b.put(SYNC_CELL, pc("switchgear_cell") + "[facing=east,open=true]");
        add(SYNC, 7, 5, b);

        b = new LinkedHashMap<>();
        b.put(FREQ_GENERATOR, pc("shaft_generator") + "[facing=west]");
        for (int x = 3; x <= 4; x++) b.put(new BlockPos(x, 1, 4), "create:shaft[axis=x]");
        for (BlockPos p : FREQ_CONSUMERS) b.put(p, pc("grid_consumer") + "[facing=west,lit=true]");
        add(FREQUENCY, 9, 5, b);

        b = new LinkedHashMap<>();
        for (int x = 0; x <= 7; x++) for (int y = 1; y <= 3; y++) b.put(new BlockPos(x, y, 0), pc("painted_concrete"));
        b.put(RL_PSU, pc("relay_power_supply") + "[facing=south,powered=true]");
        b.put(RL_STOP, pc("pushbutton") + "[facing=south]");
        b.put(RL_START, pc("pushbutton") + "[facing=south]");
        b.put(RL_K1, pc("relay") + "[facing=south]");
        b.put(RL_K2, pc("relay") + "[facing=south]");
        b.put(RL_LAMP, pc("indicator_lamp") + "[facing=south,lit=false]");
        add(RELAY, 8, 4, b);

        texts(COKE, new String[] {"The Coke Oven Battery", "Коксовая батарея"},
                "Narrow coking chambers alternate with heating flue walls of silica brick",
                "Узкие камеры коксования чередуются с отопительными простенками из динаса",
                "Coal is charged from the top. Coking time grows with the square of the chamber width",
                "Уголь загружают сверху. Время коксования растёт как квадрат ширины камеры",
                "Coke oven gas rises into the collecting main and goes on to the exhausters and the by-product plant",
                "Коксовый газ поднимается в газосборник и уходит к эксгаустерам и в цех улавливания",
                "A coked chamber is emptied by a pusher ram mounted on a Create contraption",
                "Готовую камеру освобождает выталкивающая штанга на контрапции Create",
                "Red-hot coke must be quenched at once, or it burns away in the air",
                "Раскалённый кокс сразу тушат, иначе он сгорит на воздухе");
        texts(BLAST_FURNACE, new String[] {"Tapping the Blast Furnace", "Выпуск чугуна из домны"},
                "A blast furnace is a ring of shell blocks 5 wide and 12 to 18 high; this one is cut short",
                "Домна — кольцо кожуха шириной 5 и высотой 12–18 блоков; здесь показана укороченной",
                "Coke, sinter and limestone go in at the top in layers",
                "Кокс, агломерат и известняк загружают сверху слоями",
                "Hot blast through the tuyeres burns the coke; the carbon monoxide reduces the iron ore",
                "Горячее дутьё через фурмы сжигает кокс, оксид углерода восстанавливает железо из руды",
                "Iron and slag gather in the hearth. The cast house drill opens the taphole",
                "Чугун и шлак собираются в горне. Бур машины литейного двора вскрывает лётку",
                "Iron runs along the runner into a ladle; the mud gun then plugs the taphole with clay",
                "Чугун идёт по жёлобу в ковш, затем пушка забивает лётку лёточной массой");
        texts(SYNC, new String[] {"Synchronising a Generator", "Включение генератора в сеть"},
                "The generator turns with a Create shaft; the exciter supplies its field current",
                "Генератор вращается валом Create, возбудитель даёт ток возбуждения",
                "Before the breaker closes, voltage, frequency and phase of the generator must match the bus",
                "Перед включением выключателя напряжение, частота и фаза генератора должны совпасть с шинами",
                "The synchroscope pointer turns with the slip: clockwise means the generator is fast",
                "Стрелка синхроноскопа вращается со скольжением: по часовой — генератор быстрее сети",
                "Close the breaker when the pointer turns slowly and comes up to twelve o'clock",
                "Включайте, когда стрелка вращается медленно и подходит к «12 часам»",
                "A clean closing keeps the torque shock within 10 %; far out of phase it damages the shaft or shears the coupling",
                "Чистое включение — бросок момента не больше 10 %; грубо не в фазе — повреждение вала или срез муфты");
        texts(FREQUENCY, new String[] {"Grid Frequency and Load Shedding", "Частота сети и АЧР"},
                "Frequency is the same across the whole grid and holds while generation equals the load",
                "Частота одна на всю сеть и держится, пока выработка равна нагрузке",
                "When load exceeds generation, the rotors slow down and the frequency falls",
                "Если нагрузка больше выработки, роторы замедляются и частота падает",
                "Under-frequency load shedding modules in the consumers disconnect them stage by stage",
                "Модули автоматической частотной разгрузки в потребителях отключают их очередями",
                "With part of the load gone, the frequency recovers; the rest of the grid stays in service",
                "Без части нагрузки частота восстанавливается, остальная сеть остаётся в работе",
                "When the frequency is back, frequency auto-reclose returns the shed consumers",
                "Когда частота вернулась, ЧАПВ включает отключённых потребителей обратно");
        texts(RELAY, new String[] {"Relay Ladder Logic", "Релейная лестничная логика"},
                "A relay circuit: a 24 V supply, buttons, relays and a lamp, wired terminal to terminal",
                "Релейная схема: блок питания 24 В, кнопки, реле и лампа, соединённые проводами от клеммы к клемме",
                "Contacts in series make AND, contacts in parallel make OR",
                "Контакты последовательно — это «И», параллельно — «ИЛИ»",
                "Pressing START energises the coil of K1",
                "Нажатие ПУСК подаёт ток на катушку K1",
                "A K1 contact in parallel with START keeps the coil energised: this is the seal-in",
                "Контакт K1 параллельно кнопке ПУСК держит катушку: это самоподхват",
                "K2 follows K1 and lights the lamp through its contact",
                "K2 повторяет K1 и через свой контакт зажигает лампу",
                "The normally closed STOP button breaks the circuit and everything drops out",
                "Нормально замкнутая кнопка СТОП разрывает цепь, и всё отпадает");
    }

    private PonderLayouts() {
    }

    /** Ключ перевода реплики Ponder ({@code header} или {@code text_N}). */
    public static String langKey(String scene, String key) {
        return "promptcritical.ponder." + scene + "." + key;
    }

    private static String pc(String id) {
        return "promptcritical:" + id;
    }

    private static void add(String scene, int size, int height, Map<BlockPos, String> blocks) {
        LAYOUTS.put(scene, new Layout(scene, size, height, blocks));
    }

    private static void texts(String scene, String[] header, String... enRu) {
        List<String[]> lines = new java.util.ArrayList<>();
        for (int i = 0; i < enRu.length; i += 2) lines.add(new String[] {enRu[i], enRu[i + 1]});
        TEXTS.put(scene, new Texts(header, List.copyOf(lines)));
    }
}
