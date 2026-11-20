"""Route of the later eras (11 §11.1.5): writes handbook/route/{i2,i3,i4,t}.json and their texts into handbook/lang.

    python art/tools/route_late.py            (from the repo root)

Tasks use obtain/build/discover only: the sim events a `reach` task listens to exist for I0 and I1 so far. Every task
names its tech tree branch, the chapter it opens and an item or block that exists (ContentGameTests.routeIsConsistent
checks all of it). Texts: what to do (text) and how (hint, shown on TRAINING), in Russian and English.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
HB = os.path.join(ROOT, "pc-mod", "src", "main", "resources", "data", "promptcritical", "handbook")
P = "promptcritical:"

ERAS = {
    "i2": dict(era="I2", sort=20, title=("Oil and Gas", "Нефть и газ"), branches=[
        ("drilling", "Drilling and production", "Бурение и добыча"),
        ("refining", "Refining", "Переработка"),
        ("gas", "Gas", "Газ"),
        ("control", "Plant control", "АСУ ТП"),
        ("logistics", "Pipelines and market", "Трубопроводы и рынок")]),
    "i3": dict(era="I3", sort=30, title=("Chemistry", "Химия"), branches=[
        ("organic", "Organics", "Органика"),
        ("inorganic", "Inorganics", "Неорганика"),
        ("energy", "Power and electronics", "Энергия и электроника")]),
    "i4": dict(era="I4", sort=40, title=("Nuclear Fuel", "Ядерное топливо"), branches=[
        ("ore", "Ore and enrichment", "Руда и обогащение"),
        ("fuel", "Fuel", "Топливо"),
        ("radiation", "Radiation", "Радиация"),
        ("backend", "Closing the cycle", "Замыкание цикла")]),
    "t": dict(era="T", sort=50, title=("Reactors", "Реакторы"), branches=[
        ("t1", "T1 First NPP", "T1 Первая АЭС"),
        ("t2", "T2 Light water", "T2 Лёгкая вода"),
        ("t3", "T3 Heavy water", "T3 Тяжёлая вода"),
        ("t4", "T4 Fast reactor", "T4 Быстрый реактор")]),
}

# (file, id, branch, requires, kind, target, count, entry, xp, title (en, ru), text (en, ru), hint (en, ru))
# kind: obtain (item), build (block placed), discover (block mined)
T = []


def t(f, tid, branch, req, kind, target, count, entry, xp, title, text, hint):
    T.append((f, tid, branch, req, kind, target, count, entry, xp, title, text, hint))


# ------------------------------------------------------------------------------------------------ I2 oil and gas
D = "i2/drilling"
t("i2", "seismic_charge", "drilling", ["i1/power_line", "i1/steel_plate"], "obtain", "seismic_charge", 4, "i2/seismic", 15,
  ("Seismic charges", "Сейсмические заряды"),
  ("Oil is found by listening to the ground: a charge sends a wave down, the layers send it back.",
   "Нефть ищут на слух: заряд посылает волну вниз, пласты отражают её обратно."),
  ("Make four charges; the survey chapter shows how far apart to fire them.",
   "Сделайте четыре заряда; глава о разведке объясняет, на каком расстоянии их взрывать."))
t("i2", "geophone", "drilling", ["i2/seismic_charge"], "build", "geophone", 4, "i2/seismic", 15,
  ("Lay out the geophones", "Расставьте сейсмоприёмники"),
  ("Geophones in a line pick up the echo from each layer.", "Сейсмоприёмники в линию ловят эхо от каждого пласта."),
  ("Put four geophones in a straight line, a few blocks apart.", "Поставьте четыре сейсмоприёмника по прямой в нескольких блоках друг от друга."))
t("i2", "seismic_recorder", "drilling", ["i2/geophone"], "build", "seismic_recorder", 1, "i2/seismic", 20,
  ("Seismic station", "Сейсмостанция"),
  ("The station records the geophones and turns the echoes into a section of the ground.",
   "Сейсмостанция записывает приёмники и превращает эхо в разрез земли."),
  ("Place it at the end of the geophone line.", "Поставьте её в конце линии приёмников."))
t("i2", "seismic_section", "drilling", ["i2/seismic_recorder"], "obtain", "seismic_section", 1, "i2/seismic", 30,
  ("A seismic section", "Сейсмический разрез"),
  ("A section shows where the layers bend up into a trap: that is where to drill.",
   "Разрез показывает, где пласты выгибаются ловушкой: там и бурить."),
  ("Fire the charges with the station recording, then take the section from it.",
   "Взорвите заряды, пока станция пишет, и заберите из неё разрез."))
t("i2", "bitumen", "drilling", ["i1/first_steel"], "discover", "natural_bitumen", 1, "i2/seismic", 15,
  ("Find a bitumen seep", "Найдите выход битума"),
  ("Tar on the surface means oil once migrated up here.", "Битум на поверхности значит, что нефть когда-то поднималась здесь."),
  ("Look in low ground and along river banks; mine one block of it.",
   "Ищите в низинах и по берегам рек; добудьте один блок."))
t("i2", "gas_seep", "drilling", ["i1/first_steel"], "discover", "gas_seep_vent", 1, "i2/seismic", 15,
  ("Find a gas seep", "Найдите выход газа"),
  ("Gas bubbling out of the ground marks a field below.", "Газ, выходящий из земли, выдаёт залежь под ней."),
  ("Seeps hiss and bubble in water; break one to record it.", "Выходы шипят и пузырят в воде; сломайте один, чтобы отметить."))
t("i2", "derrick", "drilling", ["i2/seismic_section"], "build", "derrick_segment", 3, D, 25,
  ("Raise the derrick", "Поставьте вышку"),
  ("The derrick carries the drill string over the hole.", "Вышка держит бурильную колонну над скважиной."),
  ("Stack three derrick segments over the spot the section picked.",
   "Поставьте три секции вышки над точкой, которую выбрал разрез."))
t("i2", "rotary_table", "drilling", ["i2/derrick"], "build", "rotary_table", 1, D, 15,
  ("Rotary table", "Ротор"),
  ("The rotary table turns the string; Create shaft power drives it.", "Ротор вращает колонну; его крутит вал Create."),
  ("Place it at the foot of the derrick and bring a shaft to it.", "Поставьте его у основания вышки и подведите вал."))
t("i2", "drawworks", "drilling", ["i2/derrick"], "build", "drawworks", 1, D, 15,
  ("Drawworks", "Буровая лебёдка"),
  ("The drawworks lifts and lowers the string and sets the weight on the bit.",
   "Лебёдка поднимает и опускает колонну и задаёт нагрузку на долото."),
  ("Place it beside the derrick.", "Поставьте её рядом с вышкой."))
t("i2", "mud_pump", "drilling", ["i2/derrick"], "build", "mud_pump", 1, D, 15,
  ("Mud pump", "Буровой насос"),
  ("Mud carries the cuttings up and holds the well's pressure back.",
   "Раствор выносит шлам и сдерживает давление пласта."),
  ("Drive the pump from a shaft and pipe it to the rotary table.", "Крутите насос валом и подведите трубу к ротору."))
t("i2", "mud_tanks", "drilling", ["i2/mud_pump"], "build", "mud_tank", 3, D, 15,
  ("Mud tanks", "Ёмкости раствора"),
  ("Pit volume is your kick alarm: the level rises when the well flows.",
   "Объём ёмкостей — сигнал о притоке: уровень растёт, когда скважина пошла."),
  ("Place three tanks in a row near the pump.", "Поставьте три ёмкости в ряд у насоса."))
t("i2", "rig", "drilling", ["i2/rotary_table", "i2/drawworks", "i2/mud_tanks"], "build", "drillers_console", 1, D, 40,
  ("The rig is complete", "Буровая собрана"),
  ("The driller's console sees the whole rig and starts the hole.", "Пульт бурильщика видит всю буровую и начинает скважину."),
  ("Place the console within reach of the rotary table; it lists what is missing.",
   "Поставьте пульт рядом с ротором; он перечислит, чего не хватает."))
t("i2", "bop", "drilling", ["i2/rig"], "build", "bop_annular", 1, "i2/well_control", 30,
  ("Blowout preventer", "Превентор"),
  ("A preventer closes the well when gas comes up: without it a kick becomes a blowout.",
   "Превентор закрывает скважину, когда идёт газ: без него приток становится фонтаном."),
  ("Mount it on the wellhead under the rotary table.", "Поставьте его на устье под ротором."))
t("i2", "bop_accumulator", "drilling", ["i2/bop"], "build", "bop_accumulator", 1, "i2/well_control", 20,
  ("Accumulator unit", "Аккумуляторная установка"),
  ("The accumulator keeps pressure to shut the preventer even with the power gone.",
   "Аккумулятор держит давление, чтобы закрыть превентор и без электричества."),
  ("Place it next to the preventer.", "Поставьте её рядом с превентором."))
t("i2", "choke", "drilling", ["i2/bop"], "build", "choke_manifold", 1, "i2/well_control", 20,
  ("Choke manifold", "Штуцерный манифольд"),
  ("With the well shut, the choke bleeds the gas out at a pressure you control.",
   "Когда скважина закрыта, штуцер стравливает газ под давлением, которое вы держите."),
  ("Pipe it to the preventer's side outlet.", "Подведите его к боковому отводу превентора."))
t("i2", "logging_unit", "drilling", ["i2/rig"], "build", "logging_unit", 1, "i2/well_logging", 20,
  ("Logging unit", "Каротажный подъёмник"),
  ("Logging lowers sondes down the hole and reads the rocks along it.", "Каротаж опускает зонды в скважину и читает породы."),
  ("Place it next to the rig.", "Поставьте его рядом с буровой."))
t("i2", "gamma_sonde", "drilling", ["i2/logging_unit"], "obtain", "gamma_logging_sonde", 1, "i2/well_logging", 15,
  ("Gamma sonde", "Зонд гамма-каротажа"),
  ("Shale glows in gamma, sand does not: the log finds the reservoir.", "Глины светятся в гамма, песок нет: каротаж находит пласт."),
  ("Make the sonde and load it into the logging unit.", "Сделайте зонд и заправьте его в подъёмник."))
t("i2", "core", "drilling", ["i2/rig"], "obtain", "reservoir_core", 1, "i2/well_logging", 20,
  ("Take a core", "Отберите керн"),
  ("A core is a piece of the reservoir in your hands: porosity and oil saturation.",
   "Керн — кусок пласта в руках: пористость и нефтенасыщенность."),
  ("Run the core barrel into the hole at the reservoir depth.", "Спустите керноотборник на глубину пласта."))
t("i2", "core_station", "drilling", ["i2/core"], "build", "core_analysis_station", 1, "i2/well_logging", 15,
  ("Core analysis", "Анализ керна"),
  ("The station measures the core and says how much oil the field holds.",
   "Станция измеряет керн и говорит, сколько нефти в залежи."),
  ("Place it and put the core in.", "Поставьте её и положите керн."))
t("i2", "wellhead", "drilling", ["i2/bop"], "build", "wellhead", 1, "i2/production", 25,
  ("Wellhead", "Колонная головка"),
  ("The wellhead holds the casing strings at the surface once drilling is over.",
   "Колонная головка держит обсадные колонны на поверхности после бурения."),
  ("Set it on the finished well.", "Поставьте её на законченную скважину."))
t("i2", "christmas_tree", "drilling", ["i2/wellhead"], "build", "christmas_tree", 1, "i2/production", 25,
  ("Christmas tree", "Фонтанная арматура"),
  ("The tree's valves open and close the flowing well.", "Задвижки арматуры открывают и закрывают фонтанирующую скважину."),
  ("Place it on top of the wellhead.", "Поставьте её поверх колонной головки."))
t("i2", "first_oil", "drilling", ["i2/christmas_tree"], "build", "three_phase_separator", 1, "i2/production", 40,
  ("First oil", "Первая нефть"),
  ("The separator splits the well stream into oil, gas and water.", "Сепаратор делит продукцию скважины на нефть, газ и воду."),
  ("Pipe the tree to a three-phase separator and open the well.", "Подведите арматуру к трёхфазному сепаратору и откройте скважину."))
t("i2", "pumpjack", "drilling", ["i2/christmas_tree"], "build", "pumpjack", 1, "i2/production", 20,
  ("Pumpjack", "Станок-качалка"),
  ("When the reservoir pressure falls, the pumpjack lifts the oil.", "Когда давление пласта падает, качалка поднимает нефть."),
  ("Put it beside the wellhead and drive it from a shaft.", "Поставьте её у устья и крутите валом."))
t("i2", "rod_pump", "drilling", ["i2/pumpjack"], "obtain", "downhole_rod_pump", 1, "i2/production", 15,
  ("Downhole pump", "Глубинный насос"),
  ("The pump at the bottom of the rods does the actual lifting.", "Насос на конце штанг и поднимает жидкость."),
  ("Make one and run it into the well.", "Сделайте его и спустите в скважину."))
t("i2", "flare", "drilling", ["i2/first_oil"], "build", "flare_stack", 6, "i2/flare", 20,
  ("Flare stack", "Факельный ствол"),
  ("Gas you cannot use or store goes to the flare, not to the air.", "Газ, который некуда деть, идёт на факел, а не в воздух."),
  ("Build a stack at least six blocks high away from the plant.", "Постройте ствол не ниже шести блоков подальше от установок."))
t("i2", "flare_tip", "drilling", ["i2/flare"], "build", "flare_tip", 1, "i2/flare", 15,
  ("Flare tip", "Факельный оголовок"),
  ("The tip keeps a pilot burning so released gas always ignites.", "Оголовок держит дежурное пламя, чтобы сброс всегда горел."),
  ("Cap the stack with it.", "Поставьте его на верх ствола."))
t("i2", "knockout", "drilling", ["i2/flare"], "build", "flare_knockout_drum", 1, "i2/flare", 15,
  ("Knock-out drum", "Факельный сепаратор"),
  ("Liquid carried into a flare comes down as burning rain; the drum catches it first.",
   "Жидкость в факеле падает горящим дождём; сепаратор ловит её раньше."),
  ("Put it in the flare line before the stack.", "Поставьте его на факельной линии перед стволом."))

R = "i2/refining"
t("i2", "tube_furnace", "refining", ["i2/first_oil"], "build", "tube_furnace", 1, R, 25,
  ("Tube furnace", "Трубчатая печь"),
  ("Crude is heated to about 360 °C before it enters the column.", "Нефть греют примерно до 360 °C перед колонной."),
  ("Fire it on associated gas from the separator.", "Топите её попутным газом из сепаратора."))
t("i2", "desalter", "refining", ["i2/tube_furnace"], "build", "electrostatic_desalter", 1, "i2/conversion", 20,
  ("Desalter", "ЭЛОУ"),
  ("Salt in crude corrodes the column; the desalter washes it out with water and a field.",
   "Соль в нефти разъедает колонну; ЭЛОУ вымывает её водой в электрическом поле."),
  ("Place it within six blocks of the tube furnace; it feeds the furnace itself.",
   "Поставьте её не дальше шести блоков от печи; она сама подаёт нефть в печь."))
t("i2", "crude_column", "refining", ["i2/tube_furnace"], "build", "crude_column", 1, R, 30,
  ("Crude column", "Атмосферная колонна"),
  ("The column splits crude by boiling point: gas, gasoline, kerosene, diesel, fuel oil.",
   "Колонна делит нефть по температуре кипения: газ, бензин, керосин, дизель, мазут."),
  ("Place the column base next to the furnace.", "Поставьте куб колонны рядом с печью."))
t("i2", "column_top", "refining", ["i2/crude_column"], "build", "column_top", 1, R, 30,
  ("Close the column", "Закройте колонну"),
  ("The column counts its tray sections only up to a top.", "Колонна считает тарелки только до верха."),
  ("Stack at least three tray sections on the base and finish with the top.",
   "Поставьте на куб не меньше трёх тарельчатых секций и закончите верхом."))
t("i2", "vacuum_column", "refining", ["i2/column_top"], "build", "vacuum_column", 1, "i2/conversion", 25,
  ("Vacuum column", "Вакуумная колонна"),
  ("Fuel oil boils without cracking only under vacuum: gas oil comes off, tar stays.",
   "Мазут кипит без разложения только в вакууме: уходит газойль, остаётся гудрон."),
  ("Put steam ejectors on both sides of it; without them it runs at atmosphere.",
   "Поставьте паровые эжекторы с обеих сторон; без них она работает при атмосфере."))
t("i2", "coker", "refining", ["i2/vacuum_column"], "build", "delayed_coker", 1, "i2/conversion", 25,
  ("Delayed coker", "Замедленное коксование"),
  ("The coker turns tar into light products and petroleum coke.", "Коксование превращает гудрон в светлые продукты и нефтяной кокс."),
  ("Feed it the vacuum residue.", "Подавайте в неё гудрон."))
t("i2", "fcc_catalyst", "refining", ["i2/vacuum_column"], "obtain", "catalyst_fcc", 1, "i2/conversion", 15,
  ("Cracking catalyst", "Катализатор крекинга"),
  ("Zeolite cracks heavy gas oil into gasoline.", "Цеолит крекирует тяжёлый газойль в бензин."),
  ("Make a bag of it.", "Сделайте мешок."))
t("i2", "fcc", "refining", ["i2/fcc_catalyst"], "build", "fcc_unit", 1, "i2/conversion", 35,
  ("Catalytic cracker", "Каткрекинг"),
  ("The FCC burns coke off the catalyst in the regenerator and cracks gas oil in the riser.",
   "Каткрекинг выжигает кокс с катализатора в регенераторе и крекирует газойль в лифт-реакторе."),
  ("It needs air: a centrifugal compressor on a shaft next to it.", "Ему нужен воздух: центробежный компрессор на валу рядом."))
t("i2", "hydrotreating_catalyst", "refining", ["i2/column_top"], "obtain", "catalyst_hydrotreating", 1, "i2/hydrotreating", 15,
  ("Hydrotreating catalyst", "Катализатор гидроочистки"),
  ("Cobalt-molybdenum takes sulfur out of diesel with hydrogen.", "Кобальт-молибден убирает серу из дизеля водородом."),
  ("Make one load.", "Сделайте одну загрузку."))
t("i2", "hydrotreater", "refining", ["i2/hydrotreating_catalyst"], "build", "hydrotreater", 1, "i2/hydrotreating", 30,
  ("Hydrotreater", "Гидроочистка"),
  ("Sulfur leaves the diesel as hydrogen sulfide.", "Сера уходит из дизеля сероводородом."),
  ("Load the catalyst, feed diesel from the side and hydrogen from above.",
   "Загрузите катализатор, дизель подавайте сбоку, водород сверху."))
t("i2", "claus", "refining", ["i2/hydrotreater"], "build", "claus_unit", 1, "i2/hydrotreating", 25,
  ("Claus unit", "Установка Клауса"),
  ("Hydrogen sulfide burns to sulfur instead of going to the flare.", "Сероводород сжигают до серы, а не на факел."),
  ("Place it right next to the hydrotreater: it takes the H2S directly.",
   "Поставьте её вплотную к гидроочистке: она берёт сероводород напрямую."))
t("i2", "sulfur", "refining", ["i2/claus"], "obtain", "sulfur", 16, "i2/hydrotreating", 20,
  ("Sulfur", "Сера"),
  ("Sulfur from oil is the raw material for sulfuric acid.", "Сера из нефти — сырьё для серной кислоты."),
  ("Collect sixteen from the Claus unit.", "Наберите шестнадцать с установки Клауса."))
t("i2", "reformer", "refining", ["i2/hydrotreater"], "build", "catalytic_reformer", 1, R, 30,
  ("Catalytic reformer", "Риформинг"),
  ("Reforming raises the octane of straight-run gasoline and gives hydrogen.",
   "Риформинг поднимает октан прямогонного бензина и даёт водород."),
  ("Feed naphtha and fuel gas; the hydrogen goes back to the hydrotreater.",
   "Подавайте бензин и топливный газ; водород идёт обратно на гидроочистку."))
t("i2", "blender", "refining", ["i2/reformer", "i2/fcc"], "build", "gasoline_blender", 1, R, 30,
  ("Gasoline blending", "Смешение бензина"),
  ("Sold gasoline is a blend of reformate and cracked gasoline to an octane spec.",
   "Товарный бензин — смесь риформата и бензина крекинга по октану."),
  ("Pipe both streams to the blender.", "Подведите оба потока к станции смешения."))

G = "i2/gas_processing"
t("i2", "lts", "gas", ["i2/first_oil"], "build", "low_temperature_separator", 1, G, 25,
  ("Gas plant", "Установка НТС"),
  ("Cold separation drops water and heavy ends out of the gas so it can go down a pipe.",
   "Низкотемпературная сепарация выделяет воду и тяжёлые фракции, чтобы газ можно было качать по трубе."),
  ("Feed it the separator gas.", "Подайте в неё газ сепаратора."))
t("i2", "gas_compressor", "gas", ["i2/lts"], "build", "gas_compressor", 1, G, 20,
  ("Gas compressor", "Газовый компрессор"),
  ("The compressor raises the gas to line pressure.", "Компрессор поднимает газ до давления в трубе."),
  ("Drive it from a shaft after the gas plant.", "Крутите его валом после установки НТС."))
t("i2", "amine", "gas", ["i2/lts"], "build", "amine_unit", 1, G, 25,
  ("Amine treating", "Аминовая очистка"),
  ("Amine pulls hydrogen sulfide and CO2 out of sour gas.", "Амин вытягивает сероводород и CO2 из кислого газа."),
  ("Put it on the gas line; its acid gas can go to the Claus unit.", "Поставьте её на линию газа; кислый газ можно отдать на Клаус."))
t("i2", "lpg", "gas", ["i2/lts"], "build", "gas_storage_bullet", 1, G, 20,
  ("LPG storage", "Хранение СУГ"),
  ("Propane and butane are kept liquid under pressure in bullets.", "Пропан и бутан хранят жидкими под давлением в ёмкостях."),
  ("Place a bullet and fill it from the gas plant.", "Поставьте ёмкость и заполните с установки НТС."))
t("i2", "helium", "gas", ["i2/lts", "i1/air_separation"], "build", "helium_recovery_unit", 1, "i2/helium", 30,
  ("Helium from gas", "Гелий из газа"),
  ("Some fields carry a little helium; cold separation recovers it.", "В некоторых залежах есть немного гелия; холод его выделяет."),
  ("Feed it gas from a helium-bearing field.", "Подавайте газ с гелиеносной залежи."))
t("i2", "cryo_tank", "gas", ["i2/helium"], "build", "cryogenic_tank", 1, "i2/helium", 15,
  ("Cryogenic tank", "Криогенная ёмкость"),
  ("Liquid helium and nitrogen boil away unless kept in an insulated tank.",
   "Жидкие гелий и азот выкипают без изолированной ёмкости."),
  ("Place it next to the helium unit.", "Поставьте её рядом с гелиевой установкой."))

C = "i2/plc"
t("i2", "plc", "control", ["i1/analog_loop"], "build", "plc", 1, C, 30,
  ("Programmable controller", "ПЛК"),
  ("A PLC does what a cabinet of relays did, and you can reprogram it.",
   "ПЛК делает то, что делал шкаф реле, и его можно перепрограммировать."),
  ("Place it and wire its inputs and outputs.", "Поставьте его и подключите входы и выходы."))
t("i2", "sis", "control", ["i2/plc"], "build", "sis_logic_solver", 1, "i2/sis", 30,
  ("Safety system", "Противоаварийная защита"),
  ("The safety logic is separate from control: it only trips, it never regulates.",
   "Логика ПАЗ отдельна от управления: она только отключает и никогда не регулирует."),
  ("Place a logic solver and give it its own sensors.", "Поставьте логический блок и дайте ему собственные датчики."))
t("i2", "gas_detector", "control", ["i2/sis"], "build", "gas_detector", 1, "i2/sis", 15,
  ("Gas detector", "Газоанализатор"),
  ("Fixed detectors see a leak before a person does.", "Стационарный газоанализатор замечает утечку раньше человека."),
  ("Mount one where gas would collect.", "Поставьте его там, где будет скапливаться газ."))
t("i2", "portable_detector", "control", ["i2/sis"], "obtain", "portable_gas_detector", 1, "i2/oil_gas_accidents", 15,
  ("Portable gas detector", "Переносной газоанализатор"),
  ("Hydrogen sulfide kills without a smell at high doses; carry a detector on the plant.",
   "Сероводород в больших дозах убивает без запаха; носите газоанализатор на установке."),
  ("Make one and keep it in the hotbar.", "Сделайте его и держите на панели."))
t("i2", "rtu", "control", ["i2/plc"], "build", "rtu_station", 1, "i2/telemetry", 20,
  ("Telemetry station", "Шлюз телемеханики"),
  ("A remote station brings far wells and pumps onto the control room screen.",
   "Шлюз телемеханики выводит дальние скважины и насосы на экран щитовой."),
  ("Place it at the remote site.", "Поставьте его на удалённом объекте."))
t("i2", "historian", "control", ["i2/rtu"], "build", "historian_server", 1, "i2/telemetry", 20,
  ("Historian", "Сервер историана"),
  ("The historian keeps every tag's trend, so you can see what happened before a trip.",
   "Историан хранит тренды всех тегов, чтобы видеть, что было до останова."),
  ("Place it in the control room.", "Поставьте его в щитовой."))
t("i2", "mes", "control", ["i2/historian"], "build", "mes_server", 1, "i2/mes", 30,
  ("Plant server", "Сервер завода"),
  ("The MES server plans load across the plant from the historian's data.",
   "Сервер завода распределяет нагрузку по данным историана."),
  ("Place it next to the historian.", "Поставьте его рядом с историаном."))
t("i2", "control_room", "control", ["i2/rtu", "i2/sis"], "build", "control_room_core", 1, "i2/control_room", 40,
  ("Control room", "Блочный щит"),
  ("One room where the operators see and run the whole unit.", "Одна комната, откуда операторы видят и ведут всю установку."),
  ("Place the core inside a closed room; its chapter lists what makes it habitable.",
   "Поставьте ядро в закрытом помещении; глава перечисляет, что нужно для обитаемости."))

L = "i2/pipelines"
t("i2", "pipe_welder", "logistics", ["i1/steel_plate"], "build", "pipe_welder", 1, L, 20,
  ("Pipe mill", "Трубосварочный стан"),
  ("Welded pipe from plate is cheaper than seamless for long lines.", "Сварная труба из листа дешевле бесшовной для длинных линий."),
  ("Feed it steel plate.", "Подавайте в него стальной лист."))
t("i2", "welded_pipe", "logistics", ["i2/pipe_welder"], "obtain", "welded_pipe", 16, L, 15,
  ("Line pipe", "Трубы для линии"),
  ("A pipeline needs a lot of pipe.", "Трубопроводу нужно много труб."),
  ("Make sixteen welded pipes.", "Сделайте шестнадцать сварных труб."))
t("i2", "pig_launcher", "logistics", ["i2/welded_pipe"], "build", "pig_launcher", 1, L, 20,
  ("Pig launcher", "Камера запуска СОД"),
  ("A pig scrapes the line clean and a smart pig looks for corrosion inside it.",
   "Скребок чистит трубу, а внутритрубный дефектоскоп ищет коррозию изнутри."),
  ("Put a launcher at one end of the line and a receiver at the other.",
   "Поставьте камеру запуска в начале линии и камеру приёма в конце."))
t("i2", "smart_pig", "logistics", ["i2/pig_launcher"], "obtain", "smart_pig", 1, L, 20,
  ("Smart pig", "Внутритрубный дефектоскоп"),
  ("The smart pig maps wall thinning along the whole line.", "Дефектоскоп снимает утонение стенки по всей линии."),
  ("Make one and run it through the line.", "Сделайте его и пропустите по линии."))
t("i2", "tank_gauge", "logistics", ["i2/column_top"], "build", "tank_gauge", 1, L, 15,
  ("Tank farm", "Резервуарный парк"),
  ("Products wait in tanks for the market; a gauge tells you how much you have.",
   "Продукты ждут рынка в резервуарах; уровнемер говорит, сколько их."),
  ("Mount a gauge on a product tank.", "Поставьте уровнемер на резервуар продукта."))
t("i2", "market", "logistics", ["i2/tank_gauge"], "build", "market_terminal", 1, "i2/market", 25,
  ("Exchange", "Биржа"),
  ("Prices move with supply: sell when the market wants your product.",
   "Цены двигаются со спросом: продавайте, когда рынку нужен ваш продукт."),
  ("Place the terminal; it shows prices and takes orders.", "Поставьте терминал; он показывает цены и принимает заявки."))

# ------------------------------------------------------------------------------------------------ I3 chemistry
t("i3", "pyrolysis", "organic", ["i2/column_top"], "build", "pyrolysis_furnace", 1, "i3/pyrolysis", 30,
  ("Pyrolysis furnace", "Печь пиролиза"),
  ("Ethane or propane cracked with steam at 850 °C gives ethylene.", "Этан или пропан, крекированные с паром при 850 °C, дают этилен."),
  ("Feed propane, feedwater and fuel gas.", "Подавайте пропан, питательную воду и топливный газ."))
t("i3", "cracked_gas", "organic", ["i3/pyrolysis"], "build", "cracked_gas_unit", 1, "i3/pyrolysis", 25,
  ("Gas separation", "Газоразделение"),
  ("Cracked gas is cooled and split into ethylene, propylene and the rest.",
   "Пирогаз охлаждают и делят на этилен, пропилен и остальное."),
  ("Place it after the furnaces.", "Поставьте её после печей."))
t("i3", "ldpe", "organic", ["i3/cracked_gas"], "build", "ldpe_reactor", 1, "i3/pyrolysis", 25,
  ("Polyethylene plant", "Установка ПЭВД"),
  ("Ethylene polymerizes at very high pressure into low-density polyethylene.",
   "Этилен при очень высоком давлении полимеризуется в полиэтилен низкой плотности."),
  ("Feed it ethylene.", "Подавайте этилен."))
t("i3", "polyethylene", "organic", ["i3/ldpe"], "obtain", "polyethylene", 4, "i3/pyrolysis", 30,
  ("Polyethylene", "Полиэтилен"),
  ("Pellets for cable insulation and film.", "Гранулы для изоляции кабеля и плёнки."),
  ("Collect four bags.", "Наберите четыре мешка."))
t("i3", "steam_reformer", "organic", ["i2/lts"], "build", "steam_reformer", 1, "i3/methanol", 30,
  ("Steam reformer", "Паровая конверсия"),
  ("Methane and steam over nickel give synthesis gas: hydrogen and carbon monoxide.",
   "Метан с паром на никеле дают синтез-газ: водород и угарный газ."),
  ("Fire it on gas and feed it gas and steam.", "Топите её газом и подавайте газ и пар."))
t("i3", "methanol_catalyst", "organic", ["i3/steam_reformer"], "obtain", "catalyst_methanol", 1, "i3/methanol", 15,
  ("Methanol catalyst", "Катализатор метанола"),
  ("Copper-zinc catalyst makes methanol from syngas.", "Медно-цинковый катализатор делает метанол из синтез-газа."),
  ("Make one load.", "Сделайте одну загрузку."))
t("i3", "methanol_converter", "organic", ["i3/methanol_catalyst"], "build", "methanol_converter", 1, "i3/methanol", 25,
  ("Methanol synthesis", "Синтез метанола"),
  ("Syngas at pressure over the catalyst gives crude methanol.", "Синтез-газ под давлением на катализаторе даёт метанол-сырец."),
  ("Feed it the reformer's syngas.", "Подавайте синтез-газ с конверсии."))
t("i3", "methanol_column", "organic", ["i3/methanol_converter"], "build", "methanol_column", 1, "i3/methanol", 20,
  ("Methanol columns", "Ректификация метанола"),
  ("Columns take water and by-products out of crude methanol.", "Колонны убирают воду и примеси из метанола-сырца."),
  ("Place them after the converter.", "Поставьте их после агрегата синтеза."))
t("i3", "ammonia", "inorganic", ["i3/steam_reformer", "i1/air_separation"], "build", "ammonia_converter", 1, "i3/ammonia", 35,
  ("Ammonia synthesis", "Синтез аммиака"),
  ("Hydrogen from the reformer and nitrogen from the air make ammonia at high pressure.",
   "Водород с конверсии и азот из воздуха под высоким давлением дают аммиак."),
  ("Feed hydrogen and nitrogen three to one.", "Подавайте водород и азот три к одному."))
t("i3", "nitric_acid", "inorganic", ["i3/ammonia"], "build", "nitric_acid_unit", 1, "i3/ammonia", 30,
  ("Nitric acid", "Азотная кислота"),
  ("Ammonia burnt on platinum and absorbed in water gives nitric acid.",
   "Аммиак, сожжённый на платине и поглощённый водой, даёт азотную кислоту."),
  ("Feed it ammonia, air and water.", "Подавайте аммиак, воздух и воду."))
t("i3", "chlor_alkali", "inorganic", ["i1/first_sync"], "build", "chlor_alkali_cell", 1, "i3/chlor_alkali", 30,
  ("Chlor-alkali cells", "Электролиз рассола"),
  ("Brine and a lot of current give chlorine, caustic soda and hydrogen.",
   "Рассол и много тока дают хлор, каустическую соду и водород."),
  ("Power the cells from the grid; they draw a heavy DC load.", "Питайте серию от сети; она берёт большую нагрузку постоянного тока."))
t("i3", "chlorine", "inorganic", ["i3/chlor_alkali"], "build", "chlorine_liquefier", 1, "i3/chlor_alkali", 20,
  ("Liquid chlorine", "Жидкий хлор"),
  ("Chlorine is dried and liquefied to be stored and shipped.", "Хлор сушат и сжижают, чтобы хранить и возить."),
  ("Place it after the cells.", "Поставьте её после электролизёров."))
t("i3", "pvc_reactor", "inorganic", ["i3/chlorine", "i3/cracked_gas"], "build", "pvc_reactor", 1, "i3/chlor_alkali", 25,
  ("PVC reactor", "Автоклав ПВХ"),
  ("Ethylene and chlorine give vinyl chloride; it polymerizes into PVC.",
   "Этилен и хлор дают винилхлорид; он полимеризуется в ПВХ."),
  ("Feed it from the gas separation and the chlorine line.", "Подавайте сырьё с газоразделения и линии хлора."))
t("i3", "pvc", "inorganic", ["i3/pvc_reactor"], "obtain", "pvc", 4, "i3/chlor_alkali", 25,
  ("PVC resin", "Смола ПВХ"),
  ("PVC goes into pipe and cable sheathing.", "ПВХ идёт на трубы и оболочки кабеля."),
  ("Collect four bags.", "Наберите четыре мешка."))
t("i3", "gas_turbine", "energy", ["i2/gas_compressor"], "build", "gas_turbine", 1, "i3/combined_cycle", 30,
  ("Gas turbine", "Газовая турбина"),
  ("A gas turbine burns gas directly and turns a generator.", "Газовая турбина сжигает газ напрямую и вращает генератор."),
  ("Feed it compressed gas.", "Подавайте сжатый газ."))
t("i3", "hrsg", "energy", ["i3/gas_turbine"], "build", "hrsg", 1, "i3/combined_cycle", 25,
  ("Heat recovery boiler", "Котёл-утилизатор"),
  ("The turbine exhaust is still 500 °C hot; the boiler makes steam from it.",
   "Выхлоп турбины ещё 500 °C; котёл делает из него пар."),
  ("Put it right behind the turbine exhaust.", "Поставьте его сразу за выхлопом турбины."))
t("i3", "combined_cycle", "energy", ["i3/hrsg"], "build", "steam_turbine", 1, "i3/combined_cycle", 35,
  ("Combined cycle", "Парогазовая установка"),
  ("Gas and steam turbine together get half the fuel's heat into power.",
   "Газовая и паровая турбины вместе превращают в ток половину тепла топлива."),
  ("Feed the boiler's steam to a steam turbine.", "Подайте пар котла на паровую турбину."))
E3 = "i3/electronics"
t("i3", "silicon", "energy", ["i1/first_sync"], "obtain", "metallurgical_silicon", 4, E3, 20,
  ("Silicon", "Технический кремний"),
  ("Quartz reduced with carbon in an arc furnace gives silicon, 98 % pure.",
   "Кварц, восстановленный углеродом в дуговой печи, даёт кремний чистотой 98 %."),
  ("Make four in the arc furnace.", "Выплавьте четыре в дуговой печи."))
t("i3", "trichlorosilane", "energy", ["i3/silicon", "i3/chlor_alkali"], "build", "trichlorosilane_reactor", 1, E3, 25,
  ("Trichlorosilane", "Трихлорсилан"),
  ("Silicon with hydrogen chloride becomes a liquid that can be distilled clean.",
   "Кремний с хлороводородом становится жидкостью, которую можно очистить перегонкой."),
  ("Feed it silicon and HCl.", "Подавайте кремний и хлороводород."))
t("i3", "siemens", "energy", ["i3/trichlorosilane"], "build", "siemens_reactor", 1, E3, 25,
  ("Siemens reactor", "Реактор Сименса"),
  ("Pure trichlorosilane deposits polysilicon on hot rods.", "Чистый трихлорсилан осаждает поликремний на горячих стержнях."),
  ("Feed it trichlorosilane and hydrogen.", "Подавайте трихлорсилан и водород."))
t("i3", "polysilicon", "energy", ["i3/siemens"], "obtain", "polysilicon", 1, E3, 20,
  ("Polysilicon", "Поликремний"),
  ("Nine nines pure: ready to grow a crystal.", "Девять девяток чистоты: можно растить кристалл."),
  ("Take a rod out of the reactor.", "Выньте стержень из реактора."))
t("i3", "ingot", "energy", ["i3/polysilicon"], "obtain", "silicon_ingot", 1, E3, 20,
  ("Single crystal", "Монокристалл"),
  ("A crystal is pulled from the melt, one orientation through the whole ingot.",
   "Кристалл тянут из расплава, с одной ориентацией по всему слитку."),
  ("Grow an ingot from polysilicon.", "Вырастите слиток из поликремния."))
t("i3", "wafer_saw", "energy", ["i3/ingot"], "build", "wafer_saw", 1, E3, 20,
  ("Wafer saw", "Резка пластин"),
  ("The ingot is sliced and polished into wafers.", "Слиток режут и полируют на пластины."),
  ("Place the saw and give it the ingot.", "Поставьте станок и дайте ему слиток."))
t("i3", "wafer", "energy", ["i3/wafer_saw"], "obtain", "silicon_wafer", 1, E3, 15,
  ("Silicon wafer", "Кремниевая пластина"),
  ("Every chip starts as a polished wafer.", "Каждая микросхема начинается с полированной пластины."),
  ("Take one from the saw.", "Возьмите одну со станка."))
t("i3", "transistor", "energy", ["i3/wafer"], "obtain", "transistor", 4, E3, 20,
  ("Transistors", "Транзисторы"),
  ("A transistor does what a vacuum tube did, smaller and without a filament.",
   "Транзистор делает то же, что лампа, только меньше и без накала."),
  ("Make four.", "Сделайте четыре."))
t("i3", "chip", "energy", ["i3/transistor", "i2/plc"], "obtain", "integrated_circuit", 1, E3, 30,
  ("Integrated circuit", "Микросхема"),
  ("Many transistors on one die: the logic of a whole relay cabinet in a chip.",
   "Много транзисторов на одном кристалле: логика целого шкафа реле в корпусе."),
  ("Make one logic chip.", "Сделайте одну логическую микросхему."))
t("i3", "memory", "energy", ["i3/chip"], "obtain", "memory_chip", 1, E3, 20,
  ("Memory", "Память"),
  ("Memory keeps programs and data for controllers and computers.", "Память хранит программы и данные контроллеров и компьютеров."),
  ("Make one memory chip.", "Сделайте одну микросхему памяти."))
t("i3", "board", "energy", ["i3/chip"], "obtain", "printed_circuit_board", 1, E3, 25,
  ("Circuit board", "Печатная плата"),
  ("Chips on a board make a computer.", "Микросхемы на плате — это уже компьютер."),
  ("Make one board.", "Сделайте одну плату."))

# ------------------------------------------------------------------------------------------------ I4 nuclear fuel
F = "i4/uranium_front_end"
t("i4", "dosimeter", "radiation", ["i1/pile_steady"], "obtain", "dosimeter_radiometer", 1, "i4/radiation", 20,
  ("Dosimeter", "Дозиметр"),
  ("You cannot feel radiation; the dosimeter is how you know your dose.", "Радиацию не чувствуешь; дозу знаешь только по дозиметру."),
  ("Make one and keep it with you.", "Сделайте его и держите при себе."))
t("i4", "contamination_meter", "radiation", ["i4/dosimeter"], "obtain", "contamination_meter", 1, "i4/contamination", 15,
  ("Contamination meter", "Радиометр загрязнённости"),
  ("Dose rate and contamination are different things: this one checks surfaces and hands.",
   "Мощность дозы и загрязнённость — разные вещи: этот прибор проверяет поверхности и руки."),
  ("Make one.", "Сделайте его."))
t("i4", "decon_shower", "radiation", ["i4/contamination_meter"], "build", "decon_shower", 1, "i4/contamination", 20,
  ("Decontamination shower", "Санпропускник"),
  ("Contamination leaves the controlled area on skin and clothes unless washed off at the exit.",
   "Загрязнение уходит из зоны на коже и одежде, если не смыть его на выходе."),
  ("Put it at the exit of the controlled area.", "Поставьте его на выходе из зоны контроля."))
t("i4", "decon_sprayer", "radiation", ["i4/decon_shower"], "obtain", "decon_sprayer", 1, "i4/contamination", 15,
  ("Decon sprayer", "Распылитель дезактивации"),
  ("Floors and equipment are cleaned with solutions, not water.", "Полы и оборудование отмывают растворами, а не водой."),
  ("Make one.", "Сделайте его."))
t("i4", "crushed_ore", "ore", ["i1/pitchblende"], "obtain", "crushed_uranium_ore", 16, F, 20,
  ("Crushed uranium ore", "Дроблёная урановая руда"),
  ("Ore has a fraction of a percent of uranium; it is crushed before leaching.",
   "В руде доли процента урана; перед выщелачиванием её дробят."),
  ("Crush sixteen.", "Раздробите шестнадцать."))
t("i4", "leach", "ore", ["i4/crushed_ore", "i2/sulfur"], "build", "leach_tank", 1, F, 25,
  ("Leaching", "Выщелачивание"),
  ("Sulfuric acid dissolves the uranium out of the ore.", "Серная кислота растворяет уран из руды."),
  ("Feed it ground ore and acid.", "Подавайте молотую руду и кислоту."))
t("i4", "ion_exchange", "ore", ["i4/leach"], "build", "uranium_ion_exchange", 1, F, 20,
  ("Ion exchange", "Ионный обмен"),
  ("Resin picks uranium out of the dilute solution.", "Смола выбирает уран из разбавленного раствора."),
  ("Put it after the leach tanks.", "Поставьте её после выщелачивания."))
t("i4", "precipitation", "ore", ["i4/ion_exchange"], "build", "precipitation_tank", 1, F, 20,
  ("Precipitation", "Осаждение"),
  ("Ammonia drops the uranium out as a yellow paste.", "Аммиак осаждает уран жёлтой пастой."),
  ("Feed it the eluate and ammonia.", "Подавайте элюат и аммиак."))
t("i4", "yellowcake", "ore", ["i4/precipitation"], "obtain", "yellowcake", 1, F, 30,
  ("Yellowcake", "Жёлтый кек"),
  ("Uranium concentrate: the end of the mine and the start of the fuel cycle.",
   "Концентрат урана: конец рудника и начало топливного цикла."),
  ("Take it from the precipitation tank.", "Заберите его из осадителя."))
t("i4", "calciner", "ore", ["i4/yellowcake"], "build", "rotary_calciner", 1, F, 20,
  ("Calciner", "Прокалочная печь"),
  ("Calcining turns the paste into uranium oxide.", "Прокалка превращает пасту в оксид урана."),
  ("Feed it yellowcake.", "Подавайте жёлтый кек."))
t("i4", "hf", "ore", ["i2/sulfur"], "build", "hf_kiln", 1, F, 20,
  ("Hydrogen fluoride", "Фтористый водород"),
  ("Fluorspar and sulfuric acid give HF for the conversion to UF6.", "Плавиковый шпат и серная кислота дают HF для перевода в UF6."),
  ("Feed it fluorspar and acid.", "Подавайте плавиковый шпат и кислоту."))
t("i4", "hydrofluorination", "ore", ["i4/calciner", "i4/hf"], "build", "hydrofluorination_reactor", 1, F, 20,
  ("UO2 to UF4", "UO2 в UF4"),
  ("HF turns the oxide into green salt, uranium tetrafluoride.", "HF превращает оксид в зелёную соль, тетрафторид урана."),
  ("Feed it oxide and HF.", "Подавайте оксид и HF."))
t("i4", "fluorination", "ore", ["i4/hydrofluorination"], "build", "fluorination_reactor", 1, F, 25,
  ("UF4 to UF6", "UF4 в UF6"),
  ("Fluorine gas makes UF6, the only uranium compound that is a gas at modest heat.",
   "Фтор даёт UF6 — единственное соединение урана, которое становится газом при умеренном нагреве."),
  ("Feed it green salt and fluorine.", "Подавайте зелёную соль и фтор."))
t("i4", "uf6", "ore", ["i4/fluorination"], "obtain", "uf6_cylinder_48y", 1, F, 30,
  ("A cylinder of UF6", "Баллон UF6"),
  ("Natural UF6 in a 48Y cylinder is the feed of the enrichment plant.", "Природный UF6 в баллоне 48Y — сырьё обогатительного завода."),
  ("Fill a cylinder from the fluorination reactor.", "Наполните баллон с реактора фторирования."))
EN = "i4/enrichment"
t("i4", "centrifuge", "ore", ["i4/uf6", "i3/chip"], "build", "gas_centrifuge", 8, EN, 30,
  ("Gas centrifuges", "Газовые центрифуги"),
  ("A centrifuge spins UF6 so the heavier U-238 moves out a little more than U-235.",
   "Центрифуга крутит UF6, и тяжёлый U-238 уходит к стенке чуть сильнее, чем U-235."),
  ("Place eight centrifuge sections in a hall.", "Поставьте восемь секций центрифуг в зале."))
t("i4", "cascade", "ore", ["i4/centrifuge"], "build", "centrifuge_cascade", 1, EN, 40,
  ("Cascade", "Каскад"),
  ("One machine barely separates; thousands in a cascade reach reactor grade.",
   "Одна машина почти не разделяет; тысячи в каскаде доходят до реакторного обогащения."),
  ("Place the cascade console in the centrifuge hall.", "Поставьте пульт каскада в зале центрифуг."))
t("i4", "enriched", "ore", ["i4/cascade"], "obtain", "uf6_cylinder_30b", 1, EN, 40,
  ("Enriched uranium", "Обогащённый уран"),
  ("A 30B cylinder holds product at a few percent U-235.", "Баллон 30B хранит продукт с несколькими процентами U-235."),
  ("Run the cascade with feed, product and tails cylinders loaded.", "Запустите каскад с баллонами сырья, продукта и отвала."))
FU = "i4/fuel"
t("i4", "pellet_press", "fuel", ["i4/enriched"], "build", "pellet_press", 1, FU, 25,
  ("Pellet press", "Пресс таблеток"),
  ("UO2 powder is pressed and sintered into ceramic pellets.", "Порошок UO2 прессуют и спекают в керамические таблетки."),
  ("Feed it oxide powder.", "Подавайте порошок оксида."))
t("i4", "zircon", "fuel", ["i1/first_sync"], "obtain", "zircon_concentrate", 4, FU, 15,
  ("Zircon", "Циркон"),
  ("Zirconium hardly absorbs neutrons: it is the cladding of fuel rods.", "Цирконий почти не поглощает нейтроны: из него оболочки твэлов."),
  ("Collect four concentrate.", "Наберите четыре концентрата."))
t("i4", "zirconium", "fuel", ["i4/zircon"], "obtain", "zirconium_sponge", 1, FU, 20,
  ("Reactor-grade zirconium", "Цирконий реакторной чистоты"),
  ("Hafnium sits with zirconium in the ore and absorbs neutrons; it has to come out.",
   "Гафний сидит в руде с цирконием и поглощает нейтроны; его надо убрать."),
  ("Make one sponge.", "Получите одну губку."))
t("i4", "fuel_rod", "fuel", ["i4/pellet_press", "i4/zirconium"], "obtain", "fuel_rod", 4, FU, 25,
  ("Fuel rods", "Твэлы"),
  ("Pellets stacked in a zirconium tube and sealed with helium.", "Таблетки в циркониевой трубке, заваренной с гелием."),
  ("Make four rods.", "Сделайте четыре твэла."))
t("i4", "assembly_jig", "fuel", ["i4/fuel_rod"], "build", "assembly_jig", 1, FU, 20,
  ("Assembly jig", "Стапель сборки ТВС"),
  ("Rods go into a grid frame to make an assembly.", "Твэлы вставляют в каркас с решётками."),
  ("Place the jig.", "Поставьте стапель."))
t("i4", "fuel_assembly", "fuel", ["i4/assembly_jig"], "obtain", "fuel_assembly", 1, FU, 50,
  ("Fuel assembly", "ТВС"),
  ("The assembly is what goes into a power reactor's core.", "Сборка — то, что ставят в активную зону энергетического реактора."),
  ("Assemble one on the jig.", "Соберите одну на стапеле."))
B = "i4/spent_fuel"
t("i4", "fuel_rack", "backend", ["i4/fuel_assembly"], "build", "fuel_rack", 1, B, 20,
  ("Spent fuel pool", "Бассейн выдержки"),
  ("Spent fuel stays hot for years; it cools under water in racks.", "Отработанное топливо греется годами; его держат под водой в стеллажах."),
  ("Place a rack at the bottom of a deep pool of water.", "Поставьте стеллаж на дно глубокого бассейна с водой."))
t("i4", "purex", "backend", ["i4/fuel_rack", "i3/nitric_acid"], "build", "purex_cell", 1, "i4/purex", 40,
  ("PUREX", "PUREX"),
  ("Nitric acid and solvent extraction split spent fuel into uranium, plutonium and waste.",
   "Азотная кислота и экстракция делят отработанное топливо на уран, плутоний и отходы."),
  ("Place the hot cell; it handles everything behind shielding.", "Поставьте горячую камеру; всё делается за защитой."))
t("i4", "denitration", "backend", ["i4/purex"], "build", "denitration_reactor", 1, "i4/purex", 20,
  ("Denitration", "Денитрация"),
  ("Recovered uranyl nitrate is turned back into oxide.", "Выделенный нитрат уранила снова превращают в оксид."),
  ("Feed it from the PUREX cell.", "Подавайте раствор из каньона PUREX."))
t("i4", "waste_drum", "backend", ["i4/decon_shower"], "obtain", "waste_drum", 4, "i4/radwaste", 15,
  ("Waste drums", "Бочки для РАО"),
  ("Every contaminated rag and filter is waste that has to be packed.", "Каждая грязная ветошь и фильтр — отходы, их надо упаковать."),
  ("Make four drums.", "Сделайте четыре бочки."))
t("i4", "compactor", "backend", ["i4/waste_drum"], "build", "waste_compactor", 1, "i4/radwaste", 20,
  ("Supercompactor", "Суперпресс"),
  ("Pressing drums cuts the volume of solid waste several times.", "Прессование бочек в разы уменьшает объём твёрдых отходов."),
  ("Feed it full drums.", "Подавайте полные бочки."))
t("i4", "bitumenizer", "backend", ["i4/waste_drum"], "build", "bitumenizer", 1, "i4/radwaste", 20,
  ("Bitumenizer", "Битуматор"),
  ("Liquid waste is dried into bitumen so it cannot leak.", "Жидкие отходы высушивают в битум, чтобы они не растекались."),
  ("Feed it evaporator concentrate.", "Подавайте кубовый остаток выпарки."))
t("i4", "vitrification", "backend", ["i4/purex"], "build", "vitrification_melter", 1, "i4/radwaste", 30,
  ("Vitrification", "Остекловывание"),
  ("High-level waste is melted into glass for thousands of years of storage.",
   "Высокоактивные отходы сплавляют в стекло на тысячи лет хранения."),
  ("Feed it raffinate from the PUREX cell.", "Подавайте рафинат из каньона PUREX."))
t("i4", "americium", "backend", ["i4/purex"], "obtain", "am241_oxide", 1, "i4/isotopes", 25,
  ("Americium-241", "Америций-241"),
  ("A by-product of reprocessing, the neutron source of logging sondes.", "Побочный продукт переработки, источник нейтронов для каротажных зондов."),
  ("Separate it at the PUREX cell.", "Выделите его в каньоне PUREX."))
t("i4", "rtg", "backend", ["i4/purex"], "build", "rtg_thermoelectric", 1, "i4/isotopes", 30,
  ("RTG", "РИТЭГ"),
  ("Decay heat of an isotope and thermocouples give power for decades with no fuel.",
   "Тепло распада изотопа и термопары дают ток десятилетиями без топлива."),
  ("Place it with a heat source loaded.", "Поставьте его с загруженным источником тепла."))

# ------------------------------------------------------------------------------------------------ T reactors
t("t", "fuel_channel", "t1", ["i1/pile_steady"], "build", "fuel_channel", 4, "t1/power_reactor", 25,
  ("Fuel channels", "Топливные каналы"),
  ("A power reactor is the graphite pile with water running through its fuel channels.",
   "Энергетический реактор — это графитовый котёл с водой в топливных каналах."),
  ("Place four channel blocks in the graphite stack.", "Поставьте четыре блока с каналами в графитовую кладку."))
t("t", "rod_drive", "t1", ["t/fuel_channel"], "build", "control_rod_drive", 1, "t1/power_reactor", 20,
  ("Control rod drive", "Привод стержня"),
  ("A rod drive moves the absorber in and out of the core.", "Привод двигает поглотитель в зоне и из неё."),
  ("Put it over a rod channel.", "Поставьте его над каналом стержня."))
t("t", "power_reactor", "t1", ["t/rod_drive"], "build", "power_reactor", 1, "t1/power_reactor", 60,
  ("First NPP", "Первая АЭС"),
  ("The reactor's heat makes steam for a turbine: nuclear power on the grid.",
   "Тепло реактора делает пар для турбины: атомная энергия в сети."),
  ("Place the reactor controller at the stack; its chapter lists what it needs.",
   "Поставьте пульт реактора у кладки; глава перечисляет, что ему нужно."))
t("t", "diesel", "t1", ["t/power_reactor"], "build", "diesel_generator", 1, "t1/emergency_diesel", 30,
  ("Emergency diesel", "Аварийный дизель"),
  ("A shut-down core still needs cooling; the diesel powers the pumps when the grid is gone.",
   "Заглушённой зоне всё равно нужно охлаждение; дизель питает насосы, когда пропала сеть."),
  ("Place it and connect it to the station's emergency bus.", "Поставьте его и подключите к аварийной секции станции."))
t("t", "pwr", "t2", ["t/power_reactor", "i4/fuel_assembly", "i2/control_room"], "build", "pwr_reactor", 1, "t2/pwr_reactor", 80,
  ("Pressurized water reactor", "ВВЭР"),
  ("Water under 16 MPa is both coolant and moderator: the core of most power plants today.",
   "Вода под давлением 16 МПа — и теплоноситель, и замедлитель: так устроено большинство станций."),
  ("Place the reactor; load assemblies before the first start.", "Поставьте реактор; загрузите сборки перед первым пуском."))
t("t", "pressurizer", "t2", ["t/pwr"], "build", "pressurizer", 1, "t2/pwr_reactor", 25,
  ("Pressurizer", "Компенсатор давления"),
  ("A steam bubble on top of the loop holds the pressure steady.", "Паровая подушка над контуром держит давление."),
  ("Connect it to a hot leg.", "Подключите его к горячей нитке."))
t("t", "primary_pump", "t2", ["t/pwr"], "build", "primary_pump", 1, "t2/pwr_reactor", 25,
  ("Main coolant pump", "ГЦН"),
  ("The main pumps drive the water through the core.", "Главные насосы гонят воду через зону."),
  ("Drive it from a shaft on the cold leg.", "Крутите его валом на холодной нитке."))
t("t", "steam_generator", "t2", ["t/primary_pump"], "build", "steam_generator", 1, "t2/pwr_reactor", 25,
  ("Steam generator", "Парогенератор"),
  ("Primary water heats secondary water through tubes; only the secondary goes to the turbine.",
   "Вода первого контура греет второй через трубки; на турбину идёт только второй."),
  ("Place it in the loop between hot and cold leg.", "Поставьте его в петлю между горячей и холодной нитками."))
t("t", "cleanup", "t2", ["t/pwr"], "build", "reactor_water_cleanup", 1, "t2/pwr_reactor", 20,
  ("Water cleanup", "Очистка теплоносителя"),
  ("Corrosion products in the coolant become activated; the cleanup takes them out.",
   "Продукты коррозии в теплоносителе активируются; очистка их убирает."),
  ("Connect it to the primary loop.", "Подключите её к первому контуру."))
t("t", "eccs", "t2", ["t/pwr", "t/diesel"], "build", "eccs_pump", 1, "t2/accidents", 30,
  ("Emergency core cooling", "САОЗ"),
  ("If the loop breaks, these pumps flood the core with borated water.", "Если контур порвался, эти насосы заливают зону борной водой."),
  ("Place it on the emergency bus.", "Поставьте его на аварийную секцию."))
t("t", "containment", "t2", ["t/pwr"], "build", "containment_wall", 64, "t2/containment", 40,
  ("Containment", "Гермооболочка"),
  ("The last barrier: a sealed shell that holds even a broken loop.", "Последний барьер: герметичная оболочка, которая держит даже порванный контур."),
  ("Build the shell around the reactor hall.", "Постройте оболочку вокруг реакторного зала."))
t("t", "hatch", "t2", ["t/containment"], "build", "containment_hatch", 1, "t2/containment", 20,
  ("Containment hatch", "Шлюз гермооболочки"),
  ("People get in through an airlock that never opens both doors at once.", "Люди входят через шлюз, где обе двери не открываются разом."),
  ("Set it into the containment wall.", "Встройте его в стену оболочки."))
t("t", "recombiner", "t2", ["t/containment"], "build", "hydrogen_recombiner", 1, "t2/severe_accidents", 25,
  ("Hydrogen recombiner", "Рекомбинатор водорода"),
  ("Hot zirconium and steam make hydrogen; recombiners burn it quietly before it explodes.",
   "Горячий цирконий с паром даёт водород; рекомбинаторы тихо сжигают его до взрыва."),
  ("Mount them high inside the containment.", "Поставьте их высоко внутри оболочки."))
t("t", "bwr", "t2", ["t/pwr"], "build", "bwr_reactor", 1, "t2/bwr_reactor", 40,
  ("Boiling water reactor", "Кипящий реактор"),
  ("Water boils right in the core and goes to the turbine: one loop, no steam generators.",
   "Вода кипит прямо в зоне и идёт на турбину: один контур, без парогенераторов."),
  ("Place the reactor.", "Поставьте реактор."))
t("t", "heavy_water_tower", "t3", ["i2/claus"], "build", "gs_exchange_tower", 1, "t3/heavy_water", 30,
  ("Heavy water plant", "Завод тяжёлой воды"),
  ("Deuterium moves between water and hydrogen sulfide with temperature: the GS process.",
   "Дейтерий переходит между водой и сероводородом в зависимости от температуры: процесс GS."),
  ("Feed it water and H2S.", "Подавайте воду и сероводород."))
t("t", "heavy_water_column", "t3", ["t/heavy_water_tower"], "build", "heavy_water_column", 1, "t3/heavy_water", 25,
  ("Final enrichment", "Доводка"),
  ("Vacuum distillation takes the enriched water to reactor grade.", "Вакуумная перегонка доводит обогащённую воду до реакторной."),
  ("Put it after the exchange towers.", "Поставьте её после обменных колонн."))
t("t", "heavy_water", "t3", ["t/heavy_water_column"], "obtain", "heavy_water_bucket", 1, "t3/heavy_water", 30,
  ("Heavy water", "Тяжёлая вода"),
  ("A bucket of D2O: a moderator so good that natural uranium is enough.",
   "Ведро D2O: замедлитель настолько хорош, что хватает природного урана."),
  ("Fill a bucket at the final column.", "Наберите ведро на колонне доводки."))
t("t", "candu", "t3", ["t/heavy_water", "t/power_reactor"], "build", "candu_reactor", 1, "t3/candu_reactor", 70,
  ("CANDU", "CANDU"),
  ("Heavy water moderator, natural uranium fuel, refuelled on power.", "Тяжеловодный замедлитель, природный уран, перегрузка на ходу."),
  ("Place the reactor.", "Поставьте реактор."))
t("t", "sfr", "t4", ["i4/purex", "t/pwr"], "build", "sfr_reactor", 1, "t4/sfr_reactor", 100,
  ("Fast reactor", "Быстрый реактор"),
  ("Fast neutrons and sodium coolant: the reactor breeds more fuel than it burns.",
   "Быстрые нейтроны и натрий: реактор нарабатывает топлива больше, чем сжигает."),
  ("Place the reactor; it runs on plutonium from reprocessing.", "Поставьте реактор; он работает на плутонии из переработки."))


def main():
    en = json.load(open(os.path.join(HB, "lang", "en_us.json"), encoding="utf-8"))
    ru = json.load(open(os.path.join(HB, "lang", "ru_ru.json"), encoding="utf-8"))
    for f, e in ERAS.items():
        key = f"handbook.promptcritical.route.{f}"
        en[key + ".title"], ru[key + ".title"] = e["title"]
        branches = []
        for bid, ben, bru in e["branches"]:
            bk = f"{key}.branch.{bid}"
            en[bk], ru[bk] = ben, bru
            branches.append({"id": bid, "title": bk})
        tasks = []
        for (ff, tid, branch, req, kind, target, count, entry, xp, title, text, hint) in T:
            if ff != f:
                continue
            tk = f"handbook.promptcritical.task.{f}.{tid}"
            en[tk + ".title"], ru[tk + ".title"] = title
            en[tk + ".text"], ru[tk + ".text"] = text
            en[tk + ".hint"], ru[tk + ".hint"] = hint
            cond = {"item": P + target, "count": count} if kind == "obtain" else (
                {"block": P + target, "count": count} if kind == "build" else {"block": P + target})
            if cond.get("count") == 1:
                del cond["count"]
            tasks.append({"id": f"{P}{f}/{tid}", "branch": branch, "requires": [P + r for r in req], "type": kind,
                          "condition": cond, "entry": P + entry, "icon": P + target, "title": tk + ".title",
                          "text": tk + ".text", "hint": tk + ".hint", "reward": {"xp": xp}})
        doc = {"era": e["era"], "sort": e["sort"], "title": key + ".title", "branches": branches, "tasks": tasks}
        with open(os.path.join(HB, "route", f + ".json"), "w", encoding="utf-8", newline="\n") as fh:
            fh.write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
        print(f"{f}.json: {len(tasks)} tasks")
    for name, m in (("en_us", en), ("ru_ru", ru)):
        with open(os.path.join(HB, "lang", name + ".json"), "w", encoding="utf-8", newline="\n") as fh:
            fh.write(json.dumps(m, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
