package com.example.demo.domain;

import javax.persistence.MappedSuperclass;
import javax.persistence.Version;

@MappedSuperclass
public class AbstractEntity {

    @Version
    private int version;

    public int getVersion() {
        return version;
    }
}
