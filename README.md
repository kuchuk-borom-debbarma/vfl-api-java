# Visual Flow Logger (VFL) Client API Documentation

## Overview

The Visual Flow Logger (VFL) Client API is a Java-based framework that provides comprehensive flow tracing and logging capabilities for distributed applications. It captures execution flow across method calls, remote services, and event-driven architectures, sending structured data to a VFL Hub for visualization and analysis.

## Architecture

### Core Components

- **Annotations**: Method-level annotations for defining flow boundaries
- **Buffer System**: Collects and batches data before sending to VFL Hub
- **Flush Handlers**: Manages data transmission to external systems
- **Bytecode Instrumentation**: Runtime method interception using ByteBuddy

### Data Flow

1. **Capture**: Annotations intercept method execution
2. **Buffer**: Data is collected in memory buffers
3. **Flush**: Batched data is sent to VFL Hub via HTTP
4. **Visualize**: VFL Hub processes and displays flow diagrams

## Quick Start

### 1. Initialize VFL

```java
// Initialize with default settings
VFLAnnotation.initialize();

// Or initialize with custom buffer and flush handler
VFLBuffer buffer = new SynchronousBuffer(
    new VFLHubFlushHandler("http://your-vfl-hub:8080"), 
    100
);
VFLAnnotation.initialize(buffer);
```

### 2. Annotate Your Methods

```java
@RootBlock
public void processOrder(String orderId) {
    info("Starting order processing for: {}", orderId);
    
    validateOrder(orderId);
    processPayment(orderId);
    fulfillOrder(orderId);
    
    info("Order processing completed");
}

@SubBlock
private void validateOrder(String orderId) {
    info("Validating order: {}", orderId);
    // validation logic
}
```

## Annotations

### @RootBlock

Marks the entry point of a flow trace. Creates a top-level block with no parent.

```java
@RootBlock
public void handleRequest(HttpRequest request) {
    // This creates the root of the execution tree
}
```

**Key Features:**
- Automatically creates and manages block context
- Forces buffer flush when method completes
- Handles exceptions and logs them appropriately

### @SubBlock

Creates child blocks within an existing flow context.

```java
@SubBlock(blockName = "validation", startMessage = "Starting validation", endMessage = "Validation complete")
private void validateData(Data data) {
    // This creates a child block under the current context
}
```

**Parameters:**
- `blockName`: Custom name for the block (defaults to method name)
- `startMessage`: Message logged when entering the block
- `endMessage`: Message logged when exiting the block

### @RemoteBlock

Marks methods that execute in remote contexts, typically after receiving remote calls.

```java
@RemoteBlock
public void handleRemoteCall(RemoteBlockWrapper wrapper) {
    // This method executes on a remote service
    // The wrapper contains the original block context
}
```

**Usage Pattern:**
1. Local service calls `VFLAnnotation.RemoteBlock()` before making remote call
2. Remote service method is annotated with `@RemoteBlock`
3. Remote method receives `RemoteBlockWrapper` containing context

### @EventListenerBlock

Handles asynchronous event processing with proper context linking.

```java
@EventListenerBlock
public void onOrderEvent(PublishContext context, OrderEvent event) {
    // This creates a listener block linked to the publisher
}
```

**Requirements:**
- Method must accept `PublishContext` as first parameter
- Context links listener back to the event publisher

## Logging API

The framework extends `VFLBase` to provide structured logging within flow contexts.

### Basic Logging

```java
public class OrderService extends VFLBase {
    @Override
    protected BlockContext getBlockContext() {
        return VFLAnnotation.getBlockContext();
    }
    
    @Override
    protected VFLBuffer getVFLBuffer() {
        return VFLAnnotation.getBuffer();
    }
    
    @RootBlock
    public void processOrder(String orderId) {
        info("Processing order: {}", orderId);
        warn("Order {} has expired items", orderId);
        error("Failed to process order: {}", orderId);
    }
}
```

### Available Methods

- `info(String message, Object... args)`: Informational logging
- `warn(String message, Object... args)`: Warning messages
- `error(String message, Object... args)`: Error conditions

Messages support placeholder formatting using `{}` syntax.

## Advanced Features

### Remote Service Integration

For distributed tracing across services:

```java
// In the calling service
@SubBlock
public OrderResult callRemoteService(String orderId) {
    return VFLAnnotation.RemoteBlock("remote-validation", "Calling validation service", 
        (remoteWrapper) -> {
            // Make HTTP call, pass remoteWrapper.remoteBlock context
            return httpClient.validateOrder(orderId, remoteWrapper);
        });
}

// In the remote service
@RemoteBlock
public ValidationResult validateOrder(String orderId, RemoteBlockWrapper wrapper) {
    info("Remote validation started for: {}", orderId);
    // validation logic
    return result;
}
```

### Event-Driven Architecture

For asynchronous event handling:

```java
// Publishing events
@SubBlock
public void publishOrderEvent(String orderId) {
    PublishContext context = VFLAnnotation.CreatePublishContext(
        "order-publisher", 
        "Publishing order event for: {}", 
        orderId
    );
    
    eventBus.publish(new OrderEvent(orderId, context));
}

// Listening to events
@EventListenerBlock
public void handleOrderEvent(PublishContext context, OrderEvent event) {
    info("Received order event: {}", event.getOrderId());
    // event processing logic
}
```

## Buffer Configuration

### Synchronous Buffer

Best for simple applications with low throughput:

```java
VFLBuffer buffer = new SynchronousBuffer(
    new VFLHubFlushHandler("http://vfl-hub:8080"),
    50 // Flush every 50 items
);
```

**Characteristics:**
- Thread-safe with read-write locks
- Blocks on flush operations
- Lower memory overhead
- Suitable for single-threaded or low-concurrency applications

### Asynchronous Buffer

Recommended for high-throughput applications:

```java
ExecutorService flushExecutor = Executors.newFixedThreadPool(2);
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

VFLBuffer buffer = new AsynchronousBuffer(
    100,        // Buffer size
    5000,       // Flush interval (ms)
    30000,      // Flush timeout (ms)
    flushExecutor,
    scheduler,
    new VFLHubFlushHandler("http://vfl-hub:8080")
);
```

**Characteristics:**
- Non-blocking operations
- Periodic and size-based flushing
- Higher throughput
- Requires careful resource management

## Data Model

### Block

Represents a unit of execution:

```java
public class Block {
    private final String id;           // UUID v7
    private final String parentBlockId; // Parent block reference
    private final String name;         // Block name
    private final long createdTime;    // Creation timestamp
}
```

### BlockLog

Represents a log entry within a block:

```java
public class BlockLog {
    private final String id;              // UUID v7
    private final String blockId;         // Associated block
    private final String message;         // Log message
    private final String parentLogId;     // Previous log in chain
    private final String referencedBlockId; // For trace logs
    private final long timestamp;         // Log timestamp
    private final String type;           // Log type (INFO/WARN/ERROR/TRACE_*)
}
```

### Log Types

**Base Types:**
- `INFO`: Informational messages
- `WARN`: Warning conditions
- `ERROR`: Error conditions

**Trace Types:**
- `TRACE_PRIMARY`: Sub-block execution
- `TRACE_REMOTE`: Remote service calls
- `PUBLISH_EVENT`: Event publishing
- `LISTEN_EVENT`: Event consumption

## VFL Hub Integration

### Flush Handler

The `VFLHubFlushHandler` sends data to VFL Hub via REST API:

```java
VFLHubFlushHandler handler = new VFLHubFlushHandler("http://vfl-hub:8080");
```

**API Endpoints:**
- `POST /api/v1/blocks` - Block definitions
- `POST /api/v1/logs` - Log entries
- `POST /api/v1/block-entered` - Block entry timestamps
- `POST /api/v1/block-exited` - Block exit timestamps
- `POST /api/v1/block-returned` - Block completion timestamps

### Custom Flush Handlers

Implement `VFLFlushHandler` for custom integrations:

```java
public class CustomFlushHandler implements VFLFlushHandler {
    @Override
    public void flushLogs(List<BlockLog> logs) {
        // Custom log handling
    }
    
    @Override
    public void flushBlocks(List<Block> blocks) {
        // Custom block handling
    }
    
    // ... implement other methods
}
```

## Error Handling

### Framework Resilience

- Method instrumentation failures are logged but don't break application flow
- Buffer operations are thread-safe and handle concurrent access
- Network failures during flush operations are logged but don't propagate
- Missing context scenarios are handled gracefully with warnings

### Exception Propagation

- Original method exceptions are preserved and re-thrown
- Exception details are logged within the flow context
- Framework exceptions are contained and logged separately

## Performance Considerations

### Memory Usage

- Buffers accumulate data in memory before flushing
- Consider buffer size based on available memory and flush frequency
- Use asynchronous buffers for high-throughput scenarios

### Network Overhead

- Data is sent in batches to minimize network calls
- JSON serialization overhead for HTTP transmission
- Consider VFL Hub proximity and network latency

### Bytecode Instrumentation

- Runtime overhead from method interception
- Thread-local storage for context management
- UUID generation for unique identifiers

## Best Practices

### Annotation Usage

1. **Start with @RootBlock**: Mark main entry points (controllers, message handlers)
2. **Use @SubBlock sparingly**: Focus on significant logical units, not every method
3. **Remote tracing**: Always use RemoteBlockWrapper pattern for cross-service calls
4. **Event handling**: Ensure PublishContext is properly propagated

### Logging Strategy

1. **Meaningful messages**: Use descriptive log messages with context
2. **Appropriate levels**: Use INFO for normal flow, WARN for anomalies, ERROR for failures
3. **Parameter formatting**: Leverage placeholder syntax for better performance
4. **Avoid excessive logging**: Balance detail with performance impact

### Buffer Management

1. **Size appropriately**: Balance memory usage with flush frequency
2. **Monitor performance**: Watch for buffer overflow or excessive flushing
3. **Graceful shutdown**: Ensure proper buffer flushing on application exit
4. **Error handling**: Implement fallback strategies for flush failures

## Troubleshooting

### Common Issues

**No data appearing in VFL Hub:**
- Verify VFL Hub URL configuration
- Check network connectivity
- Ensure buffer is being flushed (call `forceFlush()`)
- Verify annotation processing is working

**Performance degradation:**
- Reduce logging frequency
- Increase buffer size
- Switch to asynchronous buffer
- Check VFL Hub response times

**Context not available:**
- Ensure @RootBlock is properly placed
- Verify thread boundaries for async operations
- Check for proper context propagation in remote calls

**Memory issues:**
- Monitor buffer sizes
- Implement proper cleanup in long-running processes
- Consider flush frequency tuning

### Debugging

Enable debug logging for VFL components:

```xml
<!-- logback.xml -->
<logger name="dev.kuku.vfl" level="DEBUG" />
```

This will show detailed information about:
- Method instrumentation
- Buffer operations
- Flush activities
- Context management

## Migration and Integration

### Existing Applications

1. **Gradual adoption**: Start with @RootBlock on main entry points
2. **Selective instrumentation**: Add @SubBlock to critical paths first
3. **Testing**: Verify flow capture in development environment
4. **Performance validation**: Monitor impact on production systems

### Framework Integration

- **Spring Boot**: Works with component scanning and dependency injection
- **Jakarta EE**: Compatible with CDI and EJB containers
- **Microservices**: Excellent for distributed tracing across services
- **Event-driven**: Native support for async messaging patterns