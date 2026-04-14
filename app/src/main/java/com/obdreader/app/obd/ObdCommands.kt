package com.obdreader.app.obd

enum class ReadPriority { HIGH, MEDIUM, LOW, ONCE }

/**
 * Pełna lista komend OBD2 – odpowiednik python-obd commands
 * Pokrywa wszystkie standardowe PIDy SAE J1979 (tryby 01, 09)
 * oraz rozszerzenia producentów (0x61–0x8C, 0xA0–0xA6)
 */
enum class ObdCommand(
    val pid: String,
    val mode: String,
    val cmdName: String,
    val unit: String,
    val description: String,
    val priority: ReadPriority = ReadPriority.MEDIUM
) {
    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – bitmapki obsługiwanych PIDów (ONCE, używane tylko do wykrywania)
    // ═══════════════════════════════════════════════════════════════════════
    PIDS_A("00","01","PID support A",       "-","PIDs 01-20",                        ReadPriority.ONCE),
    PIDS_B("20","01","PID support B",       "-","PIDs 21-40",                        ReadPriority.ONCE),
    PIDS_C("40","01","PID support C",       "-","PIDs 41-60",                        ReadPriority.ONCE),
    PIDS_D("60","01","PID support D",       "-","PIDs 61-80",                        ReadPriority.ONCE),
    PIDS_E("80","01","PID support E",       "-","PIDs 81-A0",                        ReadPriority.ONCE),
    PIDS_F("A0","01","PID support F",       "-","PIDs A1-C0",                        ReadPriority.ONCE),
    PIDS_G("C0","01","PID support G",       "-","PIDs C1-E0",                        ReadPriority.ONCE),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 01–0F
    // ═══════════════════════════════════════════════════════════════════════
    STATUS              ("01","01","Status OBD",              "-",  "Status MIL i liczba DTC",             ReadPriority.LOW),
    FREEZE_DTC          ("02","01","Zamrożony DTC",           "-",  "DTC który wywołał freeze frame",      ReadPriority.LOW),
    FUEL_STATUS         ("03","01","Status paliwa",           "-",  "Status układu paliwowego",            ReadPriority.LOW),
    ENGINE_LOAD         ("04","01","Obciążenie silnika",      "%",  "Obliczone obciążenie silnika",        ReadPriority.HIGH),
    COOLANT_TEMP        ("05","01","Temp. chłodnicy",         "°C", "Temperatura płynu chłodzącego",       ReadPriority.MEDIUM),
    SHORT_FUEL_TRIM_1   ("06","01","STFT bank 1",             "%",  "Krótkoterm. korekta paliwa bank 1",   ReadPriority.HIGH),
    LONG_FUEL_TRIM_1    ("07","01","LTFT bank 1",             "%",  "Długoterm. korekta paliwa bank 1",    ReadPriority.MEDIUM),
    SHORT_FUEL_TRIM_2   ("08","01","STFT bank 2",             "%",  "Krótkoterm. korekta paliwa bank 2",   ReadPriority.HIGH),
    LONG_FUEL_TRIM_2    ("09","01","LTFT bank 2",             "%",  "Długoterm. korekta paliwa bank 2",    ReadPriority.MEDIUM),
    FUEL_PRESSURE       ("0A","01","Ciśnienie paliwa",        "kPa","Ciśnienie w układzie paliwowym",      ReadPriority.MEDIUM),
    INTAKE_PRESSURE     ("0B","01","Ciśnienie dolotowe",      "kPa","Ciśnienie w kolektorze dolotowym",    ReadPriority.HIGH),
    ENGINE_RPM          ("0C","01","RPM",                     "rpm","Obroty silnika",                      ReadPriority.HIGH),
    VEHICLE_SPEED       ("0D","01","Prędkość",                "km/h","Prędkość pojazdu",                   ReadPriority.HIGH),
    TIMING_ADVANCE      ("0E","01","Wyprzedzenie zapłonu",    "°",  "Wyprzedzenie zapłonu cyl. 1",         ReadPriority.HIGH),
    INTAKE_TEMP         ("0F","01","Temp. powietrza",         "°C", "Temperatura powietrza na wlocie",     ReadPriority.MEDIUM),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 10–1F
    // ═══════════════════════════════════════════════════════════════════════
    MAF                 ("10","01","Przepływ MAF",            "g/s","Masowy przepływ powietrza",           ReadPriority.HIGH),
    THROTTLE_POS        ("11","01","Pozycja przepustnicy",    "%",  "Pozycja przepustnicy",                ReadPriority.HIGH),
    SECONDARY_AIR_STATUS("12","01","Status powietrza wtór.",  "-",  "Status wtórnego powietrza",           ReadPriority.LOW),
    O2_SENSORS          ("13","01","Sensory O2",              "-",  "Rozmieszczenie sond O2 (2-bank)",     ReadPriority.LOW),
    O2_SENSOR_1_1       ("14","01","O2 B1S1 V",               "V",  "Sonda lambda bank1 sensor1 napięcie", ReadPriority.HIGH),
    O2_SENSOR_1_2       ("15","01","O2 B1S2 V",               "V",  "Sonda lambda bank1 sensor2 napięcie", ReadPriority.HIGH),
    O2_SENSOR_1_3       ("16","01","O2 B1S3 V",               "V",  "Sonda lambda bank1 sensor3 napięcie", ReadPriority.MEDIUM),
    O2_SENSOR_1_4       ("17","01","O2 B1S4 V",               "V",  "Sonda lambda bank1 sensor4 napięcie", ReadPriority.MEDIUM),
    O2_SENSOR_2_1       ("18","01","O2 B2S1 V",               "V",  "Sonda lambda bank2 sensor1 napięcie", ReadPriority.HIGH),
    O2_SENSOR_2_2       ("19","01","O2 B2S2 V",               "V",  "Sonda lambda bank2 sensor2 napięcie", ReadPriority.HIGH),
    O2_SENSOR_2_3       ("1A","01","O2 B2S3 V",               "V",  "Sonda lambda bank2 sensor3 napięcie", ReadPriority.MEDIUM),
    O2_SENSOR_2_4       ("1B","01","O2 B2S4 V",               "V",  "Sonda lambda bank2 sensor4 napięcie", ReadPriority.MEDIUM),
    OBD_COMPLIANCE      ("1C","01","Standard OBD",            "-",  "Standard OBD pojazdu",                ReadPriority.ONCE),
    O2_SENSORS_ALT      ("1D","01","Sensory O2 (alt)",        "-",  "Rozmieszczenie sond O2 (4-bank)",     ReadPriority.LOW),
    AUX_INPUT_STATUS    ("1E","01","Status wejścia aux",      "-",  "Status pomocniczego wejścia",         ReadPriority.LOW),
    RUN_TIME            ("1F","01","Czas pracy silnika",      "s",  "Czas pracy silnika od uruchomienia",  ReadPriority.MEDIUM),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 21–2F
    // ═══════════════════════════════════════════════════════════════════════
    DISTANCE_W_MIL      ("21","01","Dystans z MIL",           "km", "Dystans od włączenia lampki MIL",     ReadPriority.LOW),
    FUEL_RAIL_PRESSURE_VAC("22","01","Ciśnienie szyny (vac)", "kPa","Ciśnienie szyny paliwa – próżnia",    ReadPriority.MEDIUM),
    FUEL_RAIL_PRESSURE_DIRECT("23","01","Ciśnienie szyny (bezp.)","kPa","Bezpośrednie ciśnienie szyny",    ReadPriority.HIGH),
    O2_S1_WR_LAMBDA     ("24","01","O2 WR B1S1 λ+V",          "-",  "Lambda+V sondy wide-range B1S1",      ReadPriority.HIGH),
    O2_S2_WR_LAMBDA     ("25","01","O2 WR B1S2 λ+V",          "-",  "Lambda+V sondy wide-range B1S2",      ReadPriority.HIGH),
    O2_S3_WR_LAMBDA     ("26","01","O2 WR B2S1 λ+V",          "-",  "Lambda+V sondy wide-range B2S1",      ReadPriority.MEDIUM),
    O2_S4_WR_LAMBDA     ("27","01","O2 WR B2S2 λ+V",          "-",  "Lambda+V sondy wide-range B2S2",      ReadPriority.MEDIUM),
    O2_S5_WR_LAMBDA     ("28","01","O2 WR B1S3 λ+V",          "-",  "Lambda+V sondy wide-range B1S3",      ReadPriority.MEDIUM),
    O2_S6_WR_LAMBDA     ("29","01","O2 WR B1S4 λ+V",          "-",  "Lambda+V sondy wide-range B1S4",      ReadPriority.MEDIUM),
    O2_S7_WR_LAMBDA     ("2A","01","O2 WR B2S3 λ+V",          "-",  "Lambda+V sondy wide-range B2S3",      ReadPriority.MEDIUM),
    O2_S8_WR_LAMBDA     ("2B","01","O2 WR B2S4 λ+V",          "-",  "Lambda+V sondy wide-range B2S4",      ReadPriority.MEDIUM),
    COMMANDED_EGR       ("2C","01","Polecenie EGR",           "%",  "Zadany stopień otwarcia EGR",         ReadPriority.MEDIUM),
    EGR_ERROR           ("2D","01","Błąd EGR",                "%",  "Błąd zaworu EGR",                     ReadPriority.MEDIUM),
    EVAP_PURGE          ("2E","01","Oczyszczanie EVAP",       "%",  "Polecenie zaworu oczyszczania EVAP",  ReadPriority.MEDIUM),
    FUEL_LEVEL          ("2F","01","Poziom paliwa",           "%",  "Poziom paliwa w zbiorniku",           ReadPriority.LOW),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 30–3F
    // ═══════════════════════════════════════════════════════════════════════
    WARMUPS_SINCE_DTC_CLEAR("30","01","Rozgrzewania od kasowan.","-","Liczba rozgrzewań od kasowania DTC", ReadPriority.LOW),
    DISTANCE_SINCE_DTC_CLEAR("31","01","Dystans od kasowania", "km","Dystans od kasowania DTC",            ReadPriority.LOW),
    EVAP_VAPOR_PRESSURE ("32","01","Ciśnienie EVAP",          "Pa", "Ciśnienie par EVAP",                  ReadPriority.MEDIUM),
    BARO_PRESSURE       ("33","01","Ciśnienie atmosf.",        "kPa","Ciśnienie atmosferyczne",             ReadPriority.LOW),
    O2_S1_WR_CURRENT    ("34","01","O2 WR B1S1 λ+I",          "mA", "Lambda+prąd sondy WR B1S1",          ReadPriority.HIGH),
    O2_S2_WR_CURRENT    ("35","01","O2 WR B1S2 λ+I",          "mA", "Lambda+prąd sondy WR B1S2",          ReadPriority.HIGH),
    O2_S3_WR_CURRENT    ("36","01","O2 WR B2S1 λ+I",          "mA", "Lambda+prąd sondy WR B2S1",          ReadPriority.MEDIUM),
    O2_S4_WR_CURRENT    ("37","01","O2 WR B2S2 λ+I",          "mA", "Lambda+prąd sondy WR B2S2",          ReadPriority.MEDIUM),
    O2_S5_WR_CURRENT    ("38","01","O2 WR B1S3 λ+I",          "mA", "Lambda+prąd sondy WR B1S3",          ReadPriority.MEDIUM),
    O2_S6_WR_CURRENT    ("39","01","O2 WR B1S4 λ+I",          "mA", "Lambda+prąd sondy WR B1S4",          ReadPriority.MEDIUM),
    O2_S7_WR_CURRENT    ("3A","01","O2 WR B2S3 λ+I",          "mA", "Lambda+prąd sondy WR B2S3",          ReadPriority.MEDIUM),
    O2_S8_WR_CURRENT    ("3B","01","O2 WR B2S4 λ+I",          "mA", "Lambda+prąd sondy WR B2S4",          ReadPriority.MEDIUM),
    CATALYST_TEMP_B1S1  ("3C","01","Temp. kat. B1S1",         "°C", "Temperatura katalizatora B1S1",       ReadPriority.MEDIUM),
    CATALYST_TEMP_B2S1  ("3D","01","Temp. kat. B2S1",         "°C", "Temperatura katalizatora B2S1",       ReadPriority.MEDIUM),
    CATALYST_TEMP_B1S2  ("3E","01","Temp. kat. B1S2",         "°C", "Temperatura katalizatora B1S2",       ReadPriority.MEDIUM),
    CATALYST_TEMP_B2S2  ("3F","01","Temp. kat. B2S2",         "°C", "Temperatura katalizatora B2S2",       ReadPriority.MEDIUM),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 41–4E
    // ═══════════════════════════════════════════════════════════════════════
    MONITOR_STATUS_DRIVE("41","01","Status monitorów",        "-",  "Status monitorów OBD (bieżący jazdy)",ReadPriority.LOW),
    CONTROL_MODULE_VOLTAGE("42","01","Napięcie modułu",       "V",  "Napięcie modułu sterującego",         ReadPriority.MEDIUM),
    ABSOLUTE_LOAD       ("43","01","Bezwzgl. obciążenie",     "%",  "Bezwzgl. wartość obciążenia silnika", ReadPriority.HIGH),
    COMMANDED_EQUIV_RATIO("44","01","Współczynnik lambda",    "-",  "Zadany współczynnik równoważności",   ReadPriority.HIGH),
    RELATIVE_THROTTLE_POS("45","01","Względna przepust.",     "%",  "Względna pozycja przepustnicy",       ReadPriority.HIGH),
    AMBIENT_AIR_TEMP    ("46","01","Temp. zewn.",             "°C", "Temperatura otoczenia",               ReadPriority.LOW),
    THROTTLE_POS_B      ("47","01","Przepustnica B",          "%",  "Bezwzgl. pozycja przepustnicy B",     ReadPriority.MEDIUM),
    THROTTLE_POS_C      ("48","01","Przepustnica C",          "%",  "Bezwzgl. pozycja przepustnicy C",     ReadPriority.MEDIUM),
    ACCELERATOR_POS_D   ("49","01","Pedał gazu D",            "%",  "Bezwzgl. pozycja pedału gazu D",      ReadPriority.HIGH),
    ACCELERATOR_POS_E   ("4A","01","Pedał gazu E",            "%",  "Bezwzgl. pozycja pedału gazu E",      ReadPriority.MEDIUM),
    ACCELERATOR_POS_F   ("4B","01","Pedał gazu F",            "%",  "Bezwzgl. pozycja pedału gazu F",      ReadPriority.MEDIUM),
    THROTTLE_ACTUATOR   ("4C","01","Aktuator przepustnicy",   "%",  "Polecenie sterowania przepustnicą",   ReadPriority.MEDIUM),
    TIME_WITH_MIL       ("4D","01","Czas z MIL",              "min","Czas jazdy z lampką MIL",             ReadPriority.LOW),
    TIME_SINCE_DTC_CLEARED("4E","01","Czas od kasowania DTC","min", "Czas od kasowania DTC",              ReadPriority.LOW),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 4F–5E (rozszerzenia SAE)
    // ═══════════════════════════════════════════════════════════════════════
    MAX_VALUES          ("4F","01","Maks. wartości",          "-",  "Maks. λ, napięcie O2, prąd O2, ciśn. dolotowe", ReadPriority.LOW),
    MAX_MAF             ("50","01","Maks. MAF",               "g/s","Maks. wartość przepływu MAF",         ReadPriority.LOW),
    FUEL_TYPE           ("51","01","Typ paliwa",              "-",  "Typ paliwa",                          ReadPriority.ONCE),
    ETHANOL_PERCENT     ("52","01","% etanolu",               "%",  "Zawartość etanolu w paliwie",         ReadPriority.LOW),
    EVAP_VAPOR_PRESSURE2("53","01","Ciśnienie EVAP abs.",     "kPa","Bezwzgl. ciśnienie par EVAP",         ReadPriority.MEDIUM),
    EVAP_VAPOR_PRESSURE3("54","01","Ciśnienie EVAP alt.",     "Pa", "Alternatywne ciśnienie par EVAP",     ReadPriority.MEDIUM),
    SHORT_FUEL_TRIM_B1  ("55","01","STFT B1 (alt)",           "%",  "Krótkoterm. korekta paliwa B1 alt",   ReadPriority.HIGH),
    LONG_FUEL_TRIM_B1   ("56","01","LTFT B1 (alt)",           "%",  "Długoterm. korekta paliwa B1 alt",    ReadPriority.MEDIUM),
    SHORT_FUEL_TRIM_B2  ("57","01","STFT B2 (alt)",           "%",  "Krótkoterm. korekta paliwa B2 alt",   ReadPriority.HIGH),
    LONG_FUEL_TRIM_B2   ("58","01","LTFT B2 (alt)",           "%",  "Długoterm. korekta paliwa B2 alt",    ReadPriority.MEDIUM),
    FUEL_RAIL_PRESSURE_ABS("59","01","Ciśnienie szyny abs.",  "kPa","Bezwzgl. ciśnienie szyny paliwa",     ReadPriority.HIGH),
    ACCELERATOR_POS_REL ("5A","01","Pedał gazu wzgl.",        "%",  "Względna pozycja pedału gazu",        ReadPriority.HIGH),
    HYBRID_BATTERY_LIFE ("5B","01","Żywotność baterii hyb.", "%",  "Stan naładowania baterii hybrydowej",  ReadPriority.LOW),
    ENGINE_OIL_TEMP     ("5C","01","Temp. oleju silnika",    "°C", "Temperatura oleju silnikowego",       ReadPriority.MEDIUM),
    FUEL_INJECT_TIMING  ("5D","01","Timing wtrysku",          "°",  "Timing wtrysku paliwa",               ReadPriority.HIGH),
    FUEL_RATE           ("5E","01","Zużycie paliwa",          "L/h","Chwilowe zużycie paliwa",             ReadPriority.HIGH),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 5F (status emisji)
    // ═══════════════════════════════════════════════════════════════════════
    EMISSION_REQUIREMENTS("5F","01","Wymagania emisji",       "-",  "Obsługiwane wymagania emisji",        ReadPriority.ONCE),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 61–6B (silnik i napęd)
    // ═══════════════════════════════════════════════════════════════════════
    DRIVER_DEMAND_TORQUE("61","01","Żądany moment obr.",      "%",  "Żądany moment obrotowy przez kierowcę",ReadPriority.HIGH),
    ACTUAL_TORQUE       ("62","01","Rzeczywisty moment",      "%",  "Rzeczywisty moment obrotowy silnika", ReadPriority.HIGH),
    REFERENCE_TORQUE    ("63","01","Moment ref. silnika",    "Nm", "Referencyjny moment obrotowy silnika",ReadPriority.ONCE),
    ENGINE_PERCENT_TORQUE("64","01","Momenty obr. %",         "%",  "Procentowe punkty momentu silnika",   ReadPriority.MEDIUM),
    AUX_IO_SUPPORTED    ("65","01","Aux I/O obs.",            "-",  "Obsługiwane wejścia/wyjścia pomocnicze",ReadPriority.ONCE),
    MASS_AIR_FLOW_SENSOR("66","01","Czujnik MAF (alt)",      "g/s","Wartość czujnika MAF alternatywna",   ReadPriority.HIGH),
    ENGINE_COOLANT_TEMP2("67","01","Temp. chłodnicy 2",      "°C", "Czujniki temperatury chłodnicy",      ReadPriority.MEDIUM),
    INTAKE_AIR_TEMP2    ("68","01","Temp. powietrza 2",      "°C", "Czujniki temperatury powietrza",      ReadPriority.MEDIUM),
    COMMANDED_EGR2      ("69","01","EGR/VVT",                 "%",  "Polecenia EGR i VVT",                 ReadPriority.MEDIUM),
    COMMANDED_DPF       ("6A","01","DPF/VGT",                 "%",  "Polecenia DPF i turbo zmiennej geometrii",ReadPriority.MEDIUM),
    EXHAUST_GAS_TEMP_1  ("6B","01","Temp. spalin 1",         "°C", "Temperatura spalin bank 1",           ReadPriority.MEDIUM),
    EXHAUST_GAS_TEMP_2  ("6C","01","Temp. spalin 2",         "°C", "Temperatura spalin bank 2",           ReadPriority.MEDIUM),
    DPF_DIFFERENTIAL_PRESSURE("6D","01","Ciśnienie DPF",     "kPa","Ciśnienie różnicowe filtra DPF",      ReadPriority.MEDIUM),
    ENGINE_OIL_TEMP2    ("6E","01","Temp. oleju 2",          "°C", "Temperatura oleju silnikowego (ext)", ReadPriority.MEDIUM),
    FUEL_INJECTION_TIMING2("6F","01","Timing wtrysku 2",      "°",  "Polecenia aktywatora turbo",          ReadPriority.HIGH),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 70–7F (turbo, ciśnienie)
    // ═══════════════════════════════════════════════════════════════════════
    BOOST_PRESSURE      ("70","01","Ciśnienie turbo",         "kPa","Ciśnienie doładowania turbosprężarki",ReadPriority.HIGH),
    VGT_STATUS          ("71","01","Status VGT",              "-",  "Status turbo zmiennej geometrii",     ReadPriority.MEDIUM),
    WASTEGATE_STATUS    ("72","01","Status wastegate",         "-",  "Status zaworu wastegate",             ReadPriority.MEDIUM),
    EXHAUST_PRESSURE    ("73","01","Ciśnienie spalin",        "kPa","Ciśnienie w układzie wydechowym",     ReadPriority.MEDIUM),
    TURBO_RPM           ("74","01","RPM turbo",               "rpm","Obroty turbosprężarki",               ReadPriority.HIGH),
    TURBO_TEMP_IN       ("75","01","Temp. wejścia turbo",    "°C", "Temperatura wejścia turbosprężarki",  ReadPriority.MEDIUM),
    TURBO_TEMP_OUT      ("76","01","Temp. wyjścia turbo",    "°C", "Temperatura wyjścia turbosprężarki",  ReadPriority.MEDIUM),
    INTERCOOLER_TEMP    ("77","01","Temp. intercoolera",     "°C", "Temperatura intercooolera",            ReadPriority.MEDIUM),
    EGT_SENSOR_1        ("78","01","Czujnik EGT 1",          "°C", "Czujnik temperatury spalin nr 1",     ReadPriority.MEDIUM),
    EGT_SENSOR_2        ("79","01","Czujnik EGT 2",          "°C", "Czujnik temperatury spalin nr 2",     ReadPriority.MEDIUM),
    DPF_TEMP_IN         ("7A","01","Temp. wejścia DPF",      "°C", "Temperatura wejścia filtra DPF",      ReadPriority.MEDIUM),
    DPF_TEMP_OUT        ("7B","01","Temp. wyjścia DPF",      "°C", "Temperatura wyjścia filtra DPF",      ReadPriority.MEDIUM),
    DPF_STATUS          ("7C","01","Status DPF",              "-",  "Status i regeneracja filtra DPF",     ReadPriority.LOW),
    NOX_NTE_STATUS      ("7D","01","Status NOx NTE",          "-",  "Status kontroli NOx NTE",             ReadPriority.LOW),
    PM_NTE_STATUS       ("7E","01","Status PM NTE",           "-",  "Status kontroli cząstek NTE",         ReadPriority.LOW),
    ENGINE_RUN_TIME_EXT ("7F","01","Czas pracy (ext)",       "s",  "Czas pracy silnika rozszerzony",      ReadPriority.MEDIUM),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy 81–8C (EOBD/rozszerzenia)
    // ═══════════════════════════════════════════════════════════════════════
    ENGINE_RUN_TIME_AECD("81","01","Czas AECD",              "s",  "Czas pracy urządzeń kontroli emisji", ReadPriority.LOW),
    ENGINE_RUN_TIME_AECD2("82","01","Czas AECD 2",           "s",  "Czas pracy urządzeń kontroli emisji 2",ReadPriority.LOW),
    NOX_SENSOR          ("83","01","Czujnik NOx",             "ppm","Stężenie NOx",                        ReadPriority.MEDIUM),
    MANIFOLD_SURFACE_TEMP("84","01","Temp. kolektora",       "°C", "Temperatura powierzchni kolektora",   ReadPriority.MEDIUM),
    NOX_REAGENT_SYSTEM  ("85","01","System NOx reagent",      "%",  "System reagenta NOx (AdBlue/SCR)",    ReadPriority.LOW),
    PM_SENSOR           ("86","01","Czujnik PM",              "mg/m3","Stężenie cząstek stałych",          ReadPriority.MEDIUM),
    INTAKE_MANIFOLD_PRESSURE("87","01","Ciśnienie kolektora","kPa","Ciśnienie absolutne kolektora dolot.",ReadPriority.HIGH),
    SCR_INDUCE_SYSTEM   ("88","01","System SCR",              "-",  "Status systemu SCR",                  ReadPriority.LOW),
    AECD_11_15          ("89","01","AECD 11-15",              "-",  "Liczniki urządzeń kontroli emisji",   ReadPriority.LOW),
    AECD_16_20          ("8A","01","AECD 16-20",              "-",  "Liczniki urządzeń kontroli emisji 2", ReadPriority.LOW),
    DIESEL_AFTERTREAT   ("8B","01","Obróbka końcowa diesel", "-",  "Status systemu obróbki spalin diesel", ReadPriority.LOW),
    O2_SENSOR_WIDE      ("8C","01","Szerokop. sonda O2",     "mA", "Szerokopasowy czujnik O2",            ReadPriority.HIGH),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 01 – PIDy A0–A6 (rozszerzenia SAE 2018+)
    // ═══════════════════════════════════════════════════════════════════════
    NOX_SENSOR_CORRECTED("A1","01","NOx koryg.",              "ppm","Skorygowane stężenie NOx",             ReadPriority.MEDIUM),
    CYLINDER_FUEL_RATE  ("A2","01","Zużycie paliwa cyl.",    "mg/str","Dawka paliwa na cykl cylindra",     ReadPriority.HIGH),
    EVAP_SYSTEM_VAPOR   ("A3","01","Ciśnienie EVAP sys.",    "Pa", "Ciśnienie układu EVAP",                ReadPriority.MEDIUM),
    TRANSMISSION_ACTUAL_GEAR("A4","01","Bieg skrzyni",        "-",  "Aktualny bieg skrzyni biegów",        ReadPriority.MEDIUM),
    DIESEL_EXHAUST_FLUID("A5","01","Płyn AdBlue",             "%",  "Poziom płynu AdBlue",                 ReadPriority.LOW),
    ODOMETER            ("A6","01","Przebieg",                "km", "Przebieg całkowity pojazdu",           ReadPriority.LOW),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 02 – Freeze Frame (zrzut danych w chwili błędu DTC)
    // Tryb 02 ma identyczne PIDy jak tryb 01, ale zwraca wartości z chwili
    // gdy ECU zapisało kod błędu. Dostępny tylko gdy jest aktywny DTC.
    // ═══════════════════════════════════════════════════════════════════════
    FF_ENGINE_RPM        ("0C","02","FF RPM",                 "rpm","[Freeze] Obroty silnika",              ReadPriority.ONCE),
    FF_VEHICLE_SPEED     ("0D","02","FF Prędkość",            "km/h","[Freeze] Prędkość pojazdu",            ReadPriority.ONCE),
    FF_ENGINE_LOAD       ("04","02","FF Obciążenie",          "%",  "[Freeze] Obciążenie silnika",          ReadPriority.ONCE),
    FF_COOLANT_TEMP      ("05","02","FF Temp. chłodnicy",     "°C", "[Freeze] Temperatura chłodnicy",       ReadPriority.ONCE),
    FF_THROTTLE_POS      ("11","02","FF Przepustnica",        "%",  "[Freeze] Pozycja przepustnicy",        ReadPriority.ONCE),
    FF_INTAKE_TEMP       ("0F","02","FF Temp. powietrza",     "°C", "[Freeze] Temp. powietrza na wlocie",   ReadPriority.ONCE),
    FF_MAF               ("10","02","FF Przepływ MAF",        "g/s","[Freeze] Masowy przepływ powietrza",   ReadPriority.ONCE),
    FF_SHORT_FUEL_TRIM_1 ("06","02","FF STFT bank 1",         "%",  "[Freeze] Krótkoterm. korekta paliwa",  ReadPriority.ONCE),
    FF_LONG_FUEL_TRIM_1  ("07","02","FF LTFT bank 1",         "%",  "[Freeze] Długoterm. korekta paliwa",   ReadPriority.ONCE),
    FF_FUEL_PRESSURE     ("0A","02","FF Ciśnienie paliwa",    "kPa","[Freeze] Ciśnienie w układzie paliwowym",ReadPriority.ONCE),
    FF_INTAKE_PRESSURE   ("0B","02","FF Ciśnienie dolotowe",  "kPa","[Freeze] Ciśnienie w kolektorze",      ReadPriority.ONCE),
    FF_TIMING_ADVANCE    ("0E","02","FF Wyprzedzenie zapłonu","°",  "[Freeze] Wyprzedzenie zapłonu",        ReadPriority.ONCE),
    FF_FUEL_LEVEL        ("2F","02","FF Poziom paliwa",       "%",  "[Freeze] Poziom paliwa w zbiorniku",   ReadPriority.ONCE),
    FF_BARO_PRESSURE     ("33","02","FF Ciśnienie atmosf.",   "kPa","[Freeze] Ciśnienie atmosferyczne",     ReadPriority.ONCE),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 06 – Wyniki testów komponentów (On-Board Monitor Tests)
    // ECU wykonuje cykliczne testy czujników i zapisuje wyniki.
    // Każdy test ma ID (TID), wartość zmierzoną, min i max.
    // Dostępne TIDy zależą od producenta – poniższe są standardowe SAE.
    // ═══════════════════════════════════════════════════════════════════════
    MON_O2_B1S1_RICH_TO_LEAN ("01","06","Test O2 B1S1 R→L",   "V",  "[Monitor] Próg O2 bank1 sensor1 bogaty→ubogi",    ReadPriority.ONCE),
    MON_O2_B1S1_LEAN_TO_RICH ("02","06","Test O2 B1S1 L→R",   "V",  "[Monitor] Próg O2 bank1 sensor1 ubogi→bogaty",    ReadPriority.ONCE),
    MON_O2_B2S1_RICH_TO_LEAN ("03","06","Test O2 B2S1 R→L",   "V",  "[Monitor] Próg O2 bank2 sensor1 bogaty→ubogi",    ReadPriority.ONCE),
    MON_O2_B2S1_LEAN_TO_RICH ("04","06","Test O2 B2S1 L→R",   "V",  "[Monitor] Próg O2 bank2 sensor1 ubogi→bogaty",    ReadPriority.ONCE),
    MON_O2_B1S2_MIN_V        ("05","06","Test O2 B1S2 min V",  "V",  "[Monitor] Min napięcie O2 bank1 sensor2",         ReadPriority.ONCE),
    MON_O2_B1S2_MAX_V        ("06","06","Test O2 B1S2 max V",  "V",  "[Monitor] Max napięcie O2 bank1 sensor2",         ReadPriority.ONCE),
    MON_O2_B2S2_MIN_V        ("07","06","Test O2 B2S2 min V",  "V",  "[Monitor] Min napięcie O2 bank2 sensor2",         ReadPriority.ONCE),
    MON_O2_B2S2_MAX_V        ("08","06","Test O2 B2S2 max V",  "V",  "[Monitor] Max napięcie O2 bank2 sensor2",         ReadPriority.ONCE),
    MON_EGR_FLOW_MIN         ("31","06","Test EGR min",         "%",  "[Monitor] Min przepływ EGR",                      ReadPriority.ONCE),
    MON_EGR_FLOW_MAX         ("32","06","Test EGR max",         "%",  "[Monitor] Max przepływ EGR",                      ReadPriority.ONCE),
    MON_CATALYST_B1_TEMP     ("21","06","Test kat. B1 temp.",   "°C", "[Monitor] Temperatura katalizatora bank1",        ReadPriority.ONCE),
    MON_CATALYST_B2_TEMP     ("22","06","Test kat. B2 temp.",   "°C", "[Monitor] Temperatura katalizatora bank2",        ReadPriority.ONCE),
    MON_EVAP_PURGE_FLOW      ("41","06","Test EVAP przepływ",   "%",  "[Monitor] Przepływ zaworu oczyszczania EVAP",     ReadPriority.ONCE),
    MON_EVAP_LEAK_04         ("42","06","Test EVAP szczelność", "Pa", "[Monitor] Test szczelności układu EVAP 0.040\"",  ReadPriority.ONCE),
    MON_EVAP_LEAK_020        ("43","06","Test EVAP szczelność 2","Pa","[Monitor] Test szczelności układu EVAP 0.020\"",  ReadPriority.ONCE),
    MON_O2_HEATER_B1S1       ("51","06","Test grzejnika O2 B1S1","A", "[Monitor] Prąd grzejnika sondy O2 bank1 sensor1",ReadPriority.ONCE),
    MON_O2_HEATER_B1S2       ("52","06","Test grzejnika O2 B1S2","A", "[Monitor] Prąd grzejnika sondy O2 bank1 sensor2",ReadPriority.ONCE),
    MON_O2_HEATER_B2S1       ("53","06","Test grzejnika O2 B2S1","A", "[Monitor] Prąd grzejnika sondy O2 bank2 sensor1",ReadPriority.ONCE),
    MON_O2_HEATER_B2S2       ("54","06","Test grzejnika O2 B2S2","A", "[Monitor] Prąd grzejnika sondy O2 bank2 sensor2",ReadPriority.ONCE),

    // ═══════════════════════════════════════════════════════════════════════
    // TRYB 09 – informacje o pojeździe (wszystkie ONCE)
    // ═══════════════════════════════════════════════════════════════════════
    VIN                 ("02","09","VIN",                     "-",  "Numer identyfikacyjny pojazdu",        ReadPriority.ONCE),
    CALIBRATION_ID      ("04","09","ID kalibracji ECU",       "-",  "ID kalibracji oprogramowania ECU",     ReadPriority.ONCE),
    CVN                 ("06","09","CVN",                     "-",  "Numer weryfikacji kalibracji",         ReadPriority.ONCE),
    PERF_TRACKING       ("08","09","Śledzenie wydajności",    "-",  "Śledzenie wydajności OBD",             ReadPriority.ONCE),
    ECU_NAME            ("0A","09","Nazwa ECU",               "-",  "Nazwa modułu sterującego",             ReadPriority.ONCE),
    ESN                 ("0D","09","Numer seryjny silnika",   "-",  "Numer seryjny silnika (ESN)",          ReadPriority.ONCE),
    ;

}

enum class ObdCategory(val displayName: String, val pids: List<ObdCommand>) {
    ENGINE("Silnik", listOf(
        ObdCommand.ENGINE_RPM, ObdCommand.ENGINE_LOAD, ObdCommand.COOLANT_TEMP,
        ObdCommand.ENGINE_OIL_TEMP, ObdCommand.TIMING_ADVANCE, ObdCommand.RUN_TIME,
        ObdCommand.DRIVER_DEMAND_TORQUE, ObdCommand.ACTUAL_TORQUE, ObdCommand.REFERENCE_TORQUE
    )),
    MOTION("Ruch", listOf(
        ObdCommand.VEHICLE_SPEED, ObdCommand.THROTTLE_POS, ObdCommand.RELATIVE_THROTTLE_POS,
        ObdCommand.ABSOLUTE_LOAD, ObdCommand.ACCELERATOR_POS_D, ObdCommand.ACCELERATOR_POS_REL,
        ObdCommand.TRANSMISSION_ACTUAL_GEAR
    )),
    FUEL("Paliwo", listOf(
        ObdCommand.FUEL_LEVEL, ObdCommand.FUEL_RATE, ObdCommand.FUEL_PRESSURE,
        ObdCommand.FUEL_RAIL_PRESSURE_DIRECT, ObdCommand.FUEL_RAIL_PRESSURE_ABS,
        ObdCommand.SHORT_FUEL_TRIM_1, ObdCommand.LONG_FUEL_TRIM_1,
        ObdCommand.SHORT_FUEL_TRIM_2, ObdCommand.LONG_FUEL_TRIM_2,
        ObdCommand.FUEL_INJECT_TIMING, ObdCommand.COMMANDED_EQUIV_RATIO,
        ObdCommand.ETHANOL_PERCENT, ObdCommand.FUEL_TYPE, ObdCommand.CYLINDER_FUEL_RATE
    )),
    AIR("Powietrze", listOf(
        ObdCommand.MAF, ObdCommand.INTAKE_PRESSURE, ObdCommand.BARO_PRESSURE,
        ObdCommand.INTAKE_TEMP, ObdCommand.AMBIENT_AIR_TEMP, ObdCommand.INTAKE_MANIFOLD_PRESSURE
    )),
    OXYGEN("Tlen / Lambda", listOf(
        ObdCommand.O2_SENSOR_1_1, ObdCommand.O2_SENSOR_1_2,
        ObdCommand.O2_SENSOR_2_1, ObdCommand.O2_SENSOR_2_2,
        ObdCommand.O2_S1_WR_CURRENT, ObdCommand.O2_S2_WR_CURRENT,
        ObdCommand.O2_S1_WR_LAMBDA, ObdCommand.O2_S2_WR_LAMBDA
    )),
    CATALYST("Katalizator / Spaliny", listOf(
        ObdCommand.CATALYST_TEMP_B1S1, ObdCommand.CATALYST_TEMP_B2S1,
        ObdCommand.CATALYST_TEMP_B1S2, ObdCommand.CATALYST_TEMP_B2S2,
        ObdCommand.EXHAUST_GAS_TEMP_1, ObdCommand.EXHAUST_GAS_TEMP_2,
        ObdCommand.NOX_SENSOR, ObdCommand.PM_SENSOR
    )),
    TURBO("Turbo / DPF", listOf(
        ObdCommand.BOOST_PRESSURE, ObdCommand.TURBO_RPM,
        ObdCommand.TURBO_TEMP_IN, ObdCommand.TURBO_TEMP_OUT,
        ObdCommand.INTERCOOLER_TEMP, ObdCommand.DPF_DIFFERENTIAL_PRESSURE,
        ObdCommand.DPF_TEMP_IN, ObdCommand.DPF_TEMP_OUT, ObdCommand.DPF_STATUS
    )),
    DIAGNOSTICS("Diagnostyka", listOf(
        ObdCommand.STATUS, ObdCommand.DISTANCE_W_MIL, ObdCommand.DISTANCE_SINCE_DTC_CLEAR,
        ObdCommand.TIME_WITH_MIL, ObdCommand.TIME_SINCE_DTC_CLEARED,
        ObdCommand.CONTROL_MODULE_VOLTAGE, ObdCommand.OBD_COMPLIANCE,
        ObdCommand.MONITOR_STATUS_DRIVE, ObdCommand.ODOMETER
    )),
    FREEZE_FRAME("Freeze Frame", listOf(
        ObdCommand.FF_ENGINE_RPM, ObdCommand.FF_VEHICLE_SPEED, ObdCommand.FF_ENGINE_LOAD,
        ObdCommand.FF_COOLANT_TEMP, ObdCommand.FF_THROTTLE_POS, ObdCommand.FF_INTAKE_TEMP,
        ObdCommand.FF_MAF, ObdCommand.FF_SHORT_FUEL_TRIM_1, ObdCommand.FF_LONG_FUEL_TRIM_1,
        ObdCommand.FF_FUEL_PRESSURE, ObdCommand.FF_INTAKE_PRESSURE, ObdCommand.FF_TIMING_ADVANCE,
        ObdCommand.FF_FUEL_LEVEL, ObdCommand.FF_BARO_PRESSURE
    )),
    MONITORS("Testy monitorów", listOf(
        ObdCommand.MON_O2_B1S1_RICH_TO_LEAN, ObdCommand.MON_O2_B1S1_LEAN_TO_RICH,
        ObdCommand.MON_O2_B2S1_RICH_TO_LEAN, ObdCommand.MON_O2_B2S1_LEAN_TO_RICH,
        ObdCommand.MON_O2_B1S2_MIN_V, ObdCommand.MON_O2_B1S2_MAX_V,
        ObdCommand.MON_O2_B2S2_MIN_V, ObdCommand.MON_O2_B2S2_MAX_V,
        ObdCommand.MON_O2_HEATER_B1S1, ObdCommand.MON_O2_HEATER_B1S2,
        ObdCommand.MON_O2_HEATER_B2S1, ObdCommand.MON_O2_HEATER_B2S2,
        ObdCommand.MON_EGR_FLOW_MIN, ObdCommand.MON_EGR_FLOW_MAX,
        ObdCommand.MON_CATALYST_B1_TEMP, ObdCommand.MON_CATALYST_B2_TEMP,
        ObdCommand.MON_EVAP_PURGE_FLOW, ObdCommand.MON_EVAP_LEAK_04, ObdCommand.MON_EVAP_LEAK_020
    )),
    VEHICLE_INFO("Info o pojeździe", listOf(
        ObdCommand.VIN, ObdCommand.ECU_NAME, ObdCommand.CALIBRATION_ID,
        ObdCommand.CVN, ObdCommand.ESN
    ))
}
