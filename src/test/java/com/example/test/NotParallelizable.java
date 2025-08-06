package com.example.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark test classes that should not run in parallel.
 * Used for tests that have state dependencies or shared resources.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotParallelizable {
    String reason() default "Test has dependencies that prevent parallel execution";
}