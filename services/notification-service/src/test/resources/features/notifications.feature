Feature: Notifications

  Scenario: List notifications without authentication
    When an unauthenticated user lists notifications
    Then the response status is 401

  Scenario: Get unread count without authentication
    When an unauthenticated user gets unread count
    Then the response status is 401

  Scenario: Mark all read without authentication
    When an unauthenticated user marks all notifications read
    Then the response status is 403
