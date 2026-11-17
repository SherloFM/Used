Feature: Chat

  Scenario: List conversations without authentication
    When an unauthenticated user lists chat conversations
    Then the response status is 401

  Scenario: Create conversation without authentication
    When an unauthenticated user creates a chat conversation
    Then the response status is 403
