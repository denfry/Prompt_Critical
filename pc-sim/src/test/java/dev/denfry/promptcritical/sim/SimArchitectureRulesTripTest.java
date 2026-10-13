package dev.denfry.promptcritical.sim;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.denfry.promptcritical.sim.archfixture.LeakyMinecraftUser;
import dev.denfry.promptcritical.sim.archfixture.UnseededRandomUser;
import dev.denfry.promptcritical.sim.archfixture.WallClockUser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Правила §5.1.2 действительно срабатывают на нарушителях (T-05-01, негативная часть). */
@Tag("T-05-01")
class SimArchitectureRulesTripTest {

    @Test
    void minecraftImportIsRejected() {
        JavaClasses leaky = new ClassFileImporter().importClasses(LeakyMinecraftUser.class);
        assertThatThrownBy(() -> SimArchitectureTest.noMinecraft.check(leaky))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("net.minecraft.core.BlockPos");
    }

    @Test
    void unseededRandomIsRejected() {
        JavaClasses random = new ClassFileImporter().importClasses(UnseededRandomUser.class);
        assertThatThrownBy(() -> SimArchitectureTest.noUnseededRandom.check(random))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void wallClockIsRejected() {
        JavaClasses clock = new ClassFileImporter().importClasses(WallClockUser.class);
        assertThatThrownBy(() -> SimArchitectureTest.noWallClock.check(clock))
                .isInstanceOf(AssertionError.class);
    }
}
