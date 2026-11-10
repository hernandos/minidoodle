package de.com.saenz.minidoodle.infrastructure.api.mapper;

import de.com.saenz.minidoodle.domain.model.Calendar;
import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.usecase.ScheduleMeetingCommand;
import de.com.saenz.minidoodle.infrastructure.api.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;

/**
 * MapStruct mapper converting between domain models and API DTOs.
 */
@Mapper(componentModel = "spring")
public interface ApiDtoMapper {

    UserResponse toUserResponse(User user);

    List<UserResponse> toUserResponseList(Collection<User> users);

    TimeSlotResponse toTimeSlotResponse(TimeSlot timeSlot);

    List<TimeSlotResponse> toTimeSlotResponseList(Collection<TimeSlot> timeSlots);

    @Mapping(target = "startTime", expression = "java(meeting.getStartTime())")
    @Mapping(target = "endTime", expression = "java(meeting.getEndTime())")
    @Mapping(target = "participants", expression = "java(toUserResponseList(meeting.getParticipants()))")
    @Mapping(target = "timeSlots", expression = "java(toTimeSlotResponseList(meeting.getTimeSlots()))")
    MeetingResponse toMeetingResponse(Meeting meeting);

    List<MeetingResponse> toMeetingResponseList(Collection<Meeting> meetings);

    @Mapping(target = "timeSlots", expression = "java(toTimeSlotResponseList(calendar.getTimeSlots()))")
    UserAvailabilityResponse toUserAvailabilityResponse(Calendar calendar);

    ScheduleMeetingCommand toScheduleMeetingCommand(ScheduleMeetingRequest request);
}
