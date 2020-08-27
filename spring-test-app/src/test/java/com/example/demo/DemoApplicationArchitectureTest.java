package com.example.demo;

import com.example.demo.domain.UserEntity;
import com.github.xebia.archunit.AbstractArchitectureTests;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class DemoApplicationArchitectureTest extends AbstractArchitectureTests {


    public DemoApplicationArchitectureTest() {
        super(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackagesOf(Application.class),
                new String[]{UserEntity.class.getPackage().getName()},
                "com.example.(*service).domain",
                Application.class.getPackage().getName(), "Dto");
    }
}
