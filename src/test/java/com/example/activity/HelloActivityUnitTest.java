package com.example.activity;

import static org.junit.jupiter.api.Assertions.*;

import io.temporal.testing.TestActivityEnvironment;
import io.temporal.testing.TestActivityExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class HelloActivityUnitTest {
    
    @RegisterExtension
    public static final TestActivityExtension activityExtension = 
        TestActivityExtension.newBuilder()
            .setActivityImplementations(new HelloActivityImpl())
            .build();
    
    @Test
    public void testSayHello(TestActivityEnvironment testEnv, HelloActivity activity) {
        // Test basic functionality
        String result = activity.sayHello("World");
        assertEquals("Hello, World!", result);
    }
    
    @Test
    public void testSayHelloWithSpecialCharacters(TestActivityEnvironment testEnv, HelloActivity activity) {
        // Test with special characters
        String result = activity.sayHello("@#$%^&*()");
        assertEquals("Hello, @#$%^&*()!", result);
    }
    
    @Test
    public void testSayHelloWithUnicodeCharacters(TestActivityEnvironment testEnv, HelloActivity activity) {
        // Test with unicode characters
        String result = activity.sayHello("こんにちは");
        assertEquals("Hello, こんにちは!", result);
    }
    
    @Test
    public void testSayHelloWithLongString(TestActivityEnvironment testEnv, HelloActivity activity) {
        // Test with a long string
        String longName = "A".repeat(1000);
        String result = activity.sayHello(longName);
        assertEquals("Hello, " + longName + "!", result);
    }
    
    @Test
    public void testSayHelloWithWhitespace(TestActivityEnvironment testEnv, HelloActivity activity) {
        // Test with whitespace
        String result = activity.sayHello("   Temporal   ");
        assertEquals("Hello,    Temporal   !", result);
    }
    
    @Test
    public void testSayHelloMultipleTimes(TestActivityEnvironment testEnv, HelloActivity activity) {
        // Test multiple invocations
        String[] names = {"Alice", "Bob", "Charlie"};
        
        for (String name : names) {
            String result = activity.sayHello(name);
            assertEquals("Hello, " + name + "!", result);
        }
    }
}