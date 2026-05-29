package com.potd.stepdefinitions;

import com.microsoft.playwright.Page;
import com.potd.pages.CodingPage;
import com.potd.pages.ProblemOfTheDayPage;
import com.potd.utils.CopilotClient;
import com.potd.utils.PageFactory;
import com.potd.utils.PlaywrightFactory;
import com.potd.utils.PlaywrightManager;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.*;

public class POTDSteps {

    private ProblemOfTheDayPage potdPage;
    private Page problemEditorTab;
    private CodingPage codingPage;

    @Given("the user is on the Problem of the Day page")
    public void theUserIsOnTheProblemOfTheDayPage() {
        potdPage = PageFactory.initPage(ProblemOfTheDayPage.class, PlaywrightManager.getPage());
        potdPage.navigateToPOTD();
        assertTrue("POTD banner should be visible after navigation",
                potdPage.isPOTDBannerVisible());
    }

    @When("the user clicks the Solve Problem button")
    public void theUserClicksTheSolveProblemButton() {
        // clickSolveProblem() handles all three steps internally:
        //   1. waitForPage() — intercepts the new tab on click
        //   2. waitForLoadState(DOMCONTENTLOADED) + waitForLoadState(NETWORKIDLE)
        //   3. registerTab() — tracks tab in factory + bringToFront()
        problemEditorTab = potdPage.clickSolveProblem();
    }

    @Then("a new tab should open with the problem editor")
    public void aNewTabShouldOpenWithTheProblemEditor() {
        assertNotNull("Problem editor tab must not be null", problemEditorTab);

        // Factory tab count must have grown by 1
        assertEquals("There should be exactly 2 open tabs after clicking Solve Problem",
                2, PlaywrightFactory.getTabCount());

        // The new tab must be the active one in the factory
        assertSame("Problem editor tab must be the active tab",
                problemEditorTab, PlaywrightFactory.getPage());
    }

    @Then("the problem editor URL should contain {string}")
    public void theProblemEditorUrlShouldContain(String expectedUrlFragment) {
        String actualUrl = problemEditorTab.url();
        assertTrue("Problem editor URL '" + actualUrl + "' does not contain '" + expectedUrlFragment + "'",
                actualUrl.contains(expectedUrlFragment));
    }

    @And("the problem editor page title should not be empty")
    public void theProblemEditorPageTitleShouldNotBeEmpty() {
        String title = problemEditorTab.title();
        assertFalse("Problem editor page title should not be empty", title.isBlank());
        System.out.println("[POTDSteps] Problem editor page title: " + title);
    }

    @Then("the control should be on the new problem tab")
    public void theControlShouldBeOnTheNewProblemTab() {
        // Verify PlaywrightFactory's active page is the problem editor tab
        Page activePage = PlaywrightFactory.getPage();
        assertSame("Active tab in PlaywrightFactory must be the problem editor tab",
                problemEditorTab, activePage);

        System.out.println("[POTDSteps] Active tab URL: " + activePage.url());
        System.out.println("[POTDSteps] Total open tabs: " + PlaywrightFactory.getTabCount());
    }

    // ------------------------------------------------------------------
    // Coding page steps
    // ------------------------------------------------------------------

    @When("the user selects language {string}")
    public void theUserSelectsLanguage(String language) {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());
        }
        codingPage.selectLanguage(language);
    }

    @When("the user enters the solution code")
    public void theUserEntersTheSolutionCode() {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());
        }
        // Minimal Java stub — replace with actual solution before running
        String sampleCode =
            "class Solution {\n" +
            "    public int solve(int[] arr) {\n" +
            "        // TODO: implement solution\n" +
            "        return 0;\n" +
            "    }\n" +
            "}\n";
        codingPage.enterCode(sampleCode);
    }

    @When("the user submits the solution")
    public void theUserSubmitsTheSolution() {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());
        }
        codingPage.clickSubmit();
    }

    @Then("the submission verdict should be displayed")
    public void theSubmissionVerdictShouldBeDisplayed() {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());
        }
        String verdict = codingPage.waitForResult();
        assertNotNull("Verdict must not be null", verdict);
        assertFalse("Verdict must not be empty", verdict.isBlank());
        System.out.println("[POTDSteps] Final verdict: " + verdict);
    }

    @Then("the coding editor should be visible")
    public void theCodingEditorShouldBeVisible() {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());
        }
        assertTrue("Monaco editor should be visible on the problem editor page",
                codingPage.isEditorReady());
    }

    // ------------------------------------------------------------------
    // Keyboard / paste entry steps
    // ------------------------------------------------------------------

    @When("the user clears the editor")
    public void theUserClearsTheEditor() {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());
        }
        codingPage.clearEditor();
    }

    @When("the user types the solution code")
    public void theUserTypesTheSolutionCode() {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());
        }
        String sampleCode =
            "class Solution {\n" +
            "    public int solve(int[] arr) {\n" +
            "        // TODO: implement solution\n" +
            "        return 0;\n" +
            "    }\n" +
            "}\n";
        codingPage.typeCode(sampleCode);    // Playwright keyboard.type()
    }

    @When("the user pastes the solution code")
    public void theUserPastesTheSolutionCode() {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());
        }
        String sampleCode =
            "class Solution {\n" +
            "    public int solve(int[] arr) {\n" +
            "        // TODO: implement solution\n" +
            "        return 0;\n" +
            "    }\n" +
            "}\n";
        codingPage.pasteCode(sampleCode);   // navigator.clipboard + Ctrl+V
    }

    // ------------------------------------------------------------------
    // Copilot AI solution step
    // ------------------------------------------------------------------

    /**
     * Fetches the problem title + description from the GFG coding page,
     * calls the GitHub Copilot API to generate a Java solution, then
     * enters it into the Monaco editor using the best available strategy.
     */
    @When("the user uses Copilot to generate and enter the solution")
    public void theUserUsesCopilotToGenerateAndEnterTheSolution() {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());
        }

        // Extract problem context from the page
        String title       = codingPage.extractProblemTitle();
        String description = codingPage.extractProblemDescription();
        System.out.println("[POTDSteps] Fetching Copilot solution for: " + title);

        // Call GitHub Copilot API
        String solution = CopilotClient.generateJavaSolution(title, description);
        System.out.println("[POTDSteps] Solution received, entering into editor...");

        // Clear editor and enter solution using best available strategy
        codingPage.clearAndEnterCode(solution);
    }
}
