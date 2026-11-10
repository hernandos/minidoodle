package de.com.saenz.minidoodle.infrastructure.api;

import de.com.saenz.minidoodle.domain.exception.ResourceNotFoundException;
import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.usecase.CancelMeetingUseCase;
import de.com.saenz.minidoodle.domain.usecase.GetMeetingQuery;
import de.com.saenz.minidoodle.domain.usecase.ScheduleMeetingCommand;
import de.com.saenz.minidoodle.domain.usecase.ScheduleMeetingUseCase;
import de.com.saenz.minidoodle.infrastructure.api.mapper.ApiDtoMapper;
import de.com.saenz.minidoodle.infrastructure.api.model.MeetingResponse;
import de.com.saenz.minidoodle.infrastructure.api.model.ScheduleMeetingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

import java.util.List;

/**
 * REST controller managing scheduled meetings.
 */
@Validated
@RestController
@RequestMapping(value = "/api/v1/meetings", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Meetings", description = "Endpoints for scheduling, querying, and cancelling meetings")
public class MeetingController {

    private final ScheduleMeetingUseCase scheduleMeetingUseCase;
    private final CancelMeetingUseCase cancelMeetingUseCase;
    private final GetMeetingQuery getMeetingQuery;
    private final ApiDtoMapper apiDtoMapper;

    @Operation(summary = "Schedule a new meeting",
               description = "Books a meeting by consuming pre-existing matching FREE slots of the organizer and all participants, transitioning them to BUSY.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Meeting scheduled successfully",
                         content = @Content(schema = @Schema(implementation = MeetingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure or invalid timeframe",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Organizer or participant not found",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                         description = "Required slots are missing or already busy (or concurrent conflict)",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse scheduleMeeting(@Valid @RequestBody ScheduleMeetingRequest request) {
        ScheduleMeetingCommand command = apiDtoMapper.toScheduleMeetingCommand(request);
        Meeting scheduled = scheduleMeetingUseCase.scheduleMeeting(command);
        return apiDtoMapper.toMeetingResponse(scheduled);
    }

    @Operation(summary = "Get a meeting by ID",
               description = "Retrieves details of a scheduled meeting including organizer, participants, and consumed slots.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Meeting found",
                         content = @Content(schema = @Schema(implementation = MeetingResponse.class))),
            @ApiResponse(responseCode = "404", description = "Meeting not found",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public MeetingResponse getMeetingById(
            @Parameter(description = "ID of the meeting", example = "100")
            @PathVariable Long id) {
        return getMeetingQuery.getMeetingById(id)
                              .map(apiDtoMapper::toMeetingResponse)
                              .orElseThrow(() -> new ResourceNotFoundException("Meeting not found with id: " + id));
    }

    @Operation(summary = "Get meetings organized by a user",
               description = "Retrieves all meetings organized by the given user ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Meetings retrieved successfully",
                         content = @Content(array = @ArraySchema(schema = @Schema(implementation = MeetingResponse.class))))
    })
    @GetMapping(params = "organizerId")
    @ResponseStatus(HttpStatus.OK)
    public List<MeetingResponse> getMeetingsByOrganizer(
            @Parameter(description = "User ID of the organizer", example = "1")
            @RequestParam Long organizerId) {
        List<Meeting> meetings = getMeetingQuery.getMeetingsByOrganizer(organizerId);
        return apiDtoMapper.toMeetingResponseList(meetings);
    }

    @Operation(summary = "Get meetings where a user is a participant",
               description = "Retrieves all meetings where the user participates (excluding organized meetings).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Meetings retrieved successfully",
                         content = @Content(array = @ArraySchema(schema = @Schema(implementation = MeetingResponse.class))))
    })
    @GetMapping(params = "participantId")
    @ResponseStatus(HttpStatus.OK)
    public List<MeetingResponse> getMeetingsByParticipant(
            @Parameter(description = "User ID of the participant", example = "2")
            @RequestParam Long participantId) {
        List<Meeting> meetings = getMeetingQuery.getMeetingsByParticipant(participantId);
        return apiDtoMapper.toMeetingResponseList(meetings);
    }

    @Operation(summary = "Cancel a scheduled meeting",
               description = "Cancels a meeting and releases all its consumed slots back to FREE status.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Meeting cancelled and slots released"),
            @ApiResponse(responseCode = "400", description = "Invalid meeting ID or meeting not found",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Meeting not found",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMeeting(
            @Parameter(description = "ID of the meeting to cancel", example = "100")
            @PathVariable Long id) {
        cancelMeetingUseCase.cancelMeeting(id);
    }
}
