Feature: Profile image upload

  @smoke
  Scenario: Upload a profile image and see it rendered
    Given the user is logged in
    When the user opens the profile page
    And the user uploads a profile image
    Then the profile avatar is displayed
    And the avatar source is a media URL
