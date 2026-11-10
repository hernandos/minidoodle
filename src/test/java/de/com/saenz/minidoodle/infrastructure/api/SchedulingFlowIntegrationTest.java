package de.com.saenz.minidoodle.infrastructure.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.com.saenz.minidoodle.PostgresIntegrationTest;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import de.com.saenz.minidoodle.infrastructure.api.model.CreateTimeSlotRequest;
import de.com.saenz.minidoodle.infrastructure.api.model.ScheduleMeetingRequest;
import de.com.saenz.minidoodle.infrastructure.api.model.UpdateTimeSlotRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class SchedulingFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    private User createUniqueUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                                       .username(prefix + "_" + uid)
                                       .name("Api User " + uid)
                                       .email(prefix + "_" + uid + "@example.com")
                                       .build());
    }

    @Test
    @DisplayName(
            "Complete end-to-end REST scheduling journey: slots -> personal calendar -> aggregate view -> meeting -> cancellation")
    void completeRestSchedulingJourney() throws Exception {
        User organizer = createUniqueUser("api_org");
        User participant = createUniqueUser("api_part");

        Instant start = Instant.parse("2026-11-25T14:00:00Z");
        Instant end = start.plus(Duration.ofHours(1));

        // 1. Organizer creates a free slot via POST /api/v1/time-slots
        CreateTimeSlotRequest orgSlotReq = new CreateTimeSlotRequest(organizer.getId(), start, end);
        String orgSlotJson = mockMvc.perform(post("/api/v1/time-slots")
                                                     .contentType(MediaType.APPLICATION_JSON)
                                                     .content(objectMapper.writeValueAsString(orgSlotReq)))
                                    .andExpect(status().isCreated())
                                    .andExpect(jsonPath("$.id").exists())
                                    .andExpect(jsonPath("$.status").value("FREE"))
                                    .andReturn().getResponse().getContentAsString();
        Long orgSlotId = objectMapper.readTree(orgSlotJson).get("id").asLong();

        // 2. Participant creates a free slot via POST /api/v1/time-slots
        CreateTimeSlotRequest partSlotReq = new CreateTimeSlotRequest(participant.getId(), start, end);
        String partSlotJson = mockMvc.perform(post("/api/v1/time-slots")
                                                      .contentType(MediaType.APPLICATION_JSON)
                                                      .content(objectMapper.writeValueAsString(partSlotReq)))
                                     .andExpect(status().isCreated())
                                     .andExpect(jsonPath("$.status").value("FREE"))
                                     .andReturn().getResponse().getContentAsString();
        Long partSlotId = objectMapper.readTree(partSlotJson).get("id").asLong();

        // 3. Query Personal Calendar view: GET /api/v1/availability/users/{userId}
        mockMvc.perform(get("/api/v1/availability/users/" + organizer.getId()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.user.id").value(organizer.getId()))
               .andExpect(jsonPath("$.timeSlots.length()").value(1))
               .andExpect(jsonPath("$.timeSlots[0].id").value(orgSlotId))
               .andExpect(jsonPath("$.timeSlots[0].status").value("FREE"));

        // 4. Query Aggregated availability view: GET /api/v1/availability/aggregate?freeOnly=true
        mockMvc.perform(get("/api/v1/availability/aggregate")
                                .param("startTime", start.toString())
                                .param("endTime", end.toString())
                                .param("freeOnly", "true"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2));

        // 5. Schedule Meeting: POST /api/v1/meetings
        ScheduleMeetingRequest meetingReq = new ScheduleMeetingRequest(
                "Sprint Demo", "Show progress to stakeholders",
                organizer.getId(), List.of(participant.getId()), start, end
        );
        String meetingJson = mockMvc.perform(post("/api/v1/meetings")
                                                     .contentType(MediaType.APPLICATION_JSON)
                                                     .content(objectMapper.writeValueAsString(meetingReq)))
                                    .andExpect(status().isCreated())
                                    .andExpect(jsonPath("$.id").exists())
                                    .andExpect(jsonPath("$.title").value("Sprint Demo"))
                                    .andExpect(jsonPath("$.timeSlots.length()").value(2))
                                    .andReturn().getResponse().getContentAsString();
        Long meetingId = objectMapper.readTree(meetingJson).get("id").asLong();

        // 6. Verify slots transitioned to BUSY in database
        assertThat(timeSlotRepository.findById(orgSlotId)).get()
                                                          .extracting(TimeSlot::getStatus)
                                                          .isEqualTo(SlotStatus.BUSY);
        assertThat(timeSlotRepository.findById(partSlotId)).get()
                                                           .extracting(TimeSlot::getStatus)
                                                           .isEqualTo(SlotStatus.BUSY);

        // 7. Aggregated view for freeOnly now returns 0 slots
        mockMvc.perform(get("/api/v1/availability/aggregate")
                                .param("startTime", start.toString())
                                .param("endTime", end.toString())
                                .param("userIds", organizer.getId() + "," + participant.getId())
                                .param("freeOnly", "true"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0));

        // 8. Attempting to modify a booked slot returns 409 Conflict
        UpdateTimeSlotRequest updateReq = new UpdateTimeSlotRequest(start.plus(Duration.ofHours(2)),
                                                                    end.plus(Duration.ofHours(2)));
        mockMvc.perform(put("/api/v1/time-slots/" + orgSlotId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.title").value("Business State Conflict"));

        // 9. Cancel Meeting: DELETE /api/v1/meetings/{id}
        mockMvc.perform(delete("/api/v1/meetings/" + meetingId))
               .andExpect(status().isNoContent());

        // 10. Verify meeting is gone and slots are back to FREE
        mockMvc.perform(get("/api/v1/meetings/" + meetingId))
               .andExpect(status().isNotFound());

        assertThat(timeSlotRepository.findById(orgSlotId)).get()
                                                          .extracting(TimeSlot::getStatus)
                                                          .isEqualTo(SlotStatus.FREE);
        assertThat(timeSlotRepository.findById(partSlotId)).get()
                                                           .extracting(TimeSlot::getStatus)
                                                           .isEqualTo(SlotStatus.FREE);
    }
}
