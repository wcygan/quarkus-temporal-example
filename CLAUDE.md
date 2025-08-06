# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Quarkus-Temporal Example** project demonstrating production-ready workflow patterns using Temporal for distributed orchestration. The project showcases comprehensive implementations of SAGA patterns, parent-child workflows, continue-as-new patterns, and scheduled workflows.

**Technology Stack:**
- **Framework**: Quarkus 3.15.1 (Java 21)
- **Workflow Engine**: Temporal 1.25.1
- **Infrastructure**: Docker Compose (MySQL 8.0, Temporal Server, Temporal UI)
- **Testing**: JUnit 5, Temporal Testing SDK, TestContainers

## Architecture

The project follows **Hexagonal Architecture** with clear separation between domain logic and infrastructure:

```
com.example/
├── activity/          # Temporal Activities (business operations)
│   ├── payment/      # Payment processing activities
│   ├── inventory/    # Inventory management activities
│   ├── shipping/     # Shipping operations
│   └── notification/ # Notification services
├── workflow/         # Temporal Workflows (orchestration)
│   ├── OrderSagaWorkflow       # SAGA pattern with compensation
│   ├── ParentWorkflow          # Parent-child orchestration
│   ├── MetricsMonitorWorkflow  # Continue-as-new pattern
│   └── ScheduledReportWorkflow # Cron-based scheduling
├── resource/         # JAX-RS REST endpoints
├── temporal/         # Temporal configuration (CDI Producers)
└── model/           # Data transfer objects
```

## Commands

### Primary Development Commands (via Deno task runner):

```bash
# Infrastructure
deno task up      # Start Docker services (MySQL, Temporal, UI)
deno task down    # Stop all services
deno task ps      # Check service status

# Development
deno task dev     # Start Quarkus dev server (port 7474)
deno task test    # Run all tests
deno task build   # Build JAR package
deno task native  # Build native executable

# Code Quality
deno task lint    # Check code formatting
deno task format  # Apply code formatting
```

### Testing Commands:

```bash
# Run specific test class
./mvnw test -Dtest=OrderSagaWorkflowTest

# Run specific test method
./mvnw test -Dtest=OrderSagaWorkflowTest#testCompensation

# Run tests by pattern
./mvnw test -Dtest="*Workflow*"

# Run integration tests only
./mvnw integration-test
```

### Service Endpoints:

- **Application**: http://localhost:7474
- **Temporal UI**: http://localhost:8088
- **MySQL**: localhost:3306
- **Temporal gRPC**: localhost:7233

## Workflow Patterns Implemented

### 1. SAGA Pattern (`OrderSagaWorkflow`)
Multi-step distributed transaction with automatic compensation on failure:
- Payment processing → Inventory reservation → Shipping → Notification
- Each step has a compensating action that executes on downstream failures
- Test with: `./mvnw test -Dtest=OrderSagaWorkflowTest`

### 2. Parent-Child Workflows (`ParentWorkflow`/`ChildWorkflow`)
Hierarchical workflow decomposition for complex business processes:
- Parent workflow spawns multiple child workflows
- Async execution with result aggregation
- Test with: `./mvnw test -Dtest=ParentWorkflowTest`

### 3. Continue-As-New Pattern (`MetricsMonitorWorkflow`)
Long-running workflow with history management:
- Monitors metrics continuously
- Resets workflow history to prevent unbounded growth
- Test with: `./mvnw test -Dtest=MetricsMonitorWorkflowTest`

### 4. Scheduled Workflows (`ScheduledReportWorkflow`)
Cron-based workflow execution:
- Runs every 5 minutes (configurable)
- Time-skipping in tests for rapid validation
- Test with: `./mvnw test -Dtest=ScheduledReportWorkflowTest`

## Testing Strategy

### Unit Tests
- Use `TestWorkflowEnvironment` for isolated workflow testing
- Time-skipping capabilities for scheduled workflows
- Activity mocking with `TestActivityEnvironment`

### Integration Tests
- Use `IntegrationTestProfile` to connect to real Temporal server
- Requires running infrastructure (`deno task up`)
- Tests end-to-end workflow execution

### Test Patterns
```java
// Test workflow with time skipping
testEnv.sleep(Duration.ofMinutes(5));

// Test compensation logic
workflow.failAtStep("PAYMENT"); // Triggers compensation

// Query workflow state
String status = workflow.getOrderStatus();
```

## Key Configuration

### Temporal Configuration
- **Task Queue**: `doc-approval-queue`
- **Namespace**: `default`
- **Worker Factory**: CDI-managed with auto-registration

### Application Properties
```properties
quarkus.http.port=7474
quarkus.http.test-port=7575
temporal.target=localhost:7233
temporal.task-queue=doc-approval-queue
```

## Development Workflow

1. **Start infrastructure**: `deno task up`
2. **Start dev server**: `deno task dev`
3. **Make changes** - Live reload is enabled
4. **Test changes**: `./mvnw test -Dtest=SpecificTest`
5. **Format code**: `deno task format`
6. **Run all tests**: `deno task test`

## Important Implementation Details

### Activity Retry Policies
Activities are configured with exponential backoff and maximum attempts:
```java
@ActivityOptions(
    startToCloseTimeout = "PT10S",
    retryOptions = @RetryOptions(
        maximumAttempts = 3,
        backoffCoefficient = 2.0
    )
)
```

### Workflow Queries
Workflows expose query methods for runtime inspection:
```java
@QueryMethod
String getOrderStatus();

@QueryMethod
List<String> getCompletedSteps();
```

### Signal Methods
Workflows can receive external signals for testing and control:
```java
@SignalMethod
void failAtStep(String step);
```

### CDI Integration
All Temporal components are CDI-managed:
- `@ApplicationScoped` for activities
- `@Produces` for WorkflowClient and WorkerFactory
- Automatic worker registration on startup

## Debugging Tips

- **Temporal UI**: Access http://localhost:8088 to inspect workflow executions
- **Workflow History**: Use UI to view detailed execution history
- **Activity Failures**: Check retry attempts in UI timeline
- **Test Failures**: Run with `-X` for detailed Maven output
- **Docker Logs**: `docker compose logs temporal` for server issues