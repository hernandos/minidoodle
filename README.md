# Mini Doodle - Meeting Scheduling Platform

## Overview
This project is a high-performance simulation of a meeting scheduling platform designed using Java and Spring Boot. It enables users to proactively manage their time slots, schedule meetings, and view custom calendar availability. All data is permanently persisted to allow for proper management and querying.

## Architectural & Design Decisions

### 1. The "Calendar" Concept (Domain-Driven Design)
Following the specification, the term "Calendar" is treated strictly as a domain concept. There is no monolithic `Calendar` entity or table in the database. Instead, a Calendar is logically represented by an aggregate of non-overlapping `TimeSlot`s belonging to a specific user.

### 2. Simplified Data Model
To efficiently support hundreds of users with thousands of slots, the relational model is kept normalized and straightforward:
*   **User**: Represents a registered platform user.
*   **TimeSlot**: Represents a block of time proactively created by a user. It contains `start_time` (timestamp), `end_time` (timestamp), and a `status` (FREE or BUSY). Users can create, modify, and delete their own time slots. Database-level constraints (e.g., exclusion constraints) guarantee that a single user's slots never overlap.
*   **Meeting**: Created when converting an available slot into a booked event. It stores meeting details such as the title and description, and links to the specific `TimeSlot`s that were consumed.

### 3. Proactive Availability & Participant Handling
The platform relies on proactive availability: users must explicitly create available time slots to be bookable.
*   Participants added to a meeting are assumed to be existing platform users.
*   To successfully invite a participant, they must have a pre-existing `FREE` slot matching the meeting's timeframe. If a slot does not exist, the system assumes the user is unavailable.
*   Booking a meeting does not create new overlapping slots; it simply updates the `status` of all involved participants' existing `TimeSlot`s from `FREE` to `BUSY`.
*   While time slots can have configurable durations and be modified, meetings themselves do not have an independent modifiable duration; they inherit the timeframe of the slots they consume.

### 4. Concurrency Control (Preventing Double-Booking)
Given the requirement for a high-performance simulation, converting a `FREE` slot to a `BUSY` meeting is protected against race conditions.
*   **Optimistic Locking** (using a `@Version` field in JPA) is implemented on the `TimeSlot` entity. If two users attempt to book the exact same free slot simultaneously, the database will reject the second transaction, preventing double-booking without the performance penalty of pessimistic database locks.

## Tech Stack & Performance Strategy

### Spring MVC & Database Optimization
To meet the requirement of handling hundreds of users and thousands of slots, the system utilizes standard Spring MVC and a well-indexed relational database (PostgreSQL).
*   **Avoiding Over-engineering**: For this specific volume of data and concurrent users, introducing reactive programming (WebFlux) or complex distributed queues is unnecessary. Standard servlet-based MVC handles hundreds of concurrent connections natively.
*   **Query Optimization**: The requirement to provide an aggregated view for a selected time frame is solved via highly optimized SQL/JPQL queries using B-Tree indexes on `start_time`, `end_time`, and `status`. This avoids iterating through thousands of records in memory and prevents the JPA N+1 problem.

## Future Scalability & Caching Strategy
The current system is designed to efficiently handle the required load (hundreds of users, thousands of slots) using optimized database indexes, as database buffer caches are more than sufficient for this volume.

However, if the platform were to scale to **millions of users** or become highly read-heavy (e.g., users constantly refreshing the aggregated availability view), the following horizontal scaling strategies would be introduced:
*   **Distributed Caching (Redis):** To cache the aggregated views of frequent timeframes.
*   **Cache Invalidation:** Implementing an event-driven cache eviction policy. When a `TimeSlot` is updated or a `Meeting` is booked, an event would clear the specific aggregated timeframe cache entries to prevent stale availability data.
*   **Local Caching (Caffeine):** A first-level L1 cache in the application instances for nearly-static data, though less applicable for highly mutable scheduling data.

## Metrics & Observability
To fulfill the requirement for metrics and ensure the high-performance simulation can be monitored, the service integrates **Spring Boot Actuator** with **Micrometer (Prometheus registry)**.

### Available Metrics
*   **Technical Metrics:** JVM memory, CPU usage, HTTP request latency, and HikariCP database connection pool metrics. Monitoring the DB connection pool is crucial for the concurrent load of hundreds of simulated users.
*   **Business Metrics (Custom):**
    *   `meetings.scheduled.total` (Counter): Tracks the number of successfully booked meetings.
    *   `slots.aggregated.view.time` (Timer): Monitors the execution time of the complex availability queries to ensure the aggregated view remains performant.

## Getting Started

### Prerequisites
*   Docker and Docker Compose installed on your machine.

### Running the Application
The solution is fully containerized and runnable locally using `docker-compose`. All necessary dependencies, including the database, are bundled in the compose file. Database schemas and initial structures are automatically managed on startup via database migration tools (Liquibase).

To start the service, run:
```bash
docker-compose up -d