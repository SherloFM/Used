Feature: Authentication

  Scenario: Successful login redirects away from login page
    Given the user is on the login page
    When the user logs in with valid credentials
    Then the user is redirected away from the login page

  Scenario: Profile page loads after login
    Given the user is on the login page
    When the user logs in with valid credentials
    Then the user is redirected away from the login page
    And the profile page loads
