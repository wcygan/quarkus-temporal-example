package com.example.activity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HelloActivityImplTest {
    
    @Test
    void testSayHello() {
        HelloActivityImpl activity = new HelloActivityImpl();
        
        String result = activity.sayHello("Alice");
        assertEquals("Hello, Alice!", result);
    }
    
    @Test
    void testSayHelloWithEmptyName() {
        HelloActivityImpl activity = new HelloActivityImpl();
        
        String result = activity.sayHello("");
        assertEquals("Hello, !", result);
    }
    
    @Test
    void testSayHelloWithNull() {
        HelloActivityImpl activity = new HelloActivityImpl();
        
        String result = activity.sayHello(null);
        assertEquals("Hello, null!", result);
    }
}