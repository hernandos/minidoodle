@availability
Feature: Availability and Calendar Queries
  As a meeting organizer or participant
  I want to view user calendars and calculate multi-user aggregated availability
  So that I can identify suitable timeframes to schedule collaboration

  Background:
    Given the MiniDoodle API is running and ready
    And registered users "user_alpha" and "user_beta" exist in the system

  # -------------------------------------------------------------------------
  # POSITIVE CASES
  # -------------------------------------------------------------------------
  Scenario: Successfully retrieve personal calendar view for a user
    Given "user_alpha" has a free time slot from "2026-11-25T08:00:00Z" to "2026-11-25T09:00:00Z"
    When personal calendar is requested for "user_alpha"
    Then the availability response status code should be 200
    And the calendar should belong to "user_alpha"
    And the calendar should include 1 time slot

  Scenario: Successfully query aggregated availability across users within a timeframe
    Given "user_alpha" has a free time slot from "2026-11-25T10:00:00Z" to "2026-11-25T11:00:00Z"
    And "user_beta" has a free time slot from "2026-11-25T10:00:00Z" to "2026-11-25T11:00:00Z"
    When aggregated availability is requested from "2026-11-25T00:00:00Z" to "2026-11-25T23:59:59Z" with freeOnly "true"
    Then the availability response status code should be 200
    And the aggregated availability should contain slots for both "user_alpha" and "user_beta"

  # -------------------------------------------------------------------------
  # NEGATIVE / NOT FOUND CASES
  # -------------------------------------------------------------------------
  Scenario: Reject personal calendar query for non-existent user
    When personal calendar is requested for user ID 999999
    Then the availability response status code should be 404
    And the error title should be "Resource Not Found"

  # -------------------------------------------------------------------------
  # VALIDATION CASES
  # -------------------------------------------------------------------------
  Scenario: Reject aggregated availability query without mandatory timeframe parameters
    When aggregated availability is requested without timeframe parameters
    Then the availability response status code should be 400
    And the error title should be "Missing Parameter"
