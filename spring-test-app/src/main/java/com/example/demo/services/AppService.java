package com.example.demo.services;

import com.example.demo.helper.AppHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AppService {
    private final String name;
    private final AppHelper appHelper;

    public AppService(@Value("${app.name:Test}") String name, AppHelper appHelper) {
        this.name = name;
        this.appHelper = appHelper;
    }
}
