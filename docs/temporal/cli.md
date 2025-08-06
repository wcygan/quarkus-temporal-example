# Temporal CLI Documentation

## Overview

The Temporal CLI is a powerful command-line tool that provides direct terminal access to Temporal Services, enabling developers and operators to manage, monitor, and debug Temporal Applications effectively. It serves as the primary interface for workflow operations, activity management, and server administration.

### Key Capabilities
- Workflow and activity lifecycle management
- Server operations and monitoring
- Development environment setup
- Debugging and troubleshooting
- Batch operations
- Schedule management
- Task queue administration

## Installation and Setup

### Installation Methods

#### macOS
```bash
# Using Homebrew (recommended)
brew install temporal

# Using CDN download
curl -sSf https://temporal.download/cli.sh | sh
```

#### Linux
```bash
# Using CDN download
curl -sSf https://temporal.download/cli.sh | sh
```

#### Windows
```powershell
# Download from CDN
# Visit https://temporal.download/cli and follow Windows instructions
```

#### Docker
```bash
# Run without installation
docker run --rm temporalio/temporal --help

# Create alias for easier usage
alias temporal="docker run --rm -v \$(pwd):\$(pwd) -w \$(pwd) temporalio/temporal"
```

### Environment Configuration

#### Setting Up Custom Environments
```bash
# Set default environment
temporal env set --env production \
  --address temporal.company.com:7233 \
  --namespace production \
  --tls-cert-path /path/to/cert.pem \
  --tls-key-path /path/to/key.pem

# List configured environments
temporal env list

# Get current environment
temporal env get
```

#### Environment Variables
```bash
export TEMPORAL_CLI_ADDRESS=localhost:7233
export TEMPORAL_CLI_NAMESPACE=default
export TEMPORAL_CLI_TLS_CA=/path/to/ca.pem
export TEMPORAL_CLI_TLS_CERT=/path/to/cert.pem
export TEMPORAL_CLI_TLS_KEY=/path/to/key.pem
```

### Auto-completion Setup

#### Bash
```bash
echo 'source <(temporal completion bash)' >> ~/.bashrc
```

#### Zsh
```bash
echo 'source <(temporal completion zsh)' >> ~/.zshrc
```

#### Fish
```bash
temporal completion fish | source
```

## Core Command Categories

### 1. Server Management (`temporal server`)

#### Development Server
```bash
# Start development server with embedded UI
temporal server start-dev

# Start with custom database
temporal server start-dev --db-filename temporal.db

# Start with custom ports
temporal server start-dev --port 7233 --http-port 8080

# Start with custom namespaces
temporal server start-dev --namespace development --namespace testing
```

The development server includes:
- Temporal Server
- Web UI (http://localhost:8233)
- In-memory SQLite database
- Auto-created default namespace

### 2. Workflow Operations (`temporal workflow`)

#### Starting Workflows
```bash
# Basic workflow start
temporal workflow start \
  --task-queue my-task-queue \
  --type MyWorkflowType \
  --workflow-id unique-workflow-id \
  --input '{"key": "value"}'

# Start with JSON file input
temporal workflow start \
  --task-queue my-task-queue \
  --type MyWorkflowType \
  --workflow-id unique-workflow-id \
  --input-file input.json

# Start workflow with memo and search attributes
temporal workflow start \
  --task-queue my-task-queue \
  --type MyWorkflowType \
  --workflow-id unique-workflow-id \
  --memo '{"environment": "production"}' \
  --search-attr '{"CustomerId": "12345"}'
```

#### Workflow Lifecycle Management
```bash
# List workflows
temporal workflow list

# List with filters
temporal workflow list \
  --query 'WorkflowType="MyWorkflowType" AND CloseTime IS NULL'

# Describe workflow
temporal workflow describe --workflow-id my-workflow-id

# Show workflow history
temporal workflow show --workflow-id my-workflow-id

# Cancel workflow
temporal workflow cancel --workflow-id my-workflow-id

# Terminate workflow
temporal workflow terminate --workflow-id my-workflow-id --reason "Manual termination"

# Reset workflow
temporal workflow reset --workflow-id my-workflow-id --event-id 10
```

#### Workflow Queries and Signals
```bash
# Query workflow state
temporal workflow query \
  --workflow-id my-workflow-id \
  --type getStatus

# Send signal to workflow
temporal workflow signal \
  --workflow-id my-workflow-id \
  --name updateConfig \
  --input '{"newConfig": "value"}'

# Update workflow
temporal workflow update \
  --workflow-id my-workflow-id \
  --name updateHandler \
  --input '{"data": "new-value"}'
```

### 3. Activity Operations (`temporal activity`)

#### Activity Management
```bash
# Complete activity manually
temporal activity complete \
  --activity-id my-activity-id \
  --workflow-id my-workflow-id \
  --result '{"success": true}'

# Fail activity
temporal activity fail \
  --activity-id my-activity-id \
  --workflow-id my-workflow-id \
  --reason "Activity failed due to external error"
```

### 4. Task Queue Management (`temporal task-queue`)

#### Task Queue Operations
```bash
# List task queues
temporal task-queue list

# Describe task queue
temporal task-queue describe --task-queue my-task-queue

# Get task queue stats
temporal task-queue list-partition --task-queue my-task-queue
```

### 5. Schedule Management (`temporal schedule`)

#### Schedule Operations
```bash
# Create schedule
temporal schedule create \
  --schedule-id my-schedule \
  --cron "0 12 * * *" \
  --workflow-type MyScheduledWorkflow \
  --task-queue my-task-queue

# List schedules
temporal schedule list

# Describe schedule
temporal schedule describe --schedule-id my-schedule

# Update schedule
temporal schedule update \
  --schedule-id my-schedule \
  --cron "0 6 * * *"

# Delete schedule
temporal schedule delete --schedule-id my-schedule

# Trigger schedule immediately
temporal schedule trigger --schedule-id my-schedule
```

### 6. Batch Operations (`temporal batch`)

#### Batch Workflow Operations
```bash
# Batch terminate workflows
temporal batch terminate \
  --query 'WorkflowType="MyWorkflowType" AND CloseTime IS NULL' \
  --reason "Batch termination"

# Batch cancel workflows
temporal batch cancel \
  --query 'ExecutionStatus="Running"' \
  --reason "Maintenance window"

# Batch signal workflows
temporal batch signal \
  --query 'WorkflowType="MyWorkflowType"' \
  --name pauseWorkflow \
  --input '{"pause": true}'
```

## Common Workflow Operations

### Development Workflow

#### 1. Starting a Development Environment
```bash
# Terminal 1: Start Temporal server
temporal server start-dev

# Terminal 2: Start your application workers
./mvnw quarkus:dev

# Terminal 3: Trigger workflows via CLI
temporal workflow start \
  --task-queue doc-approval-queue \
  --type OrderSagaWorkflow \
  --workflow-id test-order-001 \
  --input '{"orderId": "12345", "amount": 100.0}'
```

#### 2. Monitoring Workflow Execution
```bash
# Watch workflow progress
temporal workflow show --workflow-id test-order-001 --follow

# Check workflow status
temporal workflow describe --workflow-id test-order-001

# Query workflow state
temporal workflow query \
  --workflow-id test-order-001 \
  --type getOrderStatus
```

#### 3. Testing Failure Scenarios
```bash
# Signal workflow to fail at specific step
temporal workflow signal \
  --workflow-id test-order-001 \
  --name failAtStep \
  --input '"PAYMENT"'

# Terminate workflow to test cleanup
temporal workflow terminate \
  --workflow-id test-order-001 \
  --reason "Testing failure scenarios"
```

### Production Operations

#### 1. Monitoring Production Workflows
```bash
# List running workflows
temporal workflow list --query 'ExecutionStatus="Running"'

# Find workflows by customer ID
temporal workflow list \
  --query 'CustomKeywordListFilter="customer:12345"'

# Monitor workflow metrics
temporal workflow list --fields WorkflowId,Type,Status,StartTime
```

#### 2. Emergency Operations
```bash
# Cancel stuck workflows
temporal batch cancel \
  --query 'StartTime < "2024-01-01T00:00:00Z" AND ExecutionStatus="Running"' \
  --reason "Emergency maintenance"

# Reset workflows to specific point
temporal workflow reset \
  --workflow-id problematic-workflow \
  --event-id 25 \
  --reason "Reset to before problematic activity"
```

## Debugging and Troubleshooting Commands

### Workflow Debugging

#### Analyzing Workflow History
```bash
# Show detailed workflow history
temporal workflow show --workflow-id my-workflow-id

# Show history in JSON format
temporal workflow show --workflow-id my-workflow-id --output json

# Show specific event range
temporal workflow show \
  --workflow-id my-workflow-id \
  --event-id 10 \
  --max-field-length 1000
```

#### Finding Problematic Workflows
```bash
# Find failed workflows in last 24 hours
temporal workflow list \
  --query 'ExecutionStatus="Failed" AND StartTime > "2024-01-01T00:00:00Z"'

# Find long-running workflows
temporal workflow list \
  --query 'ExecutionStatus="Running" AND StartTime < "2023-12-01T00:00:00Z"'

# Find workflows with specific error patterns
temporal workflow list \
  --query 'WorkflowType="OrderSagaWorkflow" AND ExecutionStatus="Failed"'
```

### Activity Debugging

#### Activity Analysis
```bash
# Show workflow with activity details
temporal workflow show --workflow-id my-workflow-id --activity-details

# Find workflows with failed activities
temporal workflow list \
  --query 'ExecutionStatus="Running"' \
  | grep -A5 -B5 "ActivityTaskFailed"
```

### Task Queue Debugging

#### Task Queue Health
```bash
# Check task queue health
temporal task-queue describe --task-queue my-task-queue

# Monitor task queue backlog
temporal task-queue list-partition --task-queue my-task-queue

# Check worker connectivity
temporal task-queue get-build-ids --task-queue my-task-queue
```

## Best Practices and Useful Command Patterns

### Development Best Practices

#### 1. Environment Management
```bash
# Use different environments for different stages
temporal env set --env local --address localhost:7233
temporal env set --env staging --address staging.temporal.company.com:7233
temporal env set --env prod --address prod.temporal.company.com:7233

# Switch environments easily
temporal env use local
```

#### 2. Workflow ID Patterns
```bash
# Use structured workflow IDs for better organization
temporal workflow start \
  --workflow-id "order-saga-${environment}-${timestamp}-${orderId}" \
  --task-queue doc-approval-queue \
  --type OrderSagaWorkflow
```

#### 3. Input Validation
```bash
# Validate JSON input before starting workflows
echo '{"orderId": "12345"}' | jq '.' && \
temporal workflow start \
  --workflow-id test-workflow \
  --task-queue my-queue \
  --type MyWorkflow \
  --input '{"orderId": "12345"}'
```

### Operational Best Practices

#### 1. Monitoring Scripts
```bash
#!/bin/bash
# monitor-workflows.sh
while true; do
  echo "=== $(date) ==="
  echo "Running workflows:"
  temporal workflow list --query 'ExecutionStatus="Running"' --limit 10
  echo ""
  sleep 30
done
```

#### 2. Bulk Operations with Safety
```bash
# Always test batch operations with dry-run first
temporal batch describe \
  --query 'WorkflowType="OldWorkflowType"'

# Then execute with confirmation
temporal batch terminate \
  --query 'WorkflowType="OldWorkflowType"' \
  --reason "Migration to new workflow type" \
  --yes
```

#### 3. Backup Critical Workflows
```bash
# Export workflow history for important workflows
temporal workflow show --workflow-id critical-workflow-001 \
  --output json > workflow-backup-$(date +%Y%m%d).json
```

### Useful Command Aliases

```bash
# Add to ~/.bashrc or ~/.zshrc
alias tw='temporal workflow'
alias tws='temporal workflow start'
alias twl='temporal workflow list'
alias twd='temporal workflow describe'
alias tq='temporal task-queue'
alias ts='temporal schedule'
alias tb='temporal batch'

# Quick workflow status check
alias workflow-status='temporal workflow list --query "ExecutionStatus=\"Running\""'

# Quick development server start
alias temporal-dev='temporal server start-dev --log-level info'
```

## Output Formats and Integration

### JSON Output for Scripting
```bash
# Get workflow data in JSON for processing
temporal workflow describe --workflow-id my-workflow \
  --output json | jq '.workflowExecutionInfo.status'

# List workflows and extract specific fields
temporal workflow list --output json | \
  jq '.workflowExecutions[] | {id: .execution.workflowId, type: .type.name}'
```

### Integration with CI/CD
```bash
# Health check script for CI/CD
#!/bin/bash
set -e

# Check if Temporal server is healthy
temporal cluster health

# Verify specific namespace is accessible
temporal namespace describe --namespace production

# Check critical task queues
temporal task-queue describe --task-queue critical-queue

echo "Temporal health check passed"
```

This documentation provides a comprehensive guide to using the Temporal CLI effectively in both development and production environments. Refer to `temporal --help` for the most up-to-date command options and syntax.