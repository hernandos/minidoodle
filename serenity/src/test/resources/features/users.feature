@users
Feature: User Registration Management
  As a meeting organizer or participant
  I want to register an account in MiniDoodle
  So that I can declare my availability and participate in meetings

  Background:
    Given the MiniDoodle API is running and ready

  # -------------------------------------------------------------------------
  # POSITIVE CASES
  # -------------------------------------------------------------------------
  Scenario: Successfully register a new unique user profile
    When I submit a request to register a user with:
      | username | alice_smith        |
      | name     | Alice Smith        |
      | email    | alice@example.com  |
    Then the user registration response status code should be 201
    And the registered user details should match:
      | username | alice_smith        |
      | name     | Alice Smith        |
      | email    | alice@example.com  |
    And the response should contain a valid generated user ID

  # -------------------------------------------------------------------------
  # NEGATIVE / CONFLICT CASES
  # -------------------------------------------------------------------------
  Scenario: Reject user registration when username is already taken
    Given an existing registered user with username "existing_user" and email "first@example.com"
    When I submit a request to register a user with:
      | username | existing_user        |
      | name     | Duplicate Username   |
      | email    | different@example.com|
    Then the user registration response status code should be 409
    And the error title should be "Business State Conflict"

  Scenario: Reject user registration when email is already registered
    Given an existing registered user with username "first_user" and email "shared@example.com"
    When I submit a request to register a user with:
      | username | second_user         |
      | name     | Duplicate Email     |
      | email    | shared@example.com  |
    Then the user registration response status code should be 409
    And the error title should be "Business State Conflict"

  # -------------------------------------------------------------------------
  # VALIDATION CASES
  # -------------------------------------------------------------------------
  Scenario Outline: Reject user registration with invalid input constraints
    When I submit a request to register a user with:
      | username | <username> |
      | name     | <name>     |
      | email    | <email>    |
    Then the user registration response status code should be 400
    And the error title should be "Validation Failed"

    Examples:
      | username    | name        | email              |
      | ab          | Valid Name  | user@example.com   |
      | valid_user  |             | user@example.com   |
      | valid_user  | Valid Name  | not-an-email       |
      |             |             |                    |
