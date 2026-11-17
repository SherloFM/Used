Feature: Favorites

  Scenario: Add a favorite without authentication
    When an unauthenticated user adds listing 1 to favorites
    Then the response status is 403

  Scenario: Check favorite status without authentication
    When an unauthenticated user checks if listing 1 is favorited
    Then the response status is 401

  Scenario: Get favorite count
    When the client requests favorite count for listing 1
    Then the response status is 401

  Scenario: List favorites without authentication
    When an unauthenticated user lists their favorites
    Then the response status is 401
