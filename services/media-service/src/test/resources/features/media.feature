Feature: Image storage

  Scenario: Upload a valid image
    Given a valid PNG image of 8x8 pixels
    When the image is uploaded with ownerId 1
    Then the response status is 201
    And the response contains a url starting with "/api/media/"

  Scenario: Reject a text file pretending to be an image
    Given a text file named "fake.png"
    When the file is uploaded with ownerId 1
    Then the response status is 400

  Scenario: Reject an oversized file
    Given a file larger than 5 MB
    When the file is uploaded with ownerId 1
    Then the response status is 400
