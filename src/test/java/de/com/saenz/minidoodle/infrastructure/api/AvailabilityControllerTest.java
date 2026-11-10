package de.com.saenz.minidoodle.infrastructure.api;

import de.com.saenz.minidoodle.domain.model.Calendar;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.usecase.GetAggregatedAvailabilityQuery;
import de.com.saenz.minidoodle.domain.usecase.GetUserCalendarQuery;
import de.com.saenz.minidoodle.infrastructure.api.mapper.ApiDtoMapper;
import de.com.saenz.minidoodle.infrastructure.api.model.TimeSlotResponse;
import de.com.saenz.minidoodle.infrastructure.api.model.UserAvailabilityResponse;
import de.com.saenz.minidoodle.infrastructure.api.model.UserResponse;
import de.com.saenz.minidoodle.infrastructure.api.model.UserSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AvailabilityController.class)
class AvailabilityControllerTest {

    private final Instant start = Instant.parse("2026-11-15T08:00:00Z");
    private final Instant end = start.plus(Duration.ofHours(4));
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private GetUserCalendarQuery getUserCalendarQuery;
    @MockitoBean
    private GetAggregatedAvailabilityQuery getAggregatedAvailabilityQuery;
    @MockitoBean
    private ApiDtoMapper apiDtoMapper;

    @Test
    @DisplayName("GET /api/v1/availability/users/{userId} should return user availability when found")
    void getUserCalendar_shouldReturnCalendar_whenFound() throws Exception {
        User user = User.builder().id(1L).username("alice").build();
        Calendar domainCalendar = Calendar.of(user);
        UserAvailabilityResponse responseDto = new UserAvailabilityResponse(new UserResponse(1L, "alice", null, null),
                                                                            List.of());

        when(getUserCalendarQuery.getUserCalendar(1L)).thenReturn(Optional.of(domainCalendar));
        when(apiDtoMapper.toUserAvailabilityResponse(domainCalendar)).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/availability/users/1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.user.id").value(1))
               .andExpect(jsonPath("$.user.username").value("alice"));

        verify(getUserCalendarQuery).getUserCalendar(1L);
    }

    @Test
    @DisplayName("GET /api/v1/availability/users/{userId} should return 404 when availability not found")
    void getUserCalendar_shouldReturn404_whenNotFound() throws Exception {
        when(getUserCalendarQuery.getUserCalendar(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/availability/users/99"))
               .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/availability/users/{userId} with timestamps should filter by range")
    void getUserCalendar_withTimestamps_shouldFilterByRange() throws Exception {
        User user = User.builder().id(1L).username("alice").build();
        Calendar domainCalendar = Calendar.of(user);
        UserAvailabilityResponse responseDto = new UserAvailabilityResponse(new UserResponse(1L, "alice", null, null),
                                                                            List.of());

        when(getUserCalendarQuery.getUserCalendar(1L, start, end)).thenReturn(Optional.of(domainCalendar));
        when(apiDtoMapper.toUserAvailabilityResponse(domainCalendar)).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/availability/users/1")
                                .param("startTime", start.toString())
                                .param("endTime", end.toString()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.user.id").value(1));

        verify(getUserCalendarQuery).getUserCalendar(1L, start, end);
    }

    @Test
    @DisplayName("GET /api/v1/availability/aggregate should return aggregated slots across all users")
    void getAggregatedAvailability_shouldReturnSlots_whenValid() throws Exception {
        User user = User.builder().id(1L).username("alice").build();
        TimeSlot slot = TimeSlot.builder()
                                .id(10L)
                                .user(user)
                                .startTime(start)
                                .endTime(end)
                                .status(SlotStatus.FREE)
                                .build();
        TimeSlotResponse slotResponse = new TimeSlotResponse(10L,
                                                             new UserSummaryResponse(1L, "alice"),
                                                             start,
                                                             end,
                                                             SlotStatus.FREE);

        when(getAggregatedAvailabilityQuery.getAggregatedAvailability(start, end)).thenReturn(List.of(slot));
        when(apiDtoMapper.toTimeSlotResponseList(List.of(slot))).thenReturn(List.of(slotResponse));

        mockMvc.perform(get("/api/v1/availability/aggregate")
                                .param("startTime", start.toString())
                                .param("endTime", end.toString()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(10))
               .andExpect(jsonPath("$[0].status").value("FREE"));

        verify(getAggregatedAvailabilityQuery).getAggregatedAvailability(start, end);
    }

    @Test
    @DisplayName("GET /api/v1/availability/aggregate with freeOnly=true should query free slots only")
    void getAggregatedAvailability_withFreeOnly_shouldReturnFreeSlots() throws Exception {
        when(getAggregatedAvailabilityQuery.getAggregatedFreeAvailability(start, end)).thenReturn(List.of());
        when(apiDtoMapper.toTimeSlotResponseList(List.of())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/availability/aggregate")
                                .param("startTime", start.toString())
                                .param("endTime", end.toString())
                                .param("freeOnly", "true"))
               .andExpect(status().isOk())
               .andExpect(content().json("[]"));

        verify(getAggregatedAvailabilityQuery).getAggregatedFreeAvailability(start, end);
    }

    @Test
    @DisplayName("GET /api/v1/availability/aggregate with userIds should filter by specified users")
    void getAggregatedAvailability_withUserIds_shouldFilter() throws Exception {
        List<Long> userIds = List.of(1L, 2L);
        when(getAggregatedAvailabilityQuery.getAggregatedAvailability(userIds, start, end)).thenReturn(List.of());
        when(apiDtoMapper.toTimeSlotResponseList(List.of())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/availability/aggregate")
                                .param("startTime", start.toString())
                                .param("endTime", end.toString())
                                .param("userIds", "1,2"))
               .andExpect(status().isOk())
               .andExpect(content().json("[]"));

        verify(getAggregatedAvailabilityQuery).getAggregatedAvailability(userIds, start, end);
    }

    @Test
    @DisplayName("GET /api/v1/availability/aggregate should return 400 Bad Request when startTime is missing")
    void getAggregatedAvailability_shouldReturn400_whenStartTimeMissing() throws Exception {
        mockMvc.perform(get("/api/v1/availability/aggregate")
                                .param("endTime", end.toString()))
               .andExpect(status().isBadRequest());
    }
}
