package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class HelloActivityImpl implements HelloActivity {
    
    @Override
    public String sayHello(String name) {
        String message = "Hello, " + name + "!";
        System.out.println(message);
        return message;
    }
}