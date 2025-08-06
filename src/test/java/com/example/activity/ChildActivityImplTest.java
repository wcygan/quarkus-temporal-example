package com.example.activity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChildActivityImplTest {
    
    private ChildActivityImpl childActivity;
    
    @BeforeEach
    public void setUp() {
        childActivity = new ChildActivityImpl();
    }
    
    @Test
    public void testExecuteFirst() {
        String result = childActivity.executeFirst("input");
        assertEquals("first:input", result);
    }
    
    @Test
    public void testExecuteSecond() {
        String result = childActivity.executeSecond("input");
        assertEquals("second:input", result);
    }
    
    @Test
    public void testExecuteThird() {
        String result = childActivity.executeThird("input");
        assertEquals("third:input", result);
    }
    
    @Test
    public void testExecuteWithEmptyInput() {
        assertEquals("first:", childActivity.executeFirst(""));
        assertEquals("second:", childActivity.executeSecond(""));
        assertEquals("third:", childActivity.executeThird(""));
    }
    
    @Test
    public void testExecuteWithNullInput() {
        assertEquals("first:null", childActivity.executeFirst(null));
        assertEquals("second:null", childActivity.executeSecond(null));
        assertEquals("third:null", childActivity.executeThird(null));
    }
}