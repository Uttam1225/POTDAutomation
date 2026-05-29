@regression @login
Feature: GeeksForGeeks POTD Login
  As a registered GFG user
  I want to log in to the Problem of the Day page
  So that I can access and solve the daily challenge

  Background:
    Given the user is on the login page

  @smoke
  Scenario: Successful login using credentials from config
    When the user logs in with credentials from config
    Then the user should be logged in successfully
