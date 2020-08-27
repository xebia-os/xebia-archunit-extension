package com.github.xebia.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

import static com.github.xebia.archunit.rules.XebiaArchitectureRules.*;

public abstract class AbstractArchitectureTests {

    private final JavaClasses javaClasses;

    private final String[] entityClasses;
    private final String domainPackageMatchIdentifier;
    private final String rootPackageIdentifier;
    private final String dtoClassSuffixes;

    public AbstractArchitectureTests(JavaClasses javaClasses,
                                     String[] entityClasses,
                                     String domainPackageMatchIdentifier,
                                     String rootPackageIdentifier,
                                     String dtoClassSuffixes) {
        this.javaClasses = javaClasses;
        this.entityClasses = entityClasses;
        this.domainPackageMatchIdentifier = domainPackageMatchIdentifier;
        this.rootPackageIdentifier = rootPackageIdentifier;
        this.dtoClassSuffixes = dtoClassSuffixes;
    }

    @Test
    void no_get_api_should_return_list_or_set() {
        noGetApiShouldReturnListOrSet()
                .check(javaClasses);
    }

    @Test
    void get_api_whose_name_ends_with_list_should_use_pagination() {
        getApiWhoseNameEndsWithListShouldUsePagination()
                .check(javaClasses);
    }

    @Test
    void no_rest_controller_should_access_entity_class() {
        noRestControllerShouldAccessEntityClass(entityClasses)
                .check(javaClasses);
    }

    @Test
    void rest_controllers_should_return_dtos_only() {
        restControllersShouldReturnDtosOnly(dtoClassSuffixes)
                .check(javaClasses);
    }

    @Test
    void rest_controllers_name_should_end_with_resource() {
        restControllersNameShouldEndWithResource()
                .check(javaClasses);
    }

    @Test
    void all_entity_classes_should_have_version_field() {
        allEntityClassesShouldHaveVersionField()
                .check(javaClasses);
    }

    @Test
    void no_checked_exceptions() {
        noCheckedExceptions()
                .check(javaClasses);
    }

    @Test
    void microservices_should_not_depend_on_each_other() {
        microservicesShouldNotDependOnEachOther(domainPackageMatchIdentifier)
                .check(javaClasses);
    }

    @Test
    void utils_classes_should_have_private_constructor() {
        utilsClassesShouldHavePrivateConstructor()
                .check(javaClasses);
    }

    @Test
    void root_directory_should_have_application_class() {
        rootDirectoryShouldHaveApplicationClass(rootPackageIdentifier)
                .check(javaClasses);
    }

    @Test
    void logger_should_be_private_static_final() {
        loggerShouldBePrivateStaticFinal()
                .check(javaClasses);
    }

    @Test
    void repository_should_reside_in_repository_package() {
        repositoryShouldResideInRepositoryPackage()
                .check(javaClasses);
    }
}
