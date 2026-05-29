@regression @potd @e2e
Feature: GeeksForGeeks POTD End-to-End Flow
  As a GFG user
  I want to open, solve, and submit the Problem of the Day
  So that I can practice daily competitive programming

  @smoke
  Scenario: Complete POTD solve flow — from login to submission
    Given user opens GFG problem of the day page
    When  user logs in using credentials
    Then  user clicks on Solve Problem
    Then  user switches to new tab
    Then  user enters solution code
    And   user submits the solution
