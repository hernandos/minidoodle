package de.com.saenz.minidoodle.infrastructure.api;

import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.usecase.CreateTimeSlotUseCase;
import de.com.saenz.minidoodle.domain.usecase.DeleteTimeSlotUseCase;
import de.com.saenz.minidoodle.domain.usecase.UpdateTimeSlotUseCase;
import de.com.saenz.minidoodle.infrastructure.api.mapper.ApiDtoMapper;
import de.com.saenz.minidoodle.infrastructure.api.model.CreateTimeSlotRequest;
import de.com.saenz.minidoodle.infrastructure.api.model.TimeSlotResponse;
import de.com.saenz.minidoodle.infrastructure.api.model.UpdateTimeSlotRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller managing individual TimeSlot proactive availability resources.
 */
@Validated
@RestController
@RequestMapping(value = "/api/v1/time-slots", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Time Slots", description = "Endpoints for proactively managing personal free availability slots")
public class TimeSlotController {

    private final CreateTimeSlotUseCase createTimeSlotUseCase;
    private final UpdateTimeSlotUseCase updateTimeSlotUseCase;
    private final DeleteTimeSlotUseCase deleteTimeSlotUseCase;
    private final ApiDtoMapper apiDtoMapper;

    @Operation(summary = "Create a new time slot",
               description = "Proactively defines a free time slot for a user. Start time must be before end time and must not overlap with existing slots.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Time slot created successfully",
                         content = @Content(schema = @Schema(implementation = TimeSlotResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure or invalid timeframe",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Slot overlaps with existing user slot",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TimeSlotResponse createTimeSlot(@Valid @RequestBody CreateTimeSlotRequest request) {
        TimeSlot created = createTimeSlotUseCase.createTimeSlot(request.userId(),
                                                                request.startTime(),
                                                                request.endTime());
        return apiDtoMapper.toTimeSlotResponse(created);
    }

    @Operation(summary = "Update an existing time slot",
               description = "Modifies start and end times of a free time slot. Booked or busy slots cannot be modified.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Time slot updated successfully",
                         content = @Content(schema = @Schema(implementation = TimeSlotResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure or invalid timeframe",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Time slot not found",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Slot is already booked/busy or new timeframe overlaps",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public TimeSlotResponse updateTimeSlot(
            @Parameter(description = "ID of the time slot to update", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateTimeSlotRequest request) {
        TimeSlot updated = updateTimeSlotUseCase.updateTimeSlot(id, request.startTime(), request.endTime());
        return apiDtoMapper.toTimeSlotResponse(updated);
    }

    @Operation(summary = "Delete an existing time slot",
               description = "Deletes an available time slot. Slots that are booked in a meeting cannot be deleted.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Time slot deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid time slot ID",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Time slot not found",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Slot is already booked/busy",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTimeSlot(
            @Parameter(description = "ID of the time slot to delete", example = "1")
            @PathVariable Long id) {
        deleteTimeSlotUseCase.deleteTimeSlot(id);
    }
}
