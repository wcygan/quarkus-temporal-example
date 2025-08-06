package com.example.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark test classes that are safe to run in parallel.
 * These tests have no shared state, don't require specific execution order,
 * and don't conflict with other tests for resources.
 * 
 * Use this for:
 * - Pure unit tests
 * - Tests using TestWorkflowEnvironment (not real Temporal)
 * - Tests with isolated mocks
 * 
 * DO NOT use this for:
 * - Tests with @QuarkusTest
 * - Tests with @TestMethodOrder
 * - Tests sharing static state
 * - Integration tests with real infrastructure
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParallelSafe {
    String reason() default "Test has no shared state or resource conflicts";
}