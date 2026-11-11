package de.com.saenz.minidoodle.gatling;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Gatling Load & Performance Simulation for MiniDoodle REST API.
 * <p>
 * Evaluates high-concurrency capabilities across three core scenarios:
 * 1. Race Condition (Double-Booking): 100 concurrent users attempt to book the exact same slot at the exact same millisecond (@Version Optimistic Locking).
 * 2. Read-Heavy (Aggregated View): 200 concurrent users continuously query the aggregated availability endpoint stressing B-Tree indexes.
 * 3. Basic User Journey: Sequential lifecycle (Create Free Slot -> Check Availability -> Schedule Meeting).
 */
public class MiniDoodleSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    // HTTP Protocol Configuration
    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .shareConnections();

    // -------------------------------------------------------------------------
    // 1. RACE CONDITION SCENARIO (DOUBLE-BOOKING / OPTIMISTIC LOCKING)
    // -------------------------------------------------------------------------
    // 100 concurrent users send a meeting booking request for the exact same slot/timeframe.
    // Under JPA @Version Optimistic Locking:
    // - Exactly 1 request succeeds with 201 Created.
    // - The remaining 99 requests fail with 409 Conflict (OptimisticLockException / State Conflict).
    // - 0 requests result in 500 Internal Server Error (no unhandled database crash).
    private static final String CONCURRENT_START = "2026-12-01T10:00:00Z";
    private static final String CONCURRENT_END = "2026-12-01T11:00:00Z";

    private final ScenarioBuilder raceConditionScenario = scenario("Scenario 1 - Double-Booking Race Condition")
            .exec(
                    http("Concurrent Meeting Booking")
                            .post("/api/v1/meetings")
                            .body(StringBody(String.format("""
                                    {
                                      "title": "Concurrent Team Sync",
                                      "description": "Double-booking stress test",
                                      "organizerId": 1,
                                      "participantIds": [2],
                                      "startTime": "%s",
                                      "endTime": "%s"
                                    }
                                    """, CONCURRENT_START, CONCURRENT_END)))
                            // Both 201 (winner) and 409 (safe optimistic lock conflict) are valid business outcomes
                            .check(status().in(201, 409))
            );

    // -------------------------------------------------------------------------
    // 2. READ-HEAVY SCENARIO (AGGREGATED VIEW / B-TREE INDEX STRESS)
    // -------------------------------------------------------------------------
    // 200 users constantly query the availability aggregate endpoint within a wide timeframe
    // to stress-test PostgreSQL B-Tree composite indexes: (start_time, end_time, status).
    private final ScenarioBuilder readHeavyScenario = scenario("Scenario 2 - Read-Heavy Availability Queries")
            .during(Duration.ofSeconds(30)).on(
                    exec(
                            http("Query Aggregated Availability")
                                    .get("/api/v1/availability/aggregate")
                                    .queryParam("startTime", "2026-01-01T00:00:00Z")
                                    .queryParam("endTime", "2026-12-31T23:59:59Z")
                                    .queryParam("freeOnly", "true")
                                    .check(status().is(200))
                    ).pause(Duration.ofMillis(100), Duration.ofMillis(300))
            );

    // -------------------------------------------------------------------------
    // 3. BASIC USER JOURNEY SCENARIO (SEQUENTIAL E2E FLOW)
    // -------------------------------------------------------------------------
    // Sequential user workflow:
    // 1. Declare proactive FREE time slot (POST /api/v1/time-slots)
    // 2. Query user's personal calendar (GET /api/v1/availability/users/{userId})
    // 3. Schedule meeting consuming the free slot (POST /api/v1/meetings)
    private static final AtomicLong USER_JOURNEY_OFFSET = new AtomicLong(100);

    private final Iterator<Map<String, Object>> userJourneyFeeder = Stream.generate((Supplier<Map<String, Object>>) () -> {
        long offsetHours = USER_JOURNEY_OFFSET.getAndIncrement();
        Instant start = Instant.parse("2027-01-01T08:00:00Z").plus(offsetHours, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        return Map.of(
                "journeyStart", start.toString(),
                "journeyEnd", end.toString()
        );
    }).iterator();

    private final ScenarioBuilder basicUserJourneyScenario = scenario("Scenario 3 - Basic User Journey")
            .feed(userJourneyFeeder)
            // Step 1: Create a proactive FREE time slot for organizer (ID: 1)
            .exec(
                    http("1. Proactively Declare Free Time Slot")
                            .post("/api/v1/time-slots")
                            .body(StringBody("""
                                    {
                                      "userId": 1,
                                      "startTime": "#{journeyStart}",
                                      "endTime": "#{journeyEnd}"
                                    }
                                    """))
                            .check(status().is(201))
            )
            .pause(Duration.ofMillis(250))
            // Step 2: Also declare matching FREE slot for participant (ID: 2) so meeting can be booked
            .exec(
                    http("2. Declare Free Time Slot for Participant")
                            .post("/api/v1/time-slots")
                            .body(StringBody("""
                                    {
                                      "userId": 2,
                                      "startTime": "#{journeyStart}",
                                      "endTime": "#{journeyEnd}"
                                    }
                                    """))
                            .check(status().is(201))
            )
            .pause(Duration.ofMillis(250))
            // Step 3: Check availability / personal calendar
            .exec(
                    http("3. Check Personal Calendar Availability")
                            .get("/api/v1/availability/users/1")
                            .queryParam("startTime", "#{journeyStart}")
                            .queryParam("endTime", "#{journeyEnd}")
                            .check(status().is(200))
            )
            .pause(Duration.ofMillis(250))
            // Step 4: Schedule the meeting, converting both slots from FREE to BUSY
            .exec(
                    http("4. Schedule Meeting Consuming Time Slots")
                            .post("/api/v1/meetings")
                            .body(StringBody("""
                                    {
                                      "title": "Sprint Review & Retro",
                                      "description": "Sequential user journey booked meeting",
                                      "organizerId": 1,
                                      "participantIds": [2],
                                      "startTime": "#{journeyStart}",
                                      "endTime": "#{journeyEnd}"
                                    }
                                    """))
                            .check(status().is(201))
            );

    // -------------------------------------------------------------------------
    // LOAD INJECTION PROFILE
    // -------------------------------------------------------------------------
    {
        setUp(
                // 1. Race Condition: Exactly 100 concurrent users firing at the exact same millisecond
                raceConditionScenario.injectOpen(
                        atOnceUsers(100)
                ),

                // 2. Read Heavy: 200 users concurrently querying availability for 30 seconds
                readHeavyScenario.injectClosed(
                        constantConcurrentUsers(200).during(Duration.ofSeconds(30))
                ),

                // 3. Basic User Journey: Ramp up 50 users over 20 seconds executing the full lifecycle
                basicUserJourneyScenario.injectOpen(
                        rampUsers(50).during(Duration.ofSeconds(20))
                )
        ).protocols(httpProtocol)
         .maxDuration(Duration.ofSeconds(35))
         .assertions(
                 global().successfulRequests().percent().gte(95.0),
                 global().responseTime().percentile(95).lt(500)
         );
    }
}
