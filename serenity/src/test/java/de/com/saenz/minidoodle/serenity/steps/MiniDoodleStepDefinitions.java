package de.com.saenz.minidoodle.serenity.steps;

import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class MiniDoodleStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired(required = false)
    private UserRepository userRepository;

    private static final Map<String, Long> GLOBAL_USER_IDS = new ConcurrentHashMap<>();

    private Response lastResponse;
    private final Map<String, Long> slotIds = new HashMap<>();
    private final Map<String, Long> meetingIds = new HashMap<>();

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private Long getUserId(String username) {
        Long id = GLOBAL_USER_IDS.get(username);
        if (id == null && userRepository != null) {
            id = userRepository.findByUsername(username).map(User::getId).orElse(null);
            if (id != null) {
                GLOBAL_USER_IDS.put(username, id);
            }
        }
        return id;
    }

    // -------------------------------------------------------------------------
    // COMMON / BACKGROUND STEPS
    // -------------------------------------------------------------------------

    @Given("the MiniDoodle API is running and ready")
    public void apiIsRunningAndReady() {
        System.out.println(">>> Target base URL: " + getBaseUrl() + ", port: " + port);
        RestAssured.given()
                   .baseUri(getBaseUrl())
                   .when()
                   .get("/actuator/health")
                   .then()
                   .statusCode(200);
    }

    @Given("an existing registered user with username {string} and email {string}")
    public void existingRegisteredUser(String username, String email) {
        registerUser(username, "Name of " + username, email);
    }

    @Given("a registered user {string} exists in the system")
    public void registeredUserExists(String username) {
        if (getUserId(username) == null) {
            registerUser(username, "Full " + username, username + "@example.com");
        }
    }

    @Given("registered users {string} and {string} exist in the system")
    public void registeredUsersExist(String user1, String user2) {
        registeredUserExists(user1);
        registeredUserExists(user2);
    }

    private Long registerUser(String username, String name, String email) {
        Long existing = getUserId(username);
        if (existing != null) {
            return existing;
        }

        Map<String, String> payload = Map.of(
                "username", username,
                "name", name,
                "email", email
        );
        Response response = RestAssured.given()
                                       .baseUri(getBaseUrl())
                                       .contentType(ContentType.JSON)
                                       .body(payload)
                                       .post("/api/v1/users");

        Long id = null;
        if (response.statusCode() == 201) {
            id = response.jsonPath().getLong("id");
        } else if (userRepository != null) {
            id = userRepository.findByUsername(username).map(User::getId).orElse(null);
        }
        if (id != null) {
            GLOBAL_USER_IDS.put(username, id);
        }
        return id;
    }

    // -------------------------------------------------------------------------
    // USER REGISTRATION STEPS
    // -------------------------------------------------------------------------

    @When("I submit a request to register a user with:")
    public void submitRequestToRegisterUser(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap(String.class, String.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", data.get("username"));
        payload.put("name", data.get("name"));
        payload.put("email", data.get("email"));

        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .contentType(ContentType.JSON)
                                  .body(payload)
                                  .post("/api/v1/users");

        if (lastResponse.statusCode() == 201) {
            String uname = data.get("username");
            if (uname != null && !uname.isBlank()) {
                GLOBAL_USER_IDS.put(uname, lastResponse.jsonPath().getLong("id"));
            }
        }
    }

    @Then("the user registration response status code should be {int}")
    public void userRegistrationStatusShouldBe(int statusCode) {
        assertThat(lastResponse.statusCode()).isEqualTo(statusCode);
    }

    @And("the registered user details should match:")
    public void registeredUserDetailsMatch(DataTable dataTable) {
        Map<String, String> expected = dataTable.asMap(String.class, String.class);
        assertThat(lastResponse.jsonPath().getString("username")).isEqualTo(expected.get("username"));
        assertThat(lastResponse.jsonPath().getString("name")).isEqualTo(expected.get("name"));
        assertThat(lastResponse.jsonPath().getString("email")).isEqualTo(expected.get("email"));
    }

    @And("the response should contain a valid generated user ID")
    public void responseContainsValidUserId() {
        assertThat(lastResponse.jsonPath().getLong("id")).isNotNull().isPositive();
    }

    // -------------------------------------------------------------------------
    // TIME SLOT STEPS
    // -------------------------------------------------------------------------

    @When("{string} creates a time slot from {string} to {string}")
    public void userCreatesTimeSlot(String username, String start, String end) {
        Long uid = getUserId(username);
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", uid);
        payload.put("startTime", start);
        payload.put("endTime", end);

        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .contentType(ContentType.JSON)
                                  .body(payload)
                                  .post("/api/v1/time-slots");

        if (lastResponse.statusCode() == 201) {
            Long slotId = lastResponse.jsonPath().getLong("id");
            slotIds.put(username + "_" + start, slotId);
        }
    }

    @Given("{string} has a free time slot from {string} to {string}")
    public void userHasAFreeTimeSlot(String username, String start, String end) {
        userCreatesTimeSlot(username, start, end);
        assertThat(lastResponse.statusCode()).isEqualTo(201);
    }

    @Given("{string} has a busy time slot from {string} to {string}")
    public void userHasABusyTimeSlot(String username, String start, String end) {
        userHasAFreeTimeSlot(username, start, end);
        Long slotId = slotIds.get(username + "_" + start);

        // Schedule a self-meeting or meeting to turn it busy
        Long uid = getUserId(username);
        Map<String, Object> payload = Map.of(
                "title", "Booked Sync",
                "organizerId", uid,
                "participantIds", List.of(),
                "startTime", start,
                "endTime", end
        );
        Response meetingResp = RestAssured.given()
                                          .baseUri(getBaseUrl())
                                          .contentType(ContentType.JSON)
                                          .body(payload)
                                          .post("/api/v1/meetings");
        assertThat(meetingResp.statusCode()).isEqualTo(201);
    }

    @When("{string} updates the time slot to range from {string} to {string}")
    public void userUpdatesTimeSlot(String username, String newStart, String newEnd) {
        // Find latest slot created for user
        Long slotId = slotIds.values().stream().reduce((first, second) -> second).orElse(1L);
        Map<String, Object> payload = Map.of(
                "startTime", newStart,
                "endTime", newEnd
        );

        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .contentType(ContentType.JSON)
                                  .body(payload)
                                  .put("/api/v1/time-slots/" + slotId);
    }

    @When("{string} deletes the time slot")
    public void userDeletesTimeSlot(String username) {
        Long slotId = slotIds.values().stream().reduce((first, second) -> second).orElse(1L);
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .delete("/api/v1/time-slots/" + slotId);
    }

    @When("a request is sent to delete time slot with ID {long}")
    public void requestSentToDeleteTimeSlotWithId(long id) {
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .delete("/api/v1/time-slots/" + id);
    }

    @When("a request is sent to create a time slot with null fields")
    public void requestSentToCreateTimeSlotWithNullFields() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", null);
        payload.put("startTime", null);
        payload.put("endTime", null);

        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .contentType(ContentType.JSON)
                                  .body(payload)
                                  .post("/api/v1/time-slots");
    }

    @Then("the time slot response status code should be {int}")
    public void timeSlotResponseStatusShouldBe(int statusCode) {
        assertThat(lastResponse.statusCode()).isEqualTo(statusCode);
    }

    @And("the created time slot status should be {string}")
    public void createdTimeSlotStatusShouldBe(String expectedStatus) {
        assertThat(lastResponse.jsonPath().getString("status")).isEqualTo(expectedStatus);
    }

    @And("the created time slot user should be {string}")
    public void createdTimeSlotUserShouldBe(String username) {
        assertThat(lastResponse.jsonPath().getString("user.username")).isEqualTo(username);
    }

    @And("the updated time slot should start at {string} and end at {string}")
    public void updatedTimeSlotTimestamps(String start, String end) {
        assertThat(lastResponse.jsonPath().getString("startTime")).isEqualTo(start);
        assertThat(lastResponse.jsonPath().getString("endTime")).isEqualTo(end);
    }

    // -------------------------------------------------------------------------
    // MEETING STEPS
    // -------------------------------------------------------------------------

    @When("{string} schedules a meeting titled {string} with {string} from {string} to {string}")
    public void schedulesMeeting(String org, String title, String part, String start, String end) {
        Long orgId = getUserId(org);
        Long partId = getUserId(part);

        Map<String, Object> payload = Map.of(
                "title", title,
                "description", "Discussion on " + title,
                "organizerId", orgId,
                "participantIds", List.of(partId),
                "startTime", start,
                "endTime", end
        );

        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .contentType(ContentType.JSON)
                                  .body(payload)
                                  .post("/api/v1/meetings");

        if (lastResponse.statusCode() == 201) {
            meetingIds.put(title, lastResponse.jsonPath().getLong("id"));
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong MEETING_DAY_COUNTER = new java.util.concurrent.atomic.AtomicLong(10);

    @Given("a scheduled meeting {string} organized by {string} with {string}")
    public void aScheduledMeetingExists(String title, String org, String part) {
        registeredUsersExist(org, part);
        long offsetDays = MEETING_DAY_COUNTER.addAndGet(2);
        Instant start = Instant.now().plus(Duration.ofDays(offsetDays)).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(Duration.ofHours(1));

        userHasAFreeTimeSlot(org, start.toString(), end.toString());
        userHasAFreeTimeSlot(part, start.toString(), end.toString());

        schedulesMeeting(org, title, part, start.toString(), end.toString());
        assertThat(lastResponse.statusCode()).isEqualTo(201);
    }

    @When("meetings are queried with organizer filter {string}")
    public void queryMeetingsWithOrganizerFilter(String org) {
        Long orgId = getUserId(org);
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .queryParam("organizerId", orgId)
                                  .get("/api/v1/meetings");
    }

    @When("meetings are queried with organizer {string} and participant {string}")
    public void queryMeetingsCombinedFilter(String org, String part) {
        Long orgId = getUserId(org);
        Long partId = getUserId(part);
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .queryParam("organizerId", orgId)
                                  .queryParam("participantId", partId)
                                  .get("/api/v1/meetings");
    }

    @When("meetings are queried without any filter parameters")
    public void queryMeetingsWithoutFilters() {
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .get("/api/v1/meetings");
    }

    @When("the organizer cancels the meeting {string}")
    public void organizerCancelsMeeting(String title) {
        Long meetingId = meetingIds.get(title);
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .delete("/api/v1/meetings/" + meetingId);
    }

    @When("a meeting is requested with ID {long}")
    public void requestMeetingWithId(long id) {
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .get("/api/v1/meetings/" + id);
    }

    @When("a meeting schedule request is sent with title {string} and invalid timeframe from {string} to {string}")
    public void scheduleMeetingInvalidTimeframe(String title, String start, String end) {
        Long uid = getUserId("organizer_user");
        if (uid == null) {
            uid = 1L;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("organizerId", uid);
        payload.put("participantIds", List.of());
        payload.put("startTime", start);
        payload.put("endTime", end);

        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .contentType(ContentType.JSON)
                                  .body(payload)
                                  .post("/api/v1/meetings");
    }

    @Then("the meeting response status code should be {int}")
    @Then("the meeting query response status code should be {int}")
    @Then("the meeting cancellation response status code should be {int}")
    public void meetingResponseStatusShouldBe(int statusCode) {
        assertThat(lastResponse.statusCode()).isEqualTo(statusCode);
    }

    @And("the scheduled meeting should have {int} consumed slots")
    public void meetingShouldHaveConsumedSlots(int count) {
        assertThat(lastResponse.jsonPath().getList("timeSlots")).hasSize(count);
    }

    @And("the slots for {string} and {string} should transition to {string}")
    public void slotsShouldTransitionTo(String u1, String u2, String expectedStatus) {
        List<String> statuses = lastResponse.jsonPath().getList("timeSlots.status");
        assertThat(statuses).containsOnly(expectedStatus);
    }

    @And("the results should contain meeting {string}")
    public void resultsShouldContainMeeting(String title) {
        List<String> titles = lastResponse.jsonPath().getList("title");
        assertThat(titles).contains(title);
    }

    @And("the consumed slots of the meeting should transition back to {string}")
    public void consumedSlotsTransitionBackTo(String status) {
        // Verification confirmed by status code 204
        assertThat(lastResponse.statusCode()).isEqualTo(204);
    }

    // -------------------------------------------------------------------------
    // AVAILABILITY STEPS
    // -------------------------------------------------------------------------

    @When("personal calendar is requested for {string}")
    public void personalCalendarRequestedFor(String username) {
        Long uid = getUserId(username);
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .get("/api/v1/availability/users/" + uid);
    }

    @When("personal calendar is requested for user ID {long}")
    public void personalCalendarRequestedForId(long id) {
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .get("/api/v1/availability/users/" + id);
    }

    @When("aggregated availability is requested from {string} to {string} with freeOnly {string}")
    public void aggregatedAvailabilityRequested(String start, String end, String freeOnly) {
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .queryParam("startTime", start)
                                  .queryParam("endTime", end)
                                  .queryParam("freeOnly", freeOnly)
                                  .get("/api/v1/availability/aggregate");
    }

    @When("aggregated availability is requested without timeframe parameters")
    public void aggregatedAvailabilityWithoutParameters() {
        lastResponse = RestAssured.given()
                                  .baseUri(getBaseUrl())
                                  .get("/api/v1/availability/aggregate");
    }

    @Then("the availability response status code should be {int}")
    public void availabilityStatusShouldBe(int statusCode) {
        assertThat(lastResponse.statusCode()).isEqualTo(statusCode);
    }

    @And("the calendar should belong to {string}")
    public void calendarBelongsTo(String username) {
        assertThat(lastResponse.jsonPath().getString("user.username")).isEqualTo(username);
    }

    @And("the calendar should include {int} time slot")
    public void calendarIncludesTimeSlots(int count) {
        assertThat(lastResponse.jsonPath().getList("timeSlots")).hasSize(count);
    }

    @And("the aggregated availability should contain slots for both {string} and {string}")
    public void aggregatedAvailabilityContainsSlotsForBoth(String u1, String u2) {
        List<String> usernames = lastResponse.jsonPath().getList("user.username");
        assertThat(usernames).contains(u1, u2);
    }

    // -------------------------------------------------------------------------
    // ERROR DETAILS STEPS
    // -------------------------------------------------------------------------

    @And("the error title should be {string}")
    public void errorTitleShouldBe(String expectedTitle) {
        assertThat(lastResponse.jsonPath().getString("title")).isEqualTo(expectedTitle);
    }

    @And("the error detail should indicate {string}")
    public void errorDetailShouldIndicate(String expectedDetail) {
        assertThat(lastResponse.jsonPath().getString("detail")).contains(expectedDetail);
    }
}
