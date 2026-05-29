@regression @potd
Feature: Problem of the Day — Solve Problem
  As a logged-in GFG user
  I want to click "Solve Problem" on the POTD page
  So that the problem editor opens in a new tab with control switched to it

  Background:
    Given the user is on the login page
    And  the user logs in with credentials from config
    And  the user is on the Problem of the Day page

  @smoke
  Scenario: Solve Problem opens a new tab and switches control to it
    When  the user clicks the Solve Problem button
    Then  a new tab should open with the problem editor
    And   the control should be on the new problem tab
    And   the problem editor URL should contain "geeksforgeeks.org"
    And   the problem editor page title should not be empty

  @smoke @editor
  Scenario: Monaco editor is visible in the problem editor tab
    When  the user clicks the Solve Problem button
    Then  a new tab should open with the problem editor
    And   the coding editor should be visible

  @e2e @submit
  Scenario: Select Java language, enter code and submit solution
    When  the user clicks the Solve Problem button
    Then  a new tab should open with the problem editor
    And   the coding editor should be visible
    When  the user selects language "Java (21)"
    And   the user enters the solution code
    And   the user submits the solution
    Then  the submission verdict should be displayed

  @e2e @keyboard
  Scenario: Clear editor and type Java solution using keyboard
    When  the user clicks the Solve Problem button
    Then  a new tab should open with the problem editor
    And   the coding editor should be visible
    When  the user selects language "Java (21)"
    And   the user clears the editor
    And   the user types the solution code
    And   the user submits the solution
    Then  the submission verdict should be displayed

  @e2e @paste
  Scenario: Clear editor and paste Java solution via clipboard
    When  the user clicks the Solve Problem button
    Then  a new tab should open with the problem editor
    And   the coding editor should be visible
    When  the user selects language "Java (21)"
    And   the user pastes the solution code
    And   the user submits the solution
    Then  the submission verdict should be displayed

  @e2e @copilot
  Scenario: Use GitHub Copilot to generate and submit the POTD solution
    When  the user clicks the Solve Problem button
    Then  a new tab should open with the problem editor
    And   the coding editor should be visible
    When  the user selects language "Java (21)"
    And   the user uses Copilot to generate and enter the solution
    And   the user submits the solution
    Then  the submission verdict should be displayed
