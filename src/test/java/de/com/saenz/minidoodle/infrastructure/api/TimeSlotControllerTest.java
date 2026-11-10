package de.com.saenz.minidoodle.infrastructure.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.usecase.CreateTimeSlotUseCase;
import de.com.saenz.minidoodle.domain.usecase.DeleteTimeSlotUseCase;
import de.com.saenz.minidoodle.domain.usecase.UpdateTimeSlotUseCase;
import de.com.saenz.minidoodle.infrastructure.api.mapper.ApiDtoMapper;
import de.com.saenz.minidoodle.infrastructure.api.model.CreateTimeSlotRequest;
import de.com.saenz.minidoodle.infrastructure.api.model.TimeSlotResponse;
import de.com.saenz.minidoodle.infrastructure.api.model.UpdateTimeSlotRequest;
import de.com.saenz.minidoodle.infrastructure.api.model.UserSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimeSlotController.class)
class TimeSlotControllerTest {

    private final Instant start = Instant.parse("2026-11-15T09:00:00Z");
    private final Instant end = start.plus(Duration.ofHours(1));
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private CreateTimeSlotUseCase createTimeSlotUseCase;
    @MockitoBean
    private UpdateTimeSlotUseCase updateTimeSlotUseCase;
    @MockitoBean
    private DeleteTimeSlotUseCase deleteTimeSlotUseCase;
    @MockitoBean
    private ApiDtoMapper apiDtoMapper;

    @Test
    @DisplayName("POST /api/v1/time-slots should create slot and return 201 Created")
    void createTimeSlot_shouldReturnCreated_whenValid() throws Exception {
        CreateTimeSlotRequest request = new CreateTimeSlotRequest(1L, start, end);
        User user = User.builder().id(1L).username("alice").build();
        TimeSlot domainSlot = TimeSlot.builder()
                                      .id(10L)
                                      .user(user)
                                      .startTime(start)
                                      .endTime(end)
                                      .status(SlotStatus.FREE)
                                      .build();
        TimeSlotResponse responseDto = new TimeSlotResponse(10L,
                                                            new UserSummaryResponse(1L, "alice"),
                                                            start,
                                                            end,
                                                            SlotStatus.FREE);

        when(createTimeSlotUseCase.createTimeSlot(1L, start, end)).thenReturn(domainSlot);
        when(apiDtoMapper.toTimeSlotResponse(domainSlot)).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/time-slots")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(10))
               .andExpect(jsonPath("$.status").value("FREE"))
               .andExpect(jsonPath("$.user.id").value(1));

        verify(createTimeSlotUseCase).createTimeSlot(1L, start, end);
    }

    @Test
    @DisplayName("POST /api/v1/time-slots should return 400 Bad Request when validation fails")
    void createTimeSlot_shouldReturn400_whenFieldsAreNull() throws Exception {
        CreateTimeSlotRequest invalidRequest = new CreateTimeSlotRequest(null, null, null);

        mockMvc.perform(post("/api/v1/time-slots")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"))
               .andExpect(jsonPath("$.status").value(400))
               .andExpect(jsonPath("$.timestamp").exists());

        verify(createTimeSlotUseCase, never()).createTimeSlot(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/time-slots should return 400 Bad Request when timeframe is invalid (DTO validation)")
    void createTimeSlot_shouldReturn400_whenTimeframeInvalid() throws Exception {
        CreateTimeSlotRequest request = new CreateTimeSlotRequest(1L, end, start);

        mockMvc.perform(post("/api/v1/time-slots")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"))
               .andExpect(jsonPath("$.status").value(400))
               .andExpect(jsonPath("$.timestamp").exists());

        verify(createTimeSlotUseCase, never()).createTimeSlot(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /api/v1/time-slots/{id} should update slot and return 200 OK")
    void updateTimeSlot_shouldReturnOk_whenValid() throws Exception {
        UpdateTimeSlotRequest request = new UpdateTimeSlotRequest(start, end);
        TimeSlot domainSlot = TimeSlot.builder().id(10L).startTime(start).endTime(end).status(SlotStatus.FREE).build();
        TimeSlotResponse responseDto = new TimeSlotResponse(10L, null, start, end, SlotStatus.FREE);

        when(updateTimeSlotUseCase.updateTimeSlot(eq(10L), eq(start), eq(end))).thenReturn(domainSlot);
        when(apiDtoMapper.toTimeSlotResponse(domainSlot)).thenReturn(responseDto);

        mockMvc.perform(put("/api/v1/time-slots/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(10))
               .andExpect(jsonPath("$.status").value("FREE"));

        verify(updateTimeSlotUseCase).updateTimeSlot(10L, start, end);
    }

    @Test
    @DisplayName("PUT /api/v1/time-slots/{id} should return 409 Conflict when slot is already busy")
    void updateTimeSlot_shouldReturn409_whenSlotIsBusy() throws Exception {
        UpdateTimeSlotRequest request = new UpdateTimeSlotRequest(start, end);

        when(updateTimeSlotUseCase.updateTimeSlot(eq(10L), eq(start), eq(end)))
                .thenThrow(new IllegalStateException("Cannot modify a booked or busy time slot: 10"));

        mockMvc.perform(put("/api/v1/time-slots/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.title").value("Business State Conflict"))
               .andExpect(jsonPath("$.status").value(409))
               .andExpect(jsonPath("$.detail").value("Cannot modify a booked or busy time slot: 10"))
               .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/time-slots/{id} should return 204 No Content")
    void deleteTimeSlot_shouldReturn204_whenDeleted() throws Exception {
        doNothing().when(deleteTimeSlotUseCase).deleteTimeSlot(10L);

        mockMvc.perform(delete("/api/v1/time-slots/10"))
               .andExpect(status().isNoContent());

        verify(deleteTimeSlotUseCase).deleteTimeSlot(10L);
    }
}
