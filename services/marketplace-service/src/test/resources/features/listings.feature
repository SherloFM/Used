Feature: Listing management

  Scenario: List all categories
    When the client requests all categories
    Then the response status is 200

  Scenario: Search listings
    When the client searches listings with keyword "phone"
    Then the response status is 200

  Scenario: Get a non-existent listing
    When the client requests listing with id 999999
    Then the response status is 400

  Scenario: Create a listing without authentication
    When an unauthenticated user creates a listing
    Then the response status is 403

  Scenario: Get my listings without authentication
    When an unauthenticated user requests their listings
    Then the response status is 401
