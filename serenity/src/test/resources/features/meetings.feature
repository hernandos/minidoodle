@meetings
Feature: Meeting Lifecycle Management
  As an organizer
  I want to schedule meetings with participants and cancel them when needed
  So that all participants have synchronized calendar commitments and released slots

  Background:
    Given the MiniDoodle API is running and ready
    And registered users "organizer_user" and "participant_user" exist in the system

  # -------------------------------------------------------------------------
  # POSITIVE CASES
  # -------------------------------------------------------------------------
  Scenario: Successfully schedule a meeting when both users have matching FREE slots
    Given "organizer_user" has a free time slot from "2026-11-22T09:00:00Z" to "2026-11-22T10:00:00Z"
    And "participant_user" has a free time slot from "2026-11-22T09:00:00Z" to "2026-11-22T10:00:00Z"
    When "organizer_user" schedules a meeting titled "Sprint Planning" with "participant_user" from "2026-11-22T09:00:00Z" to "2026-11-22T10:00:00Z"
    Then the meeting response status code should be 201
    And the scheduled meeting should have 2 consumed slots
    And the slots for "organizer_user" and "participant_user" should transition to "BUSY"

  Scenario: Successfully query scheduled meetings by organizer
    Given a scheduled meeting "Team Retrospective" organized by "organizer_user" with "participant_user"
    When meetings are queried with organizer filter "organizer_user"
    Then the meeting query response status code should be 200
    And the results should contain meeting "Team Retrospective"

  Scenario: Successfully query scheduled meetings with combined organizer AND participant filters
    Given a scheduled meeting "Architecture Alignment" organized by "organizer_user" with "participant_user"
    When meetings are queried with organizer "organizer_user" and participant "participant_user"
    Then the meeting query response status code should be 200
    And the results should contain meeting "Architecture Alignment"

  Scenario: Successfully cancel a scheduled meeting and release consumed slots
    Given a scheduled meeting "Quarterly Review" organized by "organizer_user" with "participant_user"
    When the organizer cancels the meeting "Quarterly Review"
    Then the meeting cancellation response status code should be 204
    And the consumed slots of the meeting should transition back to "FREE"

  # -------------------------------------------------------------------------
  # NEGATIVE / CONFLICT CASES
  # -------------------------------------------------------------------------
  Scenario: Reject scheduling meeting when participant has no matching FREE slot
    Given "organizer_user" has a free time slot from "2026-11-22T14:00:00Z" to "2026-11-22T15:00:00Z"
    When "organizer_user" schedules a meeting titled "Design Review" with "participant_user" from "2026-11-22T14:00:00Z" to "2026-11-22T15:00:00Z"
    Then the meeting response status code should be 409
    And the error title should be "Business State Conflict"

  Scenario: Reject retrieving non-existent meeting by ID
    When a meeting is requested with ID 999999
    Then the meeting response status code should be 404
    And the error title should be "Resource Not Found"

  # -------------------------------------------------------------------------
  # VALIDATION CASES
  # -------------------------------------------------------------------------
  Scenario: Reject querying meetings when both organizerId and participantId are omitted
    When meetings are queried without any filter parameters
    Then the meeting response status code should be 400
    And the error title should be "Invalid Argument"
    And the error detail should indicate "At least one filter parameter ('organizerId' or 'participantId') is required"

  Scenario Outline: Reject scheduling meeting with invalid payload
    When a meeting schedule request is sent with title "<title>" and invalid timeframe from "<startTime>" to "<endTime>"
    Then the meeting response status code should be 400
    And the error title should be "Validation Failed"

    Examples:
      | title  | startTime            | endTime              |
      |        | 2026-11-22T09:00:00Z | 2026-11-22T10:00:00Z |
      | Sync   | 2026-11-22T10:00:00Z | 2026-11-22T09:00:00Z |
      | Sync   | 2026-11-22T09:00:00Z | 2026-11-22T09:00:00Z |
