@time_slots
Feature: TimeSlot Proactive Availability Management
  As a registered user
  I want to declare, update, and remove proactive time slots in my calendar
  So that organizers know when I am available to meet

  Background:
    Given the MiniDoodle API is running and ready
    And a registered user "timeslot_owner" exists in the system

  # -------------------------------------------------------------------------
  # POSITIVE CASES
  # -------------------------------------------------------------------------
  Scenario: Successfully declare a proactive FREE time slot
    When "timeslot_owner" creates a time slot from "2026-11-20T09:00:00Z" to "2026-11-20T10:00:00Z"
    Then the time slot response status code should be 201
    And the created time slot status should be "FREE"
    And the created time slot user should be "timeslot_owner"

  Scenario: Successfully update an existing FREE time slot timeframe
    Given "timeslot_owner" has a free time slot from "2026-11-20T11:00:00Z" to "2026-11-20T12:00:00Z"
    When "timeslot_owner" updates the time slot to range from "2026-11-20T11:30:00Z" to "2026-11-20T12:30:00Z"
    Then the time slot response status code should be 200
    And the updated time slot should start at "2026-11-20T11:30:00Z" and end at "2026-11-20T12:30:00Z"

  Scenario: Successfully delete an existing FREE time slot
    Given "timeslot_owner" has a free time slot from "2026-11-20T14:00:00Z" to "2026-11-20T15:00:00Z"
    When "timeslot_owner" deletes the time slot
    Then the time slot response status code should be 204

  # -------------------------------------------------------------------------
  # NEGATIVE / CONFLICT CASES
  # -------------------------------------------------------------------------
  Scenario: Reject modifying a time slot that is already BUSY
    Given "timeslot_owner" has a busy time slot from "2026-11-20T16:00:00Z" to "2026-11-20T17:00:00Z"
    When "timeslot_owner" updates the time slot to range from "2026-11-20T16:30:00Z" to "2026-11-20T17:30:00Z"
    Then the time slot response status code should be 409
    And the error title should be "Business State Conflict"

  Scenario: Reject deleting a non-existent time slot
    When a request is sent to delete time slot with ID 999999
    Then the time slot response status code should be 404
    And the error title should be "Resource Not Found"

  # -------------------------------------------------------------------------
  # VALIDATION CASES
  # -------------------------------------------------------------------------
  Scenario: Reject creating time slot when start time is equal or after end time
    When "timeslot_owner" creates a time slot from "2026-11-20T10:00:00Z" to "2026-11-20T09:00:00Z"
    Then the time slot response status code should be 400
    And the error title should be "Validation Failed"

  Scenario: Reject creating time slot with null mandatory fields
    When a request is sent to create a time slot with null fields
    Then the time slot response status code should be 400
    And the error title should be "Validation Failed"
