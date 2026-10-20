package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Синхроноскоп, бросок момента при включении (07 §7.3.5) и автомат выключателя (§7.4.7). */
class GridSynchronizationTest {
    static final double DT = 0.05;

    /** Источник бесконечной мощности: x ≈ 0, H огромна, ручное возбуждение. */
    static SynchronousMachine infiniteSource(double vRatedV) {
        return new SynchronousMachine(new MachineParams(1e9, vRatedV, 1, 1e-4, 1e-4, 1e-4, 1e9, 1e7, 0, 0, 1),
                ExciterSpec.manual(), new PrimeMover.CreateShaft(1, 1e9));
    }

    /**
     * Сеть 6,3 кВ от шин бесконечной мощности; {@code shaft_generator} 1 МВ·А 0,4 кВ на валу Create
     * через трансформатор 1 МВ·А (u_k 5,5 %) с масляным выключателем, выключатель отключён.
     */
    static final class SyncRig {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final BusId gen, net;
        final BranchId cb;
        final SynchronousMachine m;
        final MachineId id;
        final GridOutputs out = new GridOutputs();
        final List<SyncClosing> closings = new ArrayList<>();
        final List<BreakerState> states = new ArrayList<>();
        double shaftPowerW;
        final GridInputs in = new GridInputs() {
            @Override
            public double createShaftPowerW(int machine) {
                return machine == id.index() ? shaftPowerW : Double.NaN;
            }
        };

        SyncRig(boolean shearPin) {
            net = g.addBus(VoltageClass.MV_6_3, "net");
            gen = g.addBus(VoltageClass.LV_0_4, "gen");
            g.addMachine(infiniteSource(6300), net);
            cb = g.addBranch(new Branch.Transformer(net, gen, 1e6, 0.055, 10e3, 2e3, 0.01, TapChanger.NONE, null));
            g.attachBreaker(cb, BreakerSpec.oil());
            g.setBreaker(cb, false);
            m = new SynchronousMachine(MachineParams.shaftGenerator(1e6, 400), ExciterSpec.rotating(),
                    new PrimeMover.CreateShaft(20, 0.8e6));
            m.setVoltageReference(1.02); // генератор не ниже сети (окно реле 25)
            m.setShearPinCoupling(shearPin);
            id = g.addMachine(m, gen);
            g.initializePowerFlow();
            // толчок мощностью Create на один тик: скольжение ≈ 0,05 Гц, дальше вал держит скорость
            shaftPowerW = 0.025e6;
            step();
            shaftPowerW = 0;
            for (int k = 0; k < 40; k++) step();
        }

        void step() {
            g.stepFast(DT, in, out);
            g.drainEvents(new GridEventSink() {
                @Override
                public void syncClosed(SyncClosing c) {
                    closings.add(c);
                }

                @Override
                public void breakerStateChanged(int branch, BreakerState state) {
                    states.add(state);
                }
            });
        }

        /** Ждать, пока угол синхроноскопа не пройдёт {@code deg} (по модулю). */
        void waitAngle(double deg) {
            for (int k = 0; k < 2000 && Math.toDegrees(Math.abs(g.syncScope(cb).angleRad())) < deg; k++) step();
        }

        /** Включить и дождаться касания контактов. */
        SyncClosing close() {
            assertThat(g.operateBreaker(cb, true)).isEqualTo(BreakerResponse.ACCEPTED);
            for (int k = 0; k < 20 && g.breakerState(cb) != BreakerState.CLOSED; k++) step();
            assertThat(closings).hasSize(1);
            return closings.getFirst();
        }

        /** X''_Σ на S_ном генератора: x''_d + z_T (сеть ≈ 0). */
        double xSigmaPP() {
            double rT = 10e3 / 1e6, xT = Math.sqrt(0.055 * 0.055 - rT * rT);
            return Math.hypot(rT, 0.20 + xT);
        }
    }

    @Test
    @Tag("T7.6")
    void torqueOnNonSynchronousClosing() {
        assertThat(new SyncScope(0, Math.toRadians(10), 0, 0).closingTorquePu(1, 0.35)).isCloseTo(0.50, within(0.005));
        assertThat(new SyncScope(0, Math.toRadians(120), 0, 0).closingTorquePu(1, 0.35)).isCloseTo(4.95, within(0.0495));
    }

    @Test
    @Tag("T7.7")
    void advanceAngle() {
        SyncScope s = SyncScope.of(50.1, 50, 0, 0, 1, 1, 0.08);
        assertThat(Math.toDegrees(s.advanceAngleRad())).isCloseTo(2.88, within(0.01));
        assertThat(s.slipHz()).isCloseTo(0.1, within(1e-12));
    }

    @Test
    void scopeAnglesWrapAndWindowNeedsGeneratorNotLower() {
        SyncScope s = SyncScope.of(50, 50, Math.toRadians(170), Math.toRadians(-170), 1.0, 1.0, 0);
        assertThat(Math.toDegrees(s.angleRad())).isCloseTo(-20, within(1e-9));
        assertThat(SyncScope.of(50.05, 50, 0.1, 0, 1.02, 1, 0).inWindow(Math.toRadians(10), 0.05, 0.067)).isTrue();
        assertThat(SyncScope.of(50.05, 50, 0.1, 0, 0.98, 1, 0).inWindow(Math.toRadians(10), 0.05, 0.067)).isFalse();
        assertThat(SyncScope.of(50.2, 50, 0.1, 0, 1.02, 1, 0).inWindow(Math.toRadians(10), 0.05, 0.067)).isFalse();
        assertThat(SyncScope.of(50.1, 50, 0, 0, 1, 1, 0.5).angleAtClosingRad()).isCloseTo(Math.PI / 10, within(1e-12));
    }

    @Test
    @Tag("T7.25")
    void closingAt120DegreesWithoutSyncCheckShearsTheCouplingAndOverstressesCreate() {
        SyncRig r = new SyncRig(false);
        assertThat(r.m.state()).isEqualTo(MachineState.EXCITED);
        assertThat(r.g.syncScope(r.cb).slipHz()).isCloseTo(0.052, within(0.005));
        r.waitAngle(120);
        SyncClosing c = r.close();
        double dTheta = Math.abs(c.scope().angleRad());
        assertThat(Math.toDegrees(dTheta)).isBetween(125.0, 132.0); // + угол за 0,45 с хода
        // E'' ≈ 1,02 (уставка генератора), V = 1
        assertThat(c.torquePu()).isCloseTo(c.scope().closingTorquePu(1.02, r.xSigmaPP()), within(0.02 * c.torquePu()));
        assertThat(c.torquePu()).isGreaterThan(5); // ≥ grid.coupling_shear_pu
        assertThat(c.result()).isEqualTo(SyncResult.SHEARED);
        assertThat(c.createShockW()).isCloseTo(c.torquePu() * 1e6, within(0.01 * c.torquePu() * 1e6));
        assertThat(r.m.couplingSheared()).isTrue();
        assertThat(r.m.statorDamage()).isEqualTo(0.1);
        // муфта срезана: мощность Create до генератора не доходит
        r.shaftPowerW = 0.5e6;
        for (int k = 0; k < 400; k++) r.step();
        assertThat(r.m.mechanicalPowerW()).isZero();
        assertThat(Math.abs(r.m.electricalPowerW())).isLessThan(0.05e6);
    }

    @Test
    @Tag("T7.25")
    void syncCheckRelayBlocksClosingOutOfWindowAndAllowsAtTheRightMoment() {
        SyncRig r = new SyncRig(false);
        r.g.setSyncCheck(r.cb, true);
        r.waitAngle(120);
        assertThat(r.g.operateBreaker(r.cb, true)).isEqualTo(BreakerResponse.INTERLOCK);
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.OPEN);
        // ждём, пока стрелка подойдёт к «12 часам» с опережением
        for (int k = 0; k < 2000; k++) {
            SyncScope s = r.g.syncScope(r.cb);
            if (Math.abs(s.angleAtClosingRad()) < Math.toRadians(1)) break;
            r.step();
        }
        SyncClosing c = r.close();
        assertThat(Math.toDegrees(Math.abs(c.scope().angleRad()))).isLessThan(2.5);
        assertThat(c.result()).isEqualTo(SyncResult.OK);
        assertThat(c.torquePu()).isLessThan(0.3);
        for (int k = 0; k < 200; k++) r.step();
        assertThat(r.m.poleSlips()).isZero();
        assertThat(r.m.shaftDamage()).isZero();
        assertThat(r.m.state()).isEqualTo(MachineState.SYNCHRONIZED);
    }

    @Test
    void closingAt60DegreesDamagesTheShaftOrBreaksTheShearPin() {
        SyncRig r = new SyncRig(false);
        r.waitAngle(52);
        SyncClosing c = r.close();
        double t = c.torquePu();
        assertThat(t).isBetween(3.0, 5.0);
        assertThat(c.result()).isEqualTo(SyncResult.DAMAGE);
        assertThat(c.shaftDamage()).isCloseTo(Math.pow((t - 1.5) / 3.5, 3), within(1e-12));
        assertThat(r.m.shaftDamage()).isEqualTo(c.shaftDamage());
        assertThat(c.createShockW()).isPositive();

        SyncRig pin = new SyncRig(true);
        pin.waitAngle(52);
        SyncClosing p = pin.close();
        assertThat(p.result()).isEqualTo(SyncResult.SHEARED);
        assertThat(p.createShockW()).isZero(); // штифт спас вал Create
        assertThat(p.shaftDamage()).isCloseTo(Math.pow(0.5 / 3.5, 3), within(1e-12));
        assertThat(pin.m.couplingSheared()).isTrue();
        pin.m.setShearPinCoupling(true); // новая муфта
        assertThat(pin.m.couplingSheared()).isFalse();
    }

    @Test
    void repeatedBadClosingsBreakTheShaft() {
        SyncRig r = new SyncRig(false);
        for (int n = 0; n < 4 && r.m.state() != MachineState.DAMAGED; n++) {
            r.waitAngle(52);
            r.closings.clear();
            r.close();
            r.g.setBreaker(r.cb, false);
            r.shaftPowerW = 0.025e6;
            r.step(); // снова скольжение
            r.shaftPowerW = 0;
            for (int k = 0; k < 40; k++) r.step();
        }
        assertThat(r.m.shaftDamage()).isGreaterThanOrEqualTo(1);
        assertThat(r.m.state()).isEqualTo(MachineState.DAMAGED);
        assertThat(r.m.couplingSheared()).isTrue();
        assertThatThrownBy(r.m::repairCoupling).isInstanceOf(IllegalStateException.class);
    }

    /** Шины бесконечной мощности 6,3 кВ — линия x — шина с КЗ; выключатель на линии. */
    static final class FaultRig {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final BusId far;
        final BranchId cb;
        final GridOutputs out = new GridOutputs();

        FaultRig(double xPu) {
            BusId src = g.addBus(VoltageClass.MV_6_3, "src");
            far = g.addBus(VoltageClass.MV_6_3, "far");
            g.addMachine(infiniteSource(6300), src);
            cb = g.addBranch(new Branch.Line(src, far, 0, xPu, 0, 1000, null, 1));
            g.addLoad(LoadModel.constantImpedance(1e6, 0, LoadKind.HEATER), far);
            g.attachBreaker(cb, BreakerSpec.oil());
            g.initializePowerFlow();
        }

        void ticks(int n) {
            for (int k = 0; k < n; k++) g.stepFast(DT, GridInputs.NONE, out);
        }
    }

    @Test
    void breakerTravelTimesAndFreeTrip() {
        FaultRig r = new FaultRig(0.1);
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.CLOSED);
        assertThat(r.g.operateBreaker(r.cb, false)).isEqualTo(BreakerResponse.ACCEPTED);
        r.ticks(1);
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.OPENING);
        assertThat(r.g.operateBreaker(r.cb, true)).isEqualTo(BreakerResponse.BUSY);
        r.ticks(1); // t_откл = 0,10 с
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.OPEN);
        r.ticks(1);
        assertThat(r.g.island(r.g.islandOf(r.far)).energized()).isFalse();

        r.g.operateBreaker(r.cb, true);
        r.ticks(8);
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.CLOSING);
        r.ticks(1); // t_вкл = 0,45 с
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.CLOSED);
        r.ticks(1);
        assertThat(r.g.busVoltagePu(r.far)).isGreaterThan(0.9);

        r.g.operateBreaker(r.cb, false);
        r.ticks(2);
        r.g.operateBreaker(r.cb, true);
        r.ticks(3);
        assertThat(r.g.operateBreaker(r.cb, false)).isEqualTo(BreakerResponse.ACCEPTED); // свободное расцепление
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.OPEN);
        r.ticks(20);
        assertThat(r.g.isBreakerClosed(r.cb)).isFalse();
    }

    @Test
    void withoutControlPowerTheBreakerCannotTrip() {
        FaultRig r = new FaultRig(0.1);
        r.g.setBreakerControlPower(r.cb, false);
        assertThat(r.g.operateBreaker(r.cb, false)).isEqualTo(BreakerResponse.NO_CONTROL_POWER);
        r.ticks(10);
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.CLOSED);
        r.g.setBreakerControlPower(r.cb, true);
        assertThat(r.g.operateBreaker(r.cb, false)).isEqualTo(BreakerResponse.ACCEPTED);
    }

    @Test
    void currentAboveRatingIsNotInterrupted() {
        FaultRig r = new FaultRig(0.01); // I_КЗ ≈ 916 кА ≫ 20 кА
        r.g.applyFault(r.far, FaultType.THREE_PHASE, 0);
        r.ticks(2);
        assertThat(r.g.branchCurrentA(r.cb)).isGreaterThan(20e3);
        r.g.operateBreaker(r.cb, false);
        r.ticks(2);
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.FAILED_TO_INTERRUPT);
        assertThat(r.g.isBreakerClosed(r.cb)).isTrue();
        assertThat(r.g.operateBreaker(r.cb, false)).isEqualTo(BreakerResponse.UNAVAILABLE);
        r.g.repairBreaker(r.cb);
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.OPEN);
        assertThat(r.g.isBreakerClosed(r.cb)).isFalse();
    }

    @Test
    void faultInterruptionsWearTheContactsUntilService() {
        FaultRig r = new FaultRig(1.0); // I_КЗ ≈ 9,2 кА: отключается, но изнашивает
        r.g.applyFault(r.far, FaultType.THREE_PHASE, 0);
        List<BreakerState> seen = new ArrayList<>();
        GridEventSink sink = new GridEventSink() {
            @Override
            public void breakerStateChanged(int branch, BreakerState state) {
                seen.add(state);
            }
        };
        for (int n = 0; n < 15; n++) {
            r.ticks(1);
            assertThat(r.g.branchCurrentA(r.cb)).isBetween(8e3, 10e3);
            r.g.operateBreaker(r.cb, false);
            r.ticks(2);
            if (n < 14) {
                assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.OPEN);
                r.g.operateBreaker(r.cb, true);
                r.ticks(9);
            }
            r.g.drainEvents(sink);
        }
        assertThat(r.g.breakerOperations(r.cb)).isEqualTo(15);
        assertThat(r.g.breakerWear(r.cb)).isCloseTo(1, within(1e-9));
        assertThat(r.g.breakerState(r.cb)).isEqualTo(BreakerState.FAULTED);
        assertThat(r.g.operateBreaker(r.cb, true)).isEqualTo(BreakerResponse.UNAVAILABLE);
        assertThat(seen).contains(BreakerState.OPENING, BreakerState.OPEN, BreakerState.CLOSING, BreakerState.CLOSED)
                .endsWith(BreakerState.FAULTED);
        r.g.repairBreaker(r.cb);
        assertThat(r.g.breakerWear(r.cb)).isZero();
        assertThat(r.g.operateBreaker(r.cb, true)).isEqualTo(BreakerResponse.ACCEPTED);
    }

    @Test
    void branchesWithoutBreakerRejectBreakerCommands() {
        FaultRig r = new FaultRig(0.1);
        BusId x = r.g.addBus(VoltageClass.MV_6_3, "x");
        BranchId plain = r.g.addBranch(new Branch.Line(r.far, x, 0, 0.1, 0, 1000, null, 1));
        assertThatThrownBy(() -> r.g.operateBreaker(plain, false)).isInstanceOf(IllegalStateException.class);
    }
}
