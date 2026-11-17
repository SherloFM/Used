Feature: Page routing

  Scenario: Home page loads
    When the client requests the home page
    Then the response status is 200

  Scenario: Login page loads
    When the client requests the login page
    Then the response status is 200

  Scenario: Profile page loads
    When the client requests the profile page
    Then the response status is 200
