package de.com.saenz.minidoodle.infrastructure.api;

import de.com.saenz.minidoodle.domain.exception.ResourceNotFoundException;
import de.com.saenz.minidoodle.domain.model.Calendar;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.usecase.GetAggregatedAvailabilityQuery;
import de.com.saenz.minidoodle.domain.usecase.GetUserCalendarQuery;
import de.com.saenz.minidoodle.infrastructure.api.mapper.ApiDtoMapper;
import de.com.saenz.minidoodle.infrastructure.api.model.TimeSlotResponse;
import de.com.saenz.minidoodle.infrastructure.api.model.UserAvailabilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * REST controller providing personal availability views and multi-user aggregated availability views.
 */
@Validated
@RestController
@RequestMapping(value = "/api/v1/availability", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Availability",
     description = "Endpoints for personal availability views and multi-user aggregated availability")
public class AvailabilityController {

    private final GetUserCalendarQuery getUserCalendarQuery;
    private final GetAggregatedAvailabilityQuery getAggregatedAvailabilityQuery;
    private final ApiDtoMapper apiDtoMapper;

    @Operation(
            summary = "Get personal availability view for a user",
            description = "Retrieves the user's personal availability consisting of their chronological time slots. Can be optionally filtered by start and end timestamps."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User availability retrieved successfully",
                         content = @Content(schema = @Schema(implementation = UserAvailabilityResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid timeframe or user ID",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "User availability not found",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public UserAvailabilityResponse getUserAvailability(
            @Parameter(description = "ID of the user", example = "1")
            @PathVariable Long userId,
            @Parameter(description = "Optional filter start timestamp (ISO-8601 UTC)", example = "2026-11-15T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @Parameter(description = "Optional filter end timestamp (ISO-8601 UTC)", example = "2026-11-15T23:59:59Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

        Optional<Calendar> calendarOpt;
        if (startTime != null && endTime != null) {
            calendarOpt = getUserCalendarQuery.getUserCalendar(userId, startTime, endTime);
        } else {
            calendarOpt = getUserCalendarQuery.getUserCalendar(userId);
        }

        return calendarOpt.map(apiDtoMapper::toUserAvailabilityResponse)
                          .orElseThrow(() -> new ResourceNotFoundException("User availability not found for user id: " + userId));
    }

    @Operation(
            summary = "Get aggregated availability view across users",
            description = "Queries availability across all users (or a specific subset of user IDs) within a required timeframe. Allows filtering by free-only slots via the 'freeOnly' flag. The timeframe delimits the result set."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aggregated slots retrieved successfully",
                         content = @Content(schema = @Schema(implementation = TimeSlotResponse.class))),
            @ApiResponse(responseCode = "400",
                         description = "Missing or invalid timeframe (startTime must be before endTime)",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/aggregate")
    @ResponseStatus(HttpStatus.OK)
    public List<TimeSlotResponse> getAggregatedAvailability(
            @Parameter(description = "Start timestamp of the timeframe (ISO-8601 UTC)",
                       required = true,
                       example = "2026-11-15T08:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @Parameter(description = "End timestamp of the timeframe (ISO-8601 UTC)",
                       required = true,
                       example = "2026-11-15T18:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @Parameter(description = "Optional list of user IDs to filter by. If omitted, queries across all users.",
                       example = "[1, 2, 3]")
            @RequestParam(required = false) List<Long> userIds,
            @Parameter(description = "If true, returns only FREE available slots. Defaults to false.", example = "true")
            @RequestParam(defaultValue = "false") boolean freeOnly) {

        List<TimeSlot> slots;
        boolean hasUserFilter = userIds != null && !userIds.isEmpty();
        if (freeOnly) {
            slots = hasUserFilter
                    ? getAggregatedAvailabilityQuery.getAggregatedFreeAvailability(userIds, startTime, endTime)
                    : getAggregatedAvailabilityQuery.getAggregatedFreeAvailability(startTime, endTime);
        } else {
            slots = hasUserFilter
                    ? getAggregatedAvailabilityQuery.getAggregatedAvailability(userIds, startTime, endTime)
                    : getAggregatedAvailabilityQuery.getAggregatedAvailability(startTime, endTime);
        }

        return apiDtoMapper.toTimeSlotResponseList(slots);
    }
}
