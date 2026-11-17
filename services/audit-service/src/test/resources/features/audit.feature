Feature: Audit log

  Scenario: Access audit endpoint without authentication
    When an unauthenticated user requests audit activity
    Then the response status is 401

  Scenario: Access internal audit endpoint without token
    When a client requests internal audit log without token
    Then the response status is 401
