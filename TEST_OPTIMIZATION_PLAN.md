# Test Suite Optimization Plan

## Current State
- **129 tests** taking ~2-3 minutes to run
- Parallel execution disabled due to Quarkus TestScopeManager conflicts
- Multiple tests using TestContainers and real infrastructure

## Optimization Opportunities (Ranked by Impact)

### 🔴 High Impact - Quick Wins

#### 1. Remove Thread.sleep() Calls (2x speedup for affected tests)
**Files to fix:**
- `OrderSagaResourceTest.java`: Lines 63, 194 (total 4 seconds delay)
- `OrderSagaWorkflowIntegrationTest.java`: Line 364 (2 seconds delay)
- `DocumentProcessingWorkflowTest.java`: Line 190 (100ms delay)

**Solution:** Use proper test synchronization or mocking

#### 2. Selective Parallel Execution (3x speedup potential)
**Safe for parallel execution:**
- All unit tests (non-@QuarkusTest)
- Activity tests (`*ActivityImplTest`, `*ActivityUnitTest`)
- Workflow tests using TestWorkflowEnvironment

**Must remain sequential:**
- `DocumentRepositoryJooqIntegrationTest` (shared state)
- Tests with `@TestMethodOrder`
- Integration tests with real Temporal server

### 🟡 Medium Impact - Refactoring Required

#### 3. Convert Heavy Integration Tests to Unit Tests
**Target tests:**
- `ApplicationTest` - Convert to unit tests with mocked services
- `*ResourceTest` classes - Use `@QuarkusComponentTest` or direct service testing
- Expected: 10x speedup for these 20+ tests

#### 4. Optimize Database Tests
**Current issues:**
- 11 tests in `DocumentRepositoryIntegrationTest` run sequentially
- Each test creates/destroys data
- Complex foreign key checks in `@AfterEach`

**Solutions:**
- Use `@Transactional` with rollback
- Batch similar tests
- Use test data builders

#### 5. Share Temporal Test Infrastructure
**Current:** Each test creates its own TestWorkflowEnvironment
**Optimize:** Share environment across test class using `@BeforeAll`

### 🟢 Low Impact - Nice to Have

#### 6. Test Categorization with Tags
```java
@Tag("fast")     // Unit tests
@Tag("integration") // Database/Temporal tests
@Tag("slow")     // End-to-end tests
```

Run fast tests during development:
```bash
./mvnw test -Dgroups="fast"
```

## Implementation Strategy

### Phase 1: Quick Wins (1 hour)
1. Remove all Thread.sleep() calls
2. Add @Execution annotations to mark parallel-safe tests
3. Create test categories with @Tag

### Phase 2: Test Refactoring (4 hours)
1. Convert ApplicationTest to unit tests
2. Refactor resource tests to use mocks
3. Optimize database test setup/teardown

### Phase 3: Infrastructure Optimization (2 hours)
1. Share TestWorkflowEnvironment instances
2. Implement transaction rollback for DB tests
3. Configure Maven profiles for test execution

## Expected Results

| Change | Tests Affected | Current Time | Optimized Time | Speedup |
|--------|---------------|--------------|----------------|---------|
| Remove sleep() | 4 tests | 6+ seconds | <1 second | 6x |
| Parallel execution | ~80 tests | Sequential | Parallel | 3x |
| Unit test conversion | 20 tests | 30 seconds | 3 seconds | 10x |
| DB optimization | 11 tests | 20 seconds | 7 seconds | 3x |
| **Total Suite** | **129 tests** | **2-3 minutes** | **30-45 seconds** | **4-6x** |

## Maven Configuration for Test Categories

```xml
<!-- Fast tests only -->
<profile>
  <id>fast</id>
  <build>
    <plugins>
      <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <groups>fast</groups>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>

<!-- Integration tests only -->
<profile>
  <id>integration</id>
  <build>
    <plugins>
      <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <groups>integration</groups>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

## Commands

```bash
# Run only fast tests (unit tests)
./mvnw test -Pfast

# Run only integration tests
./mvnw test -Pintegration

# Run all tests
./mvnw test
```

## Next Steps

1. Start with removing Thread.sleep() calls - immediate 2x improvement
2. Mark parallel-safe tests with annotations
3. Gradually convert integration tests to unit tests
4. Measure and iterate