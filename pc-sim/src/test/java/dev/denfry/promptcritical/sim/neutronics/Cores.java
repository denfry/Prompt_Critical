package dev.denfry.promptcritical.sim.neutronics;

import dev.denfry.promptcritical.sim.nuclide.NuclideVector;
import java.util.Arrays;

/** Тестовые зоны для нодальной модели: прямоугольные сетки узлов. */
final class Cores {
    private Cores() {}

    /** Соседи 6N (−x, +x, −y, +y, −z, +z) прямоугольной сетки, снаружи — вакуум. */
    static int[] grid(int nx, int ny, int nz) {
        int[] nb = new int[6 * nx * ny * nz];
        for (int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    int i = index(nx, ny, x, y, z);
                    nb[6 * i] = x > 0 ? i - 1 : -1;
                    nb[6 * i + 1] = x < nx - 1 ? i + 1 : -1;
                    nb[6 * i + 2] = y > 0 ? i - nx : -1;
                    nb[6 * i + 3] = y < ny - 1 ? i + nx : -1;
                    nb[6 * i + 4] = z > 0 ? i - nx * ny : -1;
                    nb[6 * i + 5] = z < nz - 1 ? i + nx * ny : -1;
                }
            }
        }
        return nb;
    }

    static int index(int nx, int ny, int x, int y, int z) {
        return x + nx * (y + ny * z);
    }

    static <T> T[] filled(T[] a, T v) {
        Arrays.fill(a, v);
        return a;
    }

    static NodalFluxSolver solve(CellComposition[] cells, SixFactor[] constants, int[] neighbors, boolean correction) {
        NodalFluxSolver s = new NodalFluxSolver();
        s.setContrastCorrection(correction);
        s.rebuild(cells, neighbors);
        s.updateConstants(constants);
        if (!s.converge()) throw new AssertionError("nodal problem did not converge");
        return s;
    }

    static NuclideVector freshFuel(String lattice) {
        return NuclideVector.uranium(1000, LatticeDesigns.referenceEnrichment(lattice));
    }
}
