# Parallel Test Execution Guide

This project is configured for parallel test execution to significantly reduce test runtime.

## Configuration Overview

### JUnit 5 Parallel Execution
- **Enabled**: Tests run concurrently by default
- **Strategy**: Dynamic thread allocation based on available CPU cores
- **Factor**: 2x CPU cores for optimal resource utilization

### Test Categories

#### 1. Parallel-Safe Tests
Most tests run in parallel by default:
- `DocumentProcessingWorkflowTest` - Unit tests with isolated test environments
- `ApplicationTest` - Basic endpoint tests
- Regular unit tests without shared state

#### 2. Sequential Tests
Tests marked with `@Execution(ExecutionMode.SAME_THREAD)`:
- `DocumentRepositoryJooqIntegrationTest` - Uses shared static state and ordered execution
- Tests with database state dependencies
- Tests requiring specific execution order

### Maven Configuration

#### Standard Test Execution (Parallel)
```bash
# Run all tests with parallel execution
./mvnw test

# Run specific test classes in parallel
./mvnw test -Dtest="DocumentProcessingWorkflowTest,ApplicationTest"
```

#### Profile-Based Execution

##### Fast Parallel Testing
```bash
# Maximum parallelization (8 threads)
./mvnw test -Pparallel-tests
```

##### Conservative Parallel Testing
```bash
# Reduced parallelization for stability (2 threads)
./mvnw test -Pparallel-conservative
```

#### Integration Tests
```bash
# Run integration tests with controlled parallelization
./mvnw verify -DskipITs=false
```

### Performance Benefits

**Expected Speedup:**
- **Unit Tests**: 3-5x faster execution
- **Integration Tests**: 2-3x faster execution
- **Overall**: 60-80% reduction in test time

**Example Performance:**
```
Sequential: ~2-3 minutes
Parallel:   ~30-45 seconds
```

## Best Practices

### Writing Parallel-Safe Tests

#### ✅ DO:
```java
@Test
void testIndependentOperation() {
    // Each test creates its own data
    String testId = "test-" + UUID.randomUUID();
    // Test logic using unique data
}
```

#### ❌ AVOID:
```java
public class BadTest {
    private static String sharedState; // Shared between tests
    
    @Test 
    void testA() { sharedState = "A"; }
    
    @Test 
    void testB() { assert sharedState.equals("A"); } // Race condition!
}
```

### Marking Sequential Tests

For tests that MUST run sequentially:

```java
@Test
@Execution(ExecutionMode.SAME_THREAD)  // Force sequential execution
@TestMethodOrder(OrderAnnotation.class) // Control execution order
class SequentialTest {
    private static Long sharedId; // Shared state
    
    @Test @Order(1)
    void createSharedData() { /* ... */ }
    
    @Test @Order(2) 
    void useSharedData() { /* ... */ }
}
```

## Configuration Files

### junit-platform.properties
```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.config.strategy=dynamic
junit.jupiter.execution.parallel.config.dynamic.factor=2
```

### Maven Properties
```xml
<properties>
  <parallel.unit.tests>true</parallel.unit.tests>
  <parallel.integration.tests>true</parallel.integration.tests>
  <test.thread.count>4</test.thread.count>
</properties>
```

## Troubleshooting

### Common Issues

#### 1. Flaky Tests
**Symptom**: Tests pass individually but fail in parallel
**Solution**: Check for shared state, static variables, or resource conflicts

#### 2. Resource Contention
**Symptom**: Database connection errors, port conflicts
**Solution**: Use TestContainers with unique containers or reduce parallelization

#### 3. Memory Issues
**Symptom**: OutOfMemoryError during parallel execution
**Solution**: Increase JVM memory or reduce thread count

### Debugging Commands

```bash
# Run tests sequentially for comparison
./mvnw test -Djunit.jupiter.execution.parallel.enabled=false

# Run with verbose output
./mvnw test -X

# Run single test in isolation
./mvnw test -Dtest=SpecificTestClass

# Check test execution order
./mvnw test -Djunit.jupiter.execution.parallel.mode.default=same_thread
```

## Integration with CI/CD

### GitHub Actions Example
```yaml
- name: Run Parallel Tests
  run: ./mvnw test -Pparallel-tests
  env:
    MAVEN_OPTS: "-Xmx2g"
```

### Local Development
```bash
# Quick parallel test run
alias quicktest="./mvnw test -Pparallel-tests --quiet"

# Full parallel test suite  
alias fulltest="./mvnw verify -Pparallel-tests -DskipITs=false"
```

## Monitoring and Metrics

Track parallel test performance:
- Monitor test execution time in CI/CD logs
- Compare parallel vs sequential runtimes
- Identify bottleneck tests that should remain sequential

## Future Improvements

Potential optimizations:
- Separate test execution into more granular groups
- Custom thread pool configurations for different test types  
- Dynamic thread allocation based on test complexity
- Container-per-test-class for complete isolation