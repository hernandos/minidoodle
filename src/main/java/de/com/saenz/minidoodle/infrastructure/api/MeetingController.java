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

    @Operation(summary = "List meetings",
               description = "Retrieves meetings filtered by organizer or participant. At least one filter ('organizerId' or 'participantId') is required. When both are provided, returns meetings organized by organizerId where participantId is also a participant.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Meetings retrieved successfully",
                         content = @Content(schema = @Schema(implementation = MeetingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed - missing filter parameter",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<MeetingResponse> getMeetings(
            @Parameter(description = "Filter by organizer user ID", example = "1")
            @RequestParam(required = false) Long organizerId,
            @Parameter(description = "Filter by participant user ID", example = "2")
            @RequestParam(required = false) Long participantId) {

        if (organizerId == null && participantId == null) {
            throw new IllegalArgumentException("At least one filter parameter ('organizerId' or 'participantId') is required");
        }

        List<Meeting> meetings;
        if (organizerId != null && participantId != null) {
            meetings = getMeetingQuery.getMeetingsByOrganizerAndParticipant(organizerId, participantId);
        } else if (organizerId != null) {
            meetings = getMeetingQuery.getMeetingsByOrganizer(organizerId);
        } else {
            meetings = getMeetingQuery.getMeetingsByParticipant(participantId);
        }

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
