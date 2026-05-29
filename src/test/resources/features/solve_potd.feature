Feature: Solve Problem of the Day

  Scenario: Login and solve problem
    Given user opens GFG problem of the day page
    When  user logs in
    Then  user clicks Solve Problem
    And   switches to coding tab
    Then  user selects Java language
    And   enters solution
    And   submits solution
