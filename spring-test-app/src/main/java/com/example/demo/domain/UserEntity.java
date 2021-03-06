package com.example.demo.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.time.LocalDate;

@Entity
public class UserEntity extends AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String name;
    private String bio;
    private LocalDate birthdate;
    private String address;

    public UserEntity() {
    }

    private UserEntity(String name, String bio, LocalDate birthdate, String address) {
        this.name = name;
        this.bio = bio;
        this.birthdate = birthdate;
        this.address = address;
    }

    public Long getId() {
        return id;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
