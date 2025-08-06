# Temporal Web UI Guide

The Temporal Web UI provides a comprehensive interface for monitoring, debugging, and managing Temporal Workflow Executions. This guide covers practical usage for workflow development and troubleshooting.

## Overview

The Temporal Web UI is a powerful tool that provides:
- Workflow Execution state and metadata visualization
- Real-time monitoring of workflow progress
- Debugging capabilities for failed or stuck workflows
- Activity and event history inspection
- Query and Signal interaction
- Schedule management
- Search and filtering capabilities

### Access Points

The Web UI is available through multiple deployment options:
- **Temporal CLI**: Local development server
- **Docker Compose**: Containerized deployment (our setup)
- **Temporal Cloud**: Managed service

**For this project**: Access at http://localhost:8088 when running `deno task up`

## Interface Components

### Main Navigation

The UI is organized into several key sections:

1. **Namespaces** - Workspace isolation and organization
2. **Recent Workflows** - Latest workflow executions
3. **Schedules** - Cron-based workflow scheduling
4. **Settings** - UI configuration and preferences
5. **Archive** - Historical workflow data
6. **Codec Server** - Data encryption/decryption
7. **Labs Mode** - Experimental features

### Dashboard Overview

The main dashboard provides:
- Quick access to recently executed workflows
- Summary statistics for workflow states
- Navigation to different workflow types
- Real-time updates of active executions

## Workflow Execution Viewing

### Recent Workflows View

The Recent Workflows section displays up to 1,000 recent Workflow Executions with:
- **Workflow ID**: Unique identifier for the execution
- **Workflow Type**: The workflow class name
- **Status**: Current execution state (Running, Completed, Failed, etc.)
- **Start Time**: When the workflow began
- **End Time**: When the workflow completed (if applicable)
- **Duration**: Total execution time

### Workflow Detail Page

Clicking on a workflow execution opens a detailed view containing:

#### Summary Tab
- Workflow metadata (ID, Type, Task Queue)
- Input parameters and output results
- Current status and timing information
- Related workflow information (parent/child relationships)

#### History Tab
- Complete event timeline of the workflow execution
- Chronological or reverse chronological ordering
- Event details including timestamps and payloads
- Visual representation of workflow progress

#### Call Stack Tab
- Current execution stack for active workflows
- Pending activities and their states
- Activity retry information
- Task queue assignments

#### Queries Tab
- Available query methods on the workflow
- Real-time query execution
- Query results and response times

#### Signals Tab
- Signal methods available on the workflow
- Signal history for the execution
- Ability to send new signals

## Activity and Event History Inspection

### Event Timeline

The History tab provides a detailed chronological view of all events:

#### Event Types
- **WorkflowExecutionStarted**: Initial workflow trigger
- **ActivityTaskScheduled**: Activity queued for execution
- **ActivityTaskStarted**: Activity begins processing
- **ActivityTaskCompleted**: Activity finishes successfully
- **ActivityTaskFailed**: Activity encounters an error
- **WorkflowExecutionCompleted**: Workflow finishes successfully
- **WorkflowExecutionFailed**: Workflow encounters a fatal error

#### Event Details
Each event includes:
- **Timestamp**: When the event occurred
- **Event ID**: Unique event identifier
- **Event Type**: Classification of the event
- **Attributes**: Event-specific data and metadata
- **Input/Output**: Activity parameters and results

### Activity Inspection

For each activity execution, you can view:
- **Activity Type**: The activity class/method name
- **Input Parameters**: Data passed to the activity
- **Output Results**: Data returned by the activity
- **Retry Attempts**: Number of retries and their outcomes
- **Failure Reasons**: Error messages and stack traces
- **Duration**: Time taken for activity execution

### Debugging Failed Activities

When activities fail:
1. **Error Messages**: Clear indication of what went wrong
2. **Stack Traces**: Full error context for debugging
3. **Retry History**: Previous attempts and their failures
4. **Input Validation**: Verify parameters passed to activities

## Query and Signal Interaction

### Query Methods

Queries provide read-only access to workflow state:

#### Using Queries in the UI
1. Navigate to the Queries tab on a workflow execution
2. Select the query method from available options
3. Provide any required parameters
4. Execute the query to see real-time results

#### Common Query Patterns
```java
// Example queries from our workflows
@QueryMethod
String getOrderStatus();

@QueryMethod  
List<String> getCompletedSteps();

@QueryMethod
Map<String, Object> getMetrics();
```

### Signal Methods

Signals allow external communication with running workflows:

#### Sending Signals through UI
1. Navigate to the Signals tab
2. Choose the signal method
3. Provide required signal data
4. Send the signal to the workflow

#### Signal Use Cases
- **Testing**: Trigger specific workflow paths
- **Control**: Pause, resume, or modify workflow behavior  
- **External Events**: Notify workflows of external changes

## Schedule Management

### Viewing Schedules

The Schedules section shows:
- **Schedule ID**: Unique identifier
- **Spec**: Cron expression or interval
- **Workflow Type**: Target workflow for execution
- **Next Run**: Upcoming execution time
- **Recent Runs**: History of schedule triggers

### Schedule Details

For each schedule, you can view:
- **Configuration**: Schedule specification and parameters
- **Execution History**: Previous workflow runs triggered by the schedule
- **Workflow Outcomes**: Success/failure status of scheduled workflows
- **Pause/Resume**: Manual control over schedule execution

### Managing Scheduled Workflows

Our `ScheduledReportWorkflow` example demonstrates:
- **Cron Expression**: `0 */5 * * * *` (every 5 minutes)
- **Workflow Parameters**: Passed to each scheduled execution
- **History Tracking**: All runs visible in the UI

## Search and Filtering Capabilities

### Advanced Filtering

The UI provides powerful filtering options:

#### By Status
- **Running**: Currently active workflows
- **Completed**: Successfully finished workflows
- **Failed**: Workflows that encountered errors
- **Terminated**: Manually stopped workflows
- **Timed Out**: Workflows that exceeded time limits

#### By Workflow Properties
- **Workflow ID**: Exact or partial matching
- **Workflow Type**: Filter by specific workflow classes
- **Task Queue**: Filter by worker assignment
- **Start Time**: Date range filtering
- **End Time**: Completion time filtering

#### Search Syntax
```
// Search examples
WorkflowId = "order-12345"
WorkflowType = "OrderSagaWorkflow"
StartTime BETWEEN "2024-01-01" AND "2024-01-02"
ExecutionStatus = "Failed"
```

### Custom Searches

Create complex queries combining multiple criteria:
- Logical operators (AND, OR, NOT)
- Comparison operators (=, !=, >, <, BETWEEN)
- String matching (exact, partial, regex)
- Time range specifications

## Best Practices for UI Usage

### Monitoring Workflows

#### Regular Monitoring Tasks
1. **Check Recent Failures**: Review failed workflows daily
2. **Monitor Long-Running Workflows**: Track progress of extended executions
3. **Validate Schedule Execution**: Ensure scheduled workflows run as expected
4. **Review Activity Retries**: Identify patterns in activity failures

#### Performance Monitoring
- **Execution Duration**: Track workflow completion times
- **Activity Performance**: Monitor individual activity execution times
- **Queue Depth**: Check for task queue backlogs
- **Worker Utilization**: Ensure adequate worker capacity

### Debugging Workflows

#### Systematic Debugging Approach
1. **Start with Summary**: Review basic execution information
2. **Check Event History**: Trace execution flow chronologically
3. **Examine Failed Activities**: Focus on error messages and stack traces
4. **Use Queries**: Check workflow state at failure points
5. **Review Input Data**: Validate parameters passed to activities

#### Common Debug Scenarios

##### Failed SAGA Workflows
1. Identify which step failed in the event history
2. Check compensation actions that were triggered
3. Verify rollback operations completed successfully
4. Review input data for validation issues

##### Stuck Workflows
1. Check the call stack for pending activities
2. Verify worker processes are running and healthy
3. Look for timeout configurations
4. Check task queue assignments

##### Parent-Child Workflow Issues
1. Review parent workflow for child spawning logic
2. Check individual child workflow executions
3. Verify result aggregation in parent workflow
4. Monitor child workflow completion timing

### Performance Optimization

#### Using UI Data for Optimization
- **Identify Bottlenecks**: Find slow activities or workflow steps
- **Optimize Retry Policies**: Adjust based on failure patterns
- **Scale Workers**: Use queue depth information for scaling decisions
- **Tune Timeouts**: Set appropriate timeouts based on actual execution times

### UI Configuration

#### Time Display Options
- **UTC**: Standard coordinated universal time
- **Local**: Browser timezone display
- **Relative**: Human-readable relative times ("2 hours ago")

#### Data Export
- **JSON Export**: Download complete event history
- **Workflow Metadata**: Export execution summaries
- **Activity Results**: Extract activity output data

### Integration with Development Workflow

#### Local Development
1. **Run Infrastructure**: `deno task up` to start UI
2. **Execute Workflows**: Use REST endpoints or tests
3. **Monitor Progress**: Watch executions in real-time
4. **Debug Issues**: Use UI for immediate troubleshooting

#### Testing Integration  
1. **Test Execution**: Run workflow tests and verify in UI
2. **Failure Scenarios**: Trigger failures and observe compensation
3. **Time Skipping**: Use test time manipulation and verify in UI
4. **Performance Validation**: Measure execution times through UI

### Security Considerations

#### Access Control
- **Namespace Isolation**: Use namespaces for environment separation
- **Authentication**: Configure appropriate access controls
- **Data Sensitivity**: Be aware of sensitive data in workflow inputs/outputs

#### Data Protection
- **Codec Servers**: Use for encrypting sensitive workflow data
- **Audit Trails**: Leverage event history for compliance requirements
- **Data Retention**: Configure appropriate data retention policies

## Troubleshooting Common Issues

### UI Access Issues
- **Connection Problems**: Verify Temporal server is running
- **Port Conflicts**: Ensure port 8088 is available
- **Browser Compatibility**: Use modern browsers for full functionality

### Display Issues
- **Missing Data**: Check namespace selection
- **Slow Loading**: Consider reducing query scope
- **Memory Usage**: Clear browser cache for large result sets

### Workflow Visibility
- **Missing Workflows**: Verify correct namespace and time range
- **Incomplete History**: Check retention policies
- **Permission Issues**: Ensure proper access rights

This comprehensive guide should help you effectively use the Temporal Web UI for monitoring and debugging your Quarkus-Temporal workflows. The UI is an essential tool for understanding workflow behavior and maintaining production systems.