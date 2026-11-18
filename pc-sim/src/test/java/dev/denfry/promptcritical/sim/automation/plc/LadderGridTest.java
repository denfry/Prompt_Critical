package dev.denfry.promptcritical.sim.automation.plc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.denfry.promptcritical.sim.automation.plc.LadderGrid.Cell;
import dev.denfry.promptcritical.sim.automation.plc.LadderGrid.Coil;
import dev.denfry.promptcritical.sim.automation.plc.LadderGrid.Kind;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Сетка графического редактора LD: сетка → текст → процессор, текст → сетка, поток энергии. */
class LadderGridTest {
    /** Пуск-стоп с самоподхватом и реле времени — как в документации {@link LdCompiler}. */
    static LadderGrid startStop() {
        LadderGrid g = new LadderGrid();
        LadderGrid.Rung r = g.addRung();
        r.cells[0][0] = new Cell(Kind.NO, "%IX0.0", "");
        r.cells[1][0] = new Cell(Kind.NO, "%QX0.0", "");
        r.cells[0][1] = new Cell(Kind.NC, "%IX0.1", "");
        r.coil = Coil.OTE;
        r.coilOperand = "%QX0.0";
        LadderGrid.Rung t = g.addRung();
        t.cells[0][0] = new Cell(Kind.NO, "%QX0.0", "");
        t.cells[0][1] = new Cell(Kind.TON, "T1", "T#5S");
        t.coilOperand = "%QX0.1";
        return g;
    }

    @Test
    void gridBecomesLadderTextThatTheProcessorRuns() {
        String text = startStop().toText();
        assertThat(text).isEqualTo("(*LD*)\nBST XIC(%IX0.0) NXB XIC(%QX0.0) BND XIO(%IX0.1) OTE(%QX0.0)\n"
                + "XIC(%QX0.0) TON(T1, T#5S) OTE(%QX0.1)\n");
        PlcCpu cpu = new PlcCpu();
        assertThat(cpu.load(text).ok()).isTrue();
        boolean[] di = new boolean[IlCompiler.IX];
        double[] ai = new double[IlCompiler.IW];
        di[0] = true; // ПУСК; СТОП (%IX0.1) не нажат — размыкающий контакт замкнут
        cpu.scan(0.05, true, di, ai);
        di[0] = false;
        cpu.scan(0.05, true, di, ai);
        assertThat(cpu.output(0)).as("самоподхват держит выход после отпускания ПУСК").isTrue();
        for (int k = 0; k < 110; k++) cpu.scan(0.05, true, di, ai);
        assertThat(cpu.output(1)).as("реле времени через 5 с").isTrue();
    }

    @Test
    void ladderTextComesBackIntoTheSameGrid() {
        String text = startStop().toText();
        assertThat(LadderGrid.fromText(text).toText()).isEqualTo(text);
        assertThatThrownBy(() -> LadderGrid.fromText("BST XIC(a) NXB BST XIC(b) NXB XIC(c) BND BND OTE(q)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LadderGrid.fromText("XIC(a) OTE(q) OTE(r)")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void powerFlowsThroughTheSealInBranch() {
        LadderGrid.Rung r = startStop().rungs().getFirst();
        boolean[][] f = LadderGrid.flow(r, Map.of("%IX0.0", false, "%QX0.0", true, "%IX0.1", false)::get);
        assertThat(f[0][0]).as("ПУСК отпущен").isFalse();
        assertThat(f[1][0]).as("питание идёт через самоподхват").isTrue();
        assertThat(f[LadderGrid.ROWS][0]).as("катушка под током").isTrue();
        boolean[][] stop = LadderGrid.flow(r, Map.of("%IX0.0", false, "%QX0.0", true, "%IX0.1", true)::get);
        assertThat(stop[LadderGrid.ROWS][0]).as("СТОП разрывает цепь").isFalse();
    }
}
