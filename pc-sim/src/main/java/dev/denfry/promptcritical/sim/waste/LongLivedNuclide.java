package dev.denfry.promptcritical.sim.waste;

/**
 * Долгоживущие радионуклиды отходов, которых нет в таблице γ-излучателей {@link dev.denfry.promptcritical.sim.radiation.Emitter}:
 * на дозу у предмета они почти не влияют (чистые β- и α-излучатели), зато определяют безопасность хранилища через
 * тысячи лет. Это «трудноизмеряемые» нуклиды (ISO 21238, IAEA NW-T-1.18): их не видно гамма-спектрометром, активность
 * пересчитывают от ключевых Cs-137 и Co-60 через {@link ScalingFactors}.
 *
 * <p>Периоды полураспада — ENSDF; тепло — средняя энергия распада, уходящая в вещество (α целиком плюс отдача, β —
 * средняя энергия), МэВ; дозовые коэффициенты при поступлении с пищей и водой для взрослых — ICRP Publication 72
 * (1996), Зв/Бк. Am-241 распадается в Np-237 — это единственная цепочка, существенная на таком горизонте.
 */
public enum LongLivedNuclide {
    TC99(2.111e5, 0.0846, Element.TC, 6.4e-10, false, null),
    I129(1.57e7, 0.064, Element.I, 1.1e-7, false, null),
    CS135(2.3e6, 0.0669, Element.CS, 2.0e-9, false, null),
    NI63(101.2, 0.0171, Element.NI, 1.5e-10, false, null),
    C14(5700, 0.0495, Element.C, 5.8e-10, false, null),
    NP237(2.144e6, 4.957, Element.NP, 1.1e-7, true, null),
    PU239(24_110, 5.245, Element.PU, 2.5e-7, true, null),
    PU240(6561, 5.255, Element.PU, 2.5e-7, true, null),
    AM241(432.6, 5.637, Element.AM, 2.0e-7, true, NP237);

    public static final double YEAR_S = 365.25 * 86_400;

    public final double halfLifeY, heatMeV, doseSvPerBq;
    public final Element element;
    /** Трансурановый α-излучатель (колонка «трансурановые» ОСПОРБ). */
    public final boolean transuranic;
    public final LongLivedNuclide daughter;

    LongLivedNuclide(double halfLifeY, double heatMeV, Element element, double doseSvPerBq, boolean transuranic, LongLivedNuclide daughter) {
        this.halfLifeY = halfLifeY;
        this.heatMeV = heatMeV;
        this.element = element;
        this.doseSvPerBq = doseSvPerBq;
        this.transuranic = transuranic;
        this.daughter = daughter;
    }

    /** Постоянная распада, 1/с. */
    public double lambda() {
        return Math.log(2) / (halfLifeY * YEAR_S);
    }
}
