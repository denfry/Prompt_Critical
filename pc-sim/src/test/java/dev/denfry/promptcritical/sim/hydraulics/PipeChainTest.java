package dev.denfry.promptcritical.sim.hydraulics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Правила слияния цепочек труб (01 §1.12.1). */
class PipeChainTest {
    private static PipeChain.Segment pipe(double length, double d, double rise) {
        return PipeChain.Segment.circular(length, d, Friction.ROUGHNESS_STEEL, rise, 0);
    }

    @Test
    void splitsByLengthRiseAndDiameter() {
        List<PipeChain.Segment> chain = new ArrayList<>();
        for (int k = 0; k < 60; k++) chain.add(pipe(1, 0.4, 0));
        assertThat(PipeChain.split(chain)).hasSize(2).allSatisfy(r -> assertThat(r).hasSize(30));

        List<PipeChain.Segment> riser = new ArrayList<>();
        for (int k = 0; k < 10; k++) riser.add(pipe(1, 0.2, 1));
        assertThat(PipeChain.split(riser)).extracting(List::size).containsExactly(4, 4, 2);

        List<PipeChain.Segment> reducer = List.of(pipe(1, 0.4, 0), pipe(1, 0.3, 0), pipe(1, 0.15, 0), pipe(1, 0.15, 0));
        assertThat(PipeChain.split(reducer)).extracting(List::size).containsExactly(2, 2);
    }

    @Test
    void mixedDiametersReduceToSmallestSection() {
        PipeChain.Segment big = pipe(2, 0.4, 1), small = pipe(3, 0.2, -0.5);
        PipeChain.Geometry g = PipeChain.contract(List.of(big, small));
        assertThat(g.area()).isEqualTo(small.area());
        assertThat(g.inertia()).isCloseTo(2 / big.area() + 3 / small.area(), within(1e-9));
        assertThat(g.length()).isEqualTo(5);
        // 2 м трубы ⌀0,4 стоят по трению как 2·(0,2/0,4)·(1/4)² м трубы ⌀0,2
        assertThat(g.frictionLength()).isCloseTo(3 + 2 * 0.5 / 16, within(1e-12));
        assertThat(g.rise()).isCloseTo(0.5, within(1e-12));
        assertThat(g.volume()).isCloseTo(big.volume() + small.volume(), within(1e-12));
    }
}
