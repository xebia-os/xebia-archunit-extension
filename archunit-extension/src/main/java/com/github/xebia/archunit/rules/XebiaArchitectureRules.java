package com.github.xebia.archunit.rules;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaAnnotation;
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

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.all;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.no;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

public final class XebiaArchitectureRules {

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
        return slices()
                .matching(packageIdentifier)
                .namingSlices("$2 of $1")
                .should().notDependOnEachOther();
    }

    public static ArchRule utilsClassesShouldHavePrivateConstructor(String... utilClassSuffixes) {
        Set<String> utilClassSuffixSet = createUtilClassSet(utilClassSuffixes);
        ClassesTransformer<JavaConstructor> utilClassesConstructors =
                new AbstractClassesTransformer<JavaConstructor>("utility class constructors") {
                    @Override
                    public Iterable<JavaConstructor> doTransform(JavaClasses classes) {
                        Set<JavaConstructor> result = new HashSet<>();
                        for (JavaClass javaClass : classes) {
                            boolean utilClass = utilClassSuffixSet.stream().anyMatch(javaClass.getSimpleName()::endsWith);
                            if (utilClass) {
                                result.addAll(javaClass.getConstructors());
                            }
                        }
                        return result;
                    }
                };

        ArchCondition<JavaConstructor> bePrivate = new ArchCondition<JavaConstructor>("be private") {
            @Override
            public void check(JavaConstructor constructor, ConditionEvents events) {
                boolean privateAccess = constructor.getModifiers().contains(JavaModifier.PRIVATE);
                String message = String.format("%s is not private", constructor.getFullName());
                events.add(new SimpleConditionEvent(constructor, privateAccess, message));
            }
        };
        return all(utilClassesConstructors)
                .should(bePrivate);
    }

    public static ArchRule utilsClassesShouldNotBeInjected(String... utilClassSuffixes) {
        Set<String> utilClassSuffixSet = createUtilClassSet(utilClassSuffixes);
        ClassesTransformer<JavaClass> utilClasses =
                new AbstractClassesTransformer<JavaClass>("utility class") {
                    @Override
                    public Iterable<JavaClass> doTransform(JavaClasses classes) {
                        Set<JavaClass> result = new HashSet<>();
                        for (JavaClass javaClass : classes) {
                            boolean utilClass = utilClassSuffixSet.stream().anyMatch(javaClass.getSimpleName()::endsWith);
                            if (utilClass) {
                                result.add(javaClass);
                            }
                        }
                        return result;
                    }
                };

        ArchCondition<JavaClass> beInjected = new ArchCondition<JavaClass>("be injected") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Set<JavaAccess<?>> accessesToSelf = javaClass.getAccessesToSelf();
                com.tngtech.archunit.base.Optional<JavaAnnotation<JavaClass>> hasComponentAnnotation = javaClass.tryGetAnnotationOfType("org.springframework.stereotype.Component");
                com.tngtech.archunit.base.Optional<JavaAnnotation<JavaClass>> hasInjectAnnotation = javaClass.tryGetAnnotationOfType("javax.inject.Inject");
                String message = String.format("%s is annotated with @Component/@Inject annotation", javaClass.getFullName());
                events.add(new SimpleConditionEvent(javaClass, hasComponentAnnotation.isPresent() || hasInjectAnnotation.isPresent(), message));
            }
        };
        return no(utilClasses)
                .should(beInjected);

    }

    public static ArchRule utilClassesMethodsShouldBeStatic(String... utilClassSuffixes) {
        Set<String> utilClassSuffixSet = createUtilClassSet(utilClassSuffixes);
        ClassesTransformer<JavaMethod> utilClassesMethods =
                new AbstractClassesTransformer<JavaMethod>("utility class methods") {
                    @Override
                    public Iterable<JavaMethod> doTransform(JavaClasses classes) {
                        Set<JavaMethod> result = new HashSet<>();
                        for (JavaClass javaClass : classes) {
                            boolean utilClass = utilClassSuffixSet.stream().anyMatch(javaClass.getSimpleName()::endsWith);
                            if (utilClass) {
                                result.addAll(javaClass.getMethods());
                            }
                        }
                        return result;
                    }
                };

        ArchCondition<JavaMethod> beStatic = new ArchCondition<JavaMethod>("be static") {
            @Override
            public void check(JavaMethod javaMethod, ConditionEvents events) {
                boolean staticAccess = javaMethod.getModifiers().contains(JavaModifier.STATIC);
                String message = String.format("%s is not static", javaMethod.getFullName());
                events.add(new SimpleConditionEvent(javaMethod, staticAccess, message));
            }
        };
        return all(utilClassesMethods)
                .should(beStatic);
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

    public static ArchRule springSingletonComponentsShouldOnlyHaveFinalFields() {
        return classes()
                .that().areAnnotatedWith("org.springframework.stereotype.Component")
                .or().areAnnotatedWith("org.springframework.stereotype.Service")
                .and().areNotAnnotatedWith("org.springframework.boot.context.properties.ConfigurationProperties")
                .or().areAnnotatedWith("org.springframework.stereotype.Controller")
                .or().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .or().areAnnotatedWith("org.springframework.stereotype.Repository")
                .should().haveOnlyFinalFields();
    }

    public static ArchRule layersShouldBeFreeOfCycles(String packageIdentifier) {
        return slices()
                .matching(packageIdentifier)
                .should().beFreeOfCycles();
    }

    public static ArchRule favorConstructorInjectionOverFieldInjection() {
        return fields().should(new ArchCondition<JavaField>("not be @Autowired/@Inject") {
            @Override
            public void check(JavaField javaField, ConditionEvents events) {
                if (javaField.isAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                        || javaField.isAnnotatedWith("javax.inject.Inject")) {
                    events.add(SimpleConditionEvent.violated(javaField,
                            String.format("Field %s of class %s is using field injection. Prefer constructor injection.", javaField.getName(), javaField.getOwner().getName())));
                }
            }
        });
    }

    public static ArchRule favorJava8DateTimeApiOverJodaTime() {
        return classes().should(new ArchCondition<JavaClass>("not use Joda time") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                List<JavaField> jodaFields = javaClass.getFields()
                        .stream()
                        .filter(field -> field.getRawType().getName().startsWith("org.joda"))
                        .collect(Collectors.toList());
                jodaFields.forEach(
                        jodaField -> events.add(SimpleConditionEvent.violated(
                                jodaField,
                                String.format("Field %s of class %s is using Joda time. Prefer Java 8 date time API", jodaField.getName(), jodaField.getOwner().getName())
                        ))
                );
            }
        });
    }

    public static ArchRule favorBuilderOverLongListConstructor() {
        return classes()
                .that().areNotAnnotatedWith("org.springframework.stereotype.Component")
                .or().areNotAnnotatedWith("org.springframework.stereotype.Service")
                .or().areNotAnnotatedWith("org.springframework.boot.context.properties.ConfigurationProperties")
                .or().areNotAnnotatedWith("org.springframework.stereotype.Controller")
                .or().areNotAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .or().areNotAnnotatedWith("org.springframework.stereotype.Repository")
                .should(new ArchCondition<JavaClass>("not have constructor more than 3 parameters") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        Set<JavaConstructor> classesWithLongConstructors = javaClass.getConstructors()
                                .stream()
                                .filter(c -> Modifier.isPublic(c.reflect().getModifiers()) && c.getRawParameterTypes().size() > 3)
                                .collect(Collectors.toSet());

                        classesWithLongConstructors.forEach(
                                c -> events.add(SimpleConditionEvent.violated(
                                        c, String.format("Constructor %s of class %s has more than 3 parameters. Prefer Builder over long list constructors", c.getName(), c.getOwner().getName())
                                ))
                        );
                    }
                });

    }


    private static Set<String> createUtilClassSet(String[] utilClassSuffixes) {
        Set<String> utilClassSuffixSet = new HashSet<>();
        if (utilClassSuffixes == null || utilClassSuffixes.length == 0) {
            utilClassSuffixSet.add("Util");
            utilClassSuffixSet.add("Utils");
        } else {
            utilClassSuffixSet.addAll(Arrays.asList(utilClassSuffixes));
        }
        return utilClassSuffixSet;
    }
}
