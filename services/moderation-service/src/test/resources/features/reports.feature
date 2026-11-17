Feature: Reports

  Scenario: Submit a report without authentication
    When an unauthenticated user submits a report for listing 1
    Then the response status is 403

  Scenario: List reports without authentication
    When an unauthenticated user lists their reports
    Then the response status is 401

  Scenario: Admin list reports without authentication
    When an unauthenticated user accesses admin reports
    Then the response status is 401
