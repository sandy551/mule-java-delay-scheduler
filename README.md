# MuleSoft Java In-Memory Scheduler

A lightweight, self-contained solution for implementing precise time-delayed processing in MuleSoft 4 applications without relying on external systems like object stores, databases, or message queues.

## 🚀 Overview

This project provides a simple yet powerful Java-based scheduling mechanism. It allows you to process individual MuleSoft messages after a specific, configurable delay directly from heap memory. It's ideal for use cases such as delayed retries, time-based triggers, grace periods, and scheduled notifications, where using external systems would be overkill.

## ❓ The Problem It Solves

In MuleSoft, implementing delayed processing for individual messages is challenging:

- **Object Stores**: Are batch-oriented and make it difficult to manage unique delays for each request.
- **VM Queues**: Process messages immediately, lacking any delay mechanism.
- **Message Queues (MQ)**: While some support delivery delays, they introduce significant operational overhead, external dependencies, and complexity for a simple requirement.

This project solves these issues by keeping everything in-memory within the Mule runtime itself.

## ✨ Features

- **Precision Scheduling**: Trigger flows with second-level precision for each message.
- **Zero External Dependencies**: No need for Redis, RabbitMQ, or any other external broker or database.
- **Simple Cancellation**: Easily cancel any scheduled task using its unique `appId`.
- **Flexible Processing**: Choose to process the task entirely in Java or call back to a Mule flow via HTTP.
- **Thread-Safe**: Built using `ScheduledExecutorService` and `ConcurrentHashMap` for safe use in concurrent environments.
- **Lightweight**: Minimal overhead and memory footprint.

## 🏗️ Architecture & Flow

### How It Works

1. An inbound HTTP request hits the Mule flow (`/api/submit`) with an `appId` and a payload.
2. The flow extracts the parameters and invokes the `TimerManager.schedule()` Java method.
3. The `TimerManager` stores the payload in a static `ConcurrentHashMap` and schedules a `Runnable` task with the specified delay.
4. After the delay, the task executes. It can either:
   - **Option A**: Process the business logic directly inside the Java method.
   - **Option B**: Make an HTTP POST request back to another Mule flow (`/api/internal/process`) to continue processing within Mule.
5. The task cleans up by removing its data from the maps.

### Component Diagram
```
[HTTP Request] --> [Mule /api/submit Flow] --> [Java TimerManager.schedule()]
                                                     |
                                                     | (stores in memory)
                                                     |
                                      [ScheduledExecutorService]---(after delay)---> [processApp()]
                                                                                           |
                                                                                           |--> [Option A: Java Logic]
                                                                                           |
                                                                                           |--> [Option B: Mule /api/internal/process Flow]
```

## 📋 Prerequisites

- Anypoint Studio 7.12+ (or another IDE)
- Mule Runtime 4.4.0+
- JDK 17

## 🛠️ Installation & Setup

1. **Create a New Mule Project**: In Anypoint Studio, create a new Mule project.
2. **Add the Java Class**:
   - Create a new Java source folder (`src/main/java`).
   - Create the package `com.example.timer`.
   - Create a new class file named `TimerManager.java` and paste the provided code.
3. **Configure the Mule Flows**:
   - Copy the provided Mule XML configuration into your `.xml` flow file.
4. **Update the POM**:
   - Ensure your `pom.xml` matches the provided one, especially the Java version and Mule dependencies.

## 🚦 Usage

### 1. Scheduling a Delayed Task

**Endpoint:** `POST /api/submit?appid=<your_app_id>`

**Example using curl:**

```bash
curl -X POST "http://localhost:8081/api/submit?appid=order-12345" \
-H "Content-Type: application/json" \
-d '{"customerId": "abc-567", "amount": 299.99, "note": "Process this in 53 seconds"}'

## Response:
```json
{
  "status": "scheduled",
  "appId": "order-12345",
  "scheduledAt": "2023-10-27T12:34:56.789Z"
}
```

## Cancelling a Scheduled Task
**Endpoint**: `POST /api/cancel`

**Example using curl:**
```bash
curl -X POST "http://localhost:8081/api/cancel" \
-H "Content-Type: text/plain" \
-d "order-12345"
```
**Response:** The response payload will indicate the status (e.g., `cancelled`, `not_found`).

## ⚙️ Configuration
### Default Delay
The default delay is set to 53 seconds in the `schedule(String, String)` method. You can easily change this.

## Custom Delays
To use a custom delay, invoke the overloaded method:
```
<java:invoke-static class="com.example.timer.TimerManager" method="schedule(java.lang.String, java.lang.String, long)">
    <java:args>#[{
        appId: vars.appId as String,
        payloadJson: vars.payloadJson as String,
        delaySeconds: 120L // 2 minutes
    }]</java:args>
</java:invoke-static>
```

## Processing Logic (Key Step!)
You must implement the `processApp()` method inside the `TimerManager` class. This is where your business logic lives.

### Example (Option A - Pure Java):
```java
public static void processApp(String appId, String payloadJson) {
    System.out.println("PROCESSING: " + appId + " with data: " + payloadJson);
    // Add your logic here: call a service, transform data, log to DB, etc.
}
```

## Example (Option B - Callback to Mule):

- Uncomment the `callInternalMuleEndpoint(appId, payloadJson);` line inside `processApp()`.

- Ensure the `internalProcessFlow` in your Mule config is active and the endpoint URL is correct.

## ⚠️ Important Considerations & Limitations
- **Volatile Storage:** All scheduled tasks are stored in RAM. They will be lost if the Mule application is restarted. This solution is not for mission-critical, persistent scheduling.

- **Single-Node Only:** The scheduler is in-memory and local to one Mule runtime instance. It is not suitable for a clustered deployment.

- **Memory Usage:** Be mindful of heap memory usage (`JVM_MEMMORY_USED`) if scheduling a very large number of tasks simultaneously.

- **Garbage Collection:** Long-lived scheduled tasks are held in memory until execution, which may impact GC behavior under very high load.

## 📊 Monitoring
Monitor your application using the **Anypoint Runtime Manager** dashboard. Key metrics to watch:

- **JVM Memory Usage:** Ensure it remains within healthy limits.

- **CPU Usage:** Should be minimal for this scheduler.

- **Custom Logs:** The `TimerManager` class logs to standard out, which can be viewed in Runtime Manager or your console.

## 🤝 Contributing
Contributions, issues, and feature requests are welcome! Feel free to check the issues page.

## 📄 License
This project is licensed under the MIT License - see the [LICENSE.md]([url](https://license.md/)) file for details.
