package secretchat.userservice;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class CleanArchitectureTest {

    private static final String BASE_PACKAGE = "secretchat.userservice";

    private static final String API_LAYER            = "api";
    private static final String APPLICATION_LAYER    = "application";
    private static final String DOMAIN_LAYER         = "domain";
    private static final String INFRASTRUCTURE_LAYER = "infrastructure";

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter().importPackages(BASE_PACKAGE);
    }

    @Test
    void layerDependenciesShouldBeRespected() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()

                .layer(API_LAYER)            .definedBy(BASE_PACKAGE + ".api..")
                .layer(APPLICATION_LAYER)    .definedBy(BASE_PACKAGE + ".application..")
                .layer(DOMAIN_LAYER)         .definedBy(BASE_PACKAGE + ".domain..")
                .layer(INFRASTRUCTURE_LAYER) .definedBy(BASE_PACKAGE + ".infrastructure..")

                .whereLayer(API_LAYER)            .mayNotBeAccessedByAnyLayer()
                .whereLayer(APPLICATION_LAYER)    .mayOnlyBeAccessedByLayers(API_LAYER)
                .whereLayer(DOMAIN_LAYER)         .mayOnlyBeAccessedByLayers(APPLICATION_LAYER, INFRASTRUCTURE_LAYER, API_LAYER)
                .whereLayer(INFRASTRUCTURE_LAYER) .mayNotBeAccessedByAnyLayer();

        rule.check(classes);
    }

    @Test
    void domainShouldNotDependOnAnyOtherLayer() {
        noClasses().that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().accessClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".api..",
                        BASE_PACKAGE + ".application..",
                        BASE_PACKAGE + ".infrastructure.."
                )
                .as("Domain layer phải hoàn toàn độc lập, không import api/application/infrastructure")
                .check(classes);
    }

    @Test
    void applicationShouldNotDependOnApiOrInfrastructure() {
        noClasses().that().resideInAPackage(BASE_PACKAGE + ".application..")
                .should().accessClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".api..",
                        BASE_PACKAGE + ".infrastructure.."
                )
                .as("Application layer không được import api hoặc infrastructure")
                .check(classes);
    }

    @Test
    void apiShouldNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage(BASE_PACKAGE + ".api..")
                .should().accessClassesThat().resideInAPackage(BASE_PACKAGE + ".infrastructure..")
                .as("API layer không được import infrastructure trực tiếp")
                .check(classes);
    }
}
