package dev.denfry.promptcritical.sim.automation.plc;

/**
 * Языки программы ПЛК (IEC 61131-3): IL, ST, LD, FBD, SFC. Все компилируются в одни и те же инструкции
 * {@link IlProgram}: LD, FBD и SFC переводятся в ST ({@link LdCompiler}, {@link FbdCompiler}, {@link SfcCompiler}).
 * Язык определяется по тексту: прагма {@code (*LD*)}, {@code (*FBD*)}, {@code (*SFC*)} в первой строке, ST — по
 * {@link StCompiler#isSt}, иначе IL. Так старые сохранения на IL грузятся как прежде.
 */
public enum PlcLanguage {
    IL, ST, LD, FBD, SFC;

    public static PlcLanguage of(String source) {
        if (LdCompiler.isLd(source)) return LD;
        if (FbdCompiler.isFbd(source)) return FBD;
        if (SfcCompiler.isSfc(source)) return SFC;
        return StCompiler.isSt(source) ? ST : IL;
    }

    public static IlProgram compile(String source) {
        return switch (of(source)) {
            case IL -> IlCompiler.compile(source);
            case ST -> StCompiler.compile(source);
            case LD -> LdCompiler.compile(source);
            case FBD -> FbdCompiler.compile(source);
            case SFC -> SfcCompiler.compile(source);
        };
    }
}
