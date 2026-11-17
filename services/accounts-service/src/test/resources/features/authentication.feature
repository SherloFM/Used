Feature: User authentication

  Scenario: Register a new user
    Given no user exists with email "newuser@test.com"
    When the user registers with email "newuser@test.com" and password "Password123"
    Then the response status is 200
    And the response contains a user id

  Scenario: Login with valid credentials
    Given a user exists with email "alice@used.local" and password "password123"
    When the user logs in with email "alice@used.local" and password "password123"
    Then the response status is 200
    And the response sets the "used_auth" cookie

  Scenario: Login with wrong password
    Given a user exists with email "alice@used.local" and password "password123"
    When the user logs in with email "alice@used.local" and password "wrong"
    Then the response status is 401

  Scenario: CSRF token endpoint
    When the client requests a CSRF token
    Then the response status is 200
    And the response contains a token field

  Scenario: Unauthenticated profile request
    When an unauthenticated user requests their profile
    Then the response status is 401

  Scenario: Authenticated profile request
    Given a user exists with email "alice@used.local" and password "password123"
    When an authenticated user requests their profile
    Then the response status is 200
    And the profile response contains the user email
