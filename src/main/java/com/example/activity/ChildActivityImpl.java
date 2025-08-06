package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ChildActivityImpl implements ChildActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(ChildActivityImpl.class);
    
    @Override
    public String executeFirst(String input) {
        logger.info("Executing first activity with input: {}", input);
        System.out.println("first");
        return "first:" + input;
    }
    
    @Override
    public String executeSecond(String input) {
        logger.info("Executing second activity with input: {}", input);
        System.out.println("second");
        return "second:" + input;
    }
    
    @Override
    public String executeThird(String input) {
        logger.info("Executing third activity with input: {}", input);
        System.out.println("third");
        return "third:" + input;
    }
}