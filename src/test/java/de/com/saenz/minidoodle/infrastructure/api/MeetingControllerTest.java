package de.com.saenz.minidoodle.infrastructure.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.usecase.CancelMeetingUseCase;
import de.com.saenz.minidoodle.domain.usecase.GetMeetingQuery;
import de.com.saenz.minidoodle.domain.usecase.ScheduleMeetingCommand;
import de.com.saenz.minidoodle.domain.usecase.ScheduleMeetingUseCase;
import de.com.saenz.minidoodle.infrastructure.api.mapper.ApiDtoMapper;
import de.com.saenz.minidoodle.infrastructure.api.model.MeetingResponse;
import de.com.saenz.minidoodle.infrastructure.api.model.ScheduleMeetingRequest;
import de.com.saenz.minidoodle.infrastructure.api.model.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeetingController.class)
class MeetingControllerTest {

    private final Instant start = Instant.parse("2026-11-15T14:00:00Z");
    private final Instant end = start.plus(Duration.ofHours(1));
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private ScheduleMeetingUseCase scheduleMeetingUseCase;
    @MockitoBean
    private CancelMeetingUseCase cancelMeetingUseCase;
    @MockitoBean
    private GetMeetingQuery getMeetingQuery;
    @MockitoBean
    private ApiDtoMapper apiDtoMapper;

    @Test
    @DisplayName("POST /api/v1/meetings should schedule meeting and return 201 Created")
    void scheduleMeeting_shouldReturnCreated_whenValid() throws Exception {
        ScheduleMeetingRequest request = new ScheduleMeetingRequest(
                "Design Sync", "Discuss architecture", 1L, List.of(2L), start, end
        );
        ScheduleMeetingCommand command = new ScheduleMeetingCommand(
                "Design Sync", "Discuss architecture", 1L, List.of(2L), start, end
        );

        User org = User.builder().id(1L).username("org").build();
        Meeting domainMeeting = Meeting.of("Design Sync", "Discuss architecture", org);
        MeetingResponse responseDto = new MeetingResponse(
                100L, "Design Sync", "Discuss architecture",
                new UserResponse(1L, "org", null, null), start, end,
                List.of(new UserResponse(2L, "part", null, null)), List.of()
        );

        when(apiDtoMapper.toScheduleMeetingCommand(any())).thenReturn(command);
        when(scheduleMeetingUseCase.scheduleMeeting(command)).thenReturn(domainMeeting);
        when(apiDtoMapper.toMeetingResponse(domainMeeting)).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/meetings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(100))
               .andExpect(jsonPath("$.title").value("Design Sync"))
               .andExpect(jsonPath("$.organizer.id").value(1))
               .andExpect(jsonPath("$.participants[0].id").value(2));

        verify(scheduleMeetingUseCase).scheduleMeeting(command);
    }

    @Test
    @DisplayName("POST /api/v1/meetings should return 400 Bad Request when validation fails")
    void scheduleMeeting_shouldReturn400_whenTitleIsBlank() throws Exception {
        ScheduleMeetingRequest request = new ScheduleMeetingRequest(
                "", null, 1L, List.of(2L), start, end
        );

        mockMvc.perform(post("/api/v1/meetings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"))
               .andExpect(jsonPath("$.status").value(400));

        verify(scheduleMeetingUseCase, never()).scheduleMeeting(any());
    }

    @Test
    @DisplayName("POST /api/v1/meetings should return 409 Conflict when participant is unavailable")
    void scheduleMeeting_shouldReturn409_whenParticipantUnavailable() throws Exception {
        ScheduleMeetingRequest request = new ScheduleMeetingRequest(
                "Sync", null, 1L, List.of(2L), start, end
        );
        ScheduleMeetingCommand command = new ScheduleMeetingCommand(
                "Sync", null, 1L, List.of(2L), start, end
        );

        when(apiDtoMapper.toScheduleMeetingCommand(any())).thenReturn(command);
        when(scheduleMeetingUseCase.scheduleMeeting(command))
                .thenThrow(new IllegalStateException("User 2 is not available: no matching slot found"));

        mockMvc.perform(post("/api/v1/meetings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.title").value("Business State Conflict"))
               .andExpect(jsonPath("$.status").value(409))
               .andExpect(jsonPath("$.detail").value("User 2 is not available: no matching slot found"));
    }

    @Test
    @DisplayName("GET /api/v1/meetings/{id} should return 200 OK when found")
    void getMeetingById_shouldReturnOk_whenMeetingExists() throws Exception {
        User org = User.builder().id(1L).username("org").build();
        Meeting meeting = Meeting.of("Retrospective", null, org);
        MeetingResponse responseDto = new MeetingResponse(
                50L, "Retrospective", null, new UserResponse(1L, "org", null, null),
                start, end, List.of(), List.of()
        );

        when(getMeetingQuery.getMeetingById(50L)).thenReturn(Optional.of(meeting));
        when(apiDtoMapper.toMeetingResponse(meeting)).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/meetings/50"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(50))
               .andExpect(jsonPath("$.title").value("Retrospective"));
    }

    @Test
    @DisplayName("GET /api/v1/meetings/{id} should return 404 Not Found when meeting does not exist")
    void getMeetingById_shouldReturn404_whenMeetingNotFound() throws Exception {
        when(getMeetingQuery.getMeetingById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/meetings/999"))
               .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/meetings?organizerId=1 should return meetings organized by user")
    void getMeetings_byOrganizer_shouldReturnMeetings() throws Exception {
        User org = User.builder().id(1L).username("org").build();
        Meeting meeting = Meeting.of("Sync", null, org);
        MeetingResponse responseDto = new MeetingResponse(
                50L, "Sync", null, new UserResponse(1L, "org", null, null),
                start, end, List.of(), List.of()
        );

        when(getMeetingQuery.getMeetingsByOrganizer(1L)).thenReturn(List.of(meeting));
        when(apiDtoMapper.toMeetingResponseList(List.of(meeting))).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/api/v1/meetings").param("organizerId", "1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(50));

        verify(getMeetingQuery).getMeetingsByOrganizer(1L);
    }

    @Test
    @DisplayName("GET /api/v1/meetings without filters should return all meetings")
    void getMeetings_withoutFilters_shouldReturnAllMeetings() throws Exception {
        when(getMeetingQuery.getAllMeetings()).thenReturn(List.of());
        when(apiDtoMapper.toMeetingResponseList(List.of())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/meetings"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isEmpty());

        verify(getMeetingQuery).getAllMeetings();
    }

    @Test
    @DisplayName(
            "GET /api/v1/meetings with both organizerId and participantId should return meetings organized by user where participant attends")
    void getMeetings_withBothFilters_shouldReturnCombinedResults() throws Exception {
        User org = User.builder().id(1L).username("org").build();
        Meeting meeting = Meeting.of("Sync", null, org);
        MeetingResponse responseDto = new MeetingResponse(
                50L, "Sync", null, new UserResponse(1L, "org", null, null),
                start, end, List.of(), List.of()
        );

        when(getMeetingQuery.getMeetingsByOrganizerAndParticipant(1L, 2L)).thenReturn(List.of(meeting));
        when(apiDtoMapper.toMeetingResponseList(List.of(meeting))).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/api/v1/meetings")
                                .param("organizerId", "1")
                                .param("participantId", "2"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(50));

        verify(getMeetingQuery).getMeetingsByOrganizerAndParticipant(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/v1/meetings/{id} should cancel meeting and return 204 No Content")
    void cancelMeeting_shouldReturn204_whenSuccessful() throws Exception {
        doNothing().when(cancelMeetingUseCase).cancelMeeting(50L);

        mockMvc.perform(delete("/api/v1/meetings/50"))
               .andExpect(status().isNoContent());

        verify(cancelMeetingUseCase).cancelMeeting(50L);
    }
}
