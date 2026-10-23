package lab.ticketing.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Checks application boundaries and avoids synchronized monitors in main code on Java 21.
 * As a root-project test, it has every module's main classes on a single classpath.
 * Each rule is also run against deliberately violating fixtures (lab.ticketing.archfixture) to prove it catches them (guards against checks that always pass).
 */
class ArchitectureTest {

    static final String BASE = "lab.ticketing";
    static final List<String> APPS = List.of("waiting", "booking", "worker", "fakeext");

    static JavaClasses main;

    @BeforeAll
    static void importMainClasses() {
        main = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    /** App packages do not depend on each other. Shared code goes through core. */
    static ArchRule appsAreIndependent(String base, List<String> apps) {
        List<ArchRule> rules = apps.stream()
                .map(app -> (ArchRule) noClasses().that().resideInAPackage(base + "." + app + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(apps.stream()
                                .filter(other -> !other.equals(app))
                                .map(other -> base + "." + other + "..")
                                .toArray(String[]::new))
                        .as(app + " must not depend on other apps"))
                .toList();
        return CompositeArchRule.of(rules);
    }

    static ArchRule noSynchronizedMethods(String base) {
        return noMethods().that().areDeclaredInClassesThat().resideInAPackage(base + "..")
                .should().haveModifier(JavaModifier.SYNCHRONIZED)
                .as("no synchronized methods (virtual thread pinning; use ReentrantLock)");
    }

    @Test
    void importSeesEveryModule() {
        for (String pkg : List.of("core", "waiting", "booking", "worker", "fakeext", "testsupport")) {
            assertThat(main.stream().filter(c -> c.getPackageName().startsWith(BASE + "." + pkg)).count())
                    .as("classes imported from %s", pkg)
                    .isPositive();
        }
        assertThat(main.stream().filter(c -> c.getPackageName().startsWith(BASE + ".arch"))).isEmpty();
    }

    @Test
    void appsDoNotDependOnEachOther() {
        appsAreIndependent(BASE, APPS).check(main);
    }

    @Test
    void coreDependsOnNoAppAndNoTestSupport() {
        noClasses().that().resideInAPackage(BASE + ".core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        APPS.stream().map(app -> BASE + "." + app + "..").toArray(String[]::new))
                .orShould().dependOnClassesThat().resideInAPackage(BASE + ".testsupport..")
                .check(main);
    }

    @Test
    void mainCodeDoesNotUseTestSupport() {
        noClasses().that().resideOutsideOfPackage(BASE + ".testsupport..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".testsupport..")
                .check(main);
    }

    @Test
    void noSynchronizedMethodsInMainCode() {
        noSynchronizedMethods(BASE).check(main);
    }

    @Test
    void noSynchronizedBlocksInMainBytecode() {
        assertThat(Monitors.find(main)).isEmpty();
    }

    /** Do the rules above actually catch violations? Applying them to the deliberately violating fixtures must fail. */
    @Nested
    class RulesCatchViolations {

        final JavaClasses fixtures = new ClassFileImporter().importPackages(BASE + ".archfixture");

        @Test
        void crossAppDependencyIsCaught() {
            assertThatThrownBy(() -> appsAreIndependent(BASE + ".archfixture", List.of("alpha", "beta")).check(fixtures))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("alpha must not depend on other apps")
                    .hasMessageContaining("BetaService");
        }

        @Test
        void synchronizedMethodIsCaught() {
            assertThatThrownBy(() -> noSynchronizedMethods(BASE + ".archfixture").check(fixtures))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("SyncMethod.increment");
        }

        @Test
        void synchronizedBlockIsCaughtInBytecode() {
            assertThat(Monitors.find(fixtures)).containsExactlyInAnyOrder(
                    "lab.ticketing.archfixture.sync.SyncBlock#increment (block)",
                    "lab.ticketing.archfixture.sync.SyncMethod#increment (method)");
        }
    }
}
