package dev.denfry.promptcritical.sim.automation.plc;

/**
 * Языки программы ПЛК (IEC 61131-3). Оба компилируются в одни и те же инструкции {@link IlProgram}; язык
 * определяется по тексту — {@link StCompiler#isSt}, иначе IL. Так старые сохранения на IL грузятся как прежде.
 */
public enum PlcLanguage {
    IL, ST;

    public static PlcLanguage of(String source) {
        return StCompiler.isSt(source) ? ST : IL;
    }

    public static IlProgram compile(String source) {
        return of(source) == ST ? StCompiler.compile(source) : IlCompiler.compile(source);
    }
}
