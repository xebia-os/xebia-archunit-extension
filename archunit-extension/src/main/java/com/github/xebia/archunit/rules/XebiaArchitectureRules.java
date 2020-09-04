package com.github.xebia.archunit.rules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.AbstractClassesTransformer;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ClassesTransformer;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.all;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

public final class XebiaArchitectureRules {

    private XebiaArchitectureRules() {
    }

    public static ArchRule noGetApiShouldReturnListOrSet() {
        return noMethods()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.GetMapping")
                .should()
                .haveRawReturnType(List.class)
                .orShould()
                .haveRawReturnType(Set.class);
    }

    public static ArchRule getApiWhoseNameEndsWithListShouldUsePagination() {
        return methods()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.GetMapping")
                .and()
                .haveNameMatching("\\w*List\\b")
                .should()
                .haveRawReturnType("org.springframework.data.domain.Page");
    }

    public static ArchRule noRestControllerShouldAccessEntityClass(String... entityPackages) {
        if (entityPackages == null || entityPackages.length == 0) {
            throw new IllegalArgumentException("Please provide package names to scan for entities");
        }
        return noClasses()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should()
                .accessClassesThat()
                .resideInAnyPackage(entityPackages);
    }

    public static ArchRule restControllersShouldReturnDtosOnly(String... dtoClassSuffixes) {
        if (dtoClassSuffixes == null || dtoClassSuffixes.length == 0) {
            throw new IllegalArgumentException("Please provide suffixes that should be considered as DTO");
        }
        return methods()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.GetMapping")
                .and().haveRawReturnType("org.springframework.data.domain.Page")
                .should(new ArchCondition<JavaMethod>("return Page<DTO> object") {
                    @Override
                    public void check(JavaMethod item, ConditionEvents events) {
                        Type genericReturnType = item.reflect().getGenericReturnType();
                        Type[] actualTypeArguments = ((ParameterizedType) genericReturnType).getActualTypeArguments();
                        for (Type actualTypeArgument : actualTypeArguments) {
                            Set<String> set = new HashSet<>(Arrays.asList(dtoClassSuffixes));
                            Optional<String> dtoClassExists = set.stream()
                                    .filter(s -> actualTypeArgument.getTypeName().endsWith(s))
                                    .findFirst();
                            if (!dtoClassExists.isPresent()) {
                                events.add(new SimpleConditionEvent(item, false, "return Page<DTO> object"));
                            }
                        }
                    }
                });
    }

    public static ArchRule restControllersNameShouldEndWithResource() {
        return classes()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should()
                .haveSimpleNameEndingWith("Resource");
    }

    public static ArchRule allEntityClassesShouldHaveVersionField() {
        return classes()
                .that()
                .areAnnotatedWith("javax.persistence.Entity")
                .should(new ArchCondition<JavaClass>("have @Version field") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        Set<JavaField> fields = javaClass.getAllFields();
                        Optional<JavaField> versionField = fields.stream().filter(field -> field.getAnnotations().stream().map(a -> a.getRawType().getName()).anyMatch(a -> a.equals("javax.persistence.Version"))).findFirst();
                        events.add(new SimpleConditionEvent(javaClass, versionField.isPresent(), javaClass.getFullName() + " have @Version field"));
                    }
                });
    }

    public static ArchRule noCheckedExceptions() {
        return noClasses()
                .should()
                .beAssignableFrom(Exception.class);
    }

    /**
     * If we have two services s1 and s2 with their domain classes packaged in libraries l1 and l2
     * we don't want s1 to use l2 domain classes(and vice versa) as it leads to tight coupling between services.
     * <p>
     * Usaage: microservicesShouldNotDependOnEachOther("com.example.(*service).domain")
     *
     * @param packageIdentifier
     * @return
     */
    public static ArchRule microservicesShouldNotDependOnEachOther(String packageIdentifier) {
        return SlicesRuleDefinition.slices()
                .matching(packageIdentifier)
                .namingSlices("$2 of $1")
                .should().notDependOnEachOther();
    }

    public static ArchRule utilsClassesShouldHavePrivateConstructor() {
        ClassesTransformer<JavaConstructor> utilClasses =
                new AbstractClassesTransformer<JavaConstructor>("utility constructors") {
                    @Override
                    public Iterable<JavaConstructor> doTransform(JavaClasses classes) {
                        Set<JavaConstructor> result = new HashSet<>();
                        for (JavaClass javaClass : classes) {
                            if (javaClass.getSimpleName().endsWith("Util") || javaClass.getSimpleName().endsWith("Utils")) {
                                result.addAll(javaClass.getConstructors());
                            }
                        }
                        return result;
                    }
                };

        ArchCondition<JavaConstructor> havePrivateConstructors = new ArchCondition<JavaConstructor>("be private") {
            @Override
            public void check(JavaConstructor constructor, ConditionEvents events) {
                boolean privateAccess = constructor.getModifiers().contains(JavaModifier.PRIVATE);
                String message = String.format("%s is not private", constructor.getFullName());
                events.add(new SimpleConditionEvent(constructor, privateAccess, message));
            }
        };
        return all(utilClasses)
                .should(havePrivateConstructors);
    }

    public static ArchRule rootDirectoryShouldHaveApplicationClass(String rootPackageIdentifier) {
        return classes()
                .that().resideInAPackage(rootPackageIdentifier)
                .should().haveSimpleNameEndingWith("Application");
    }

    public static ArchRule loggerShouldBePrivateStaticFinal() {
        return fields().that()
                .haveRawType("org.slf4j.Logger")
                .should().bePrivate()
                .andShould().beStatic()
                .andShould().beFinal();
    }

    public static ArchRule repositoryShouldResideInRepositoryPackage() {
        return classes()
                .that().haveNameMatching(".*Repository")
                .should().resideInAPackage("..repository..")
                .as("Repositories should reside in a package '..repository..'");
    }
}
