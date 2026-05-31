package com.potd.stepdefinitions;

import com.microsoft.playwright.Page;
import com.potd.pages.CodingPage;
import com.potd.pages.LoginPage;
import com.potd.pages.ProblemOfTheDayPage;
import com.potd.utils.ConfigReader;
import com.potd.utils.CopilotClient;
import com.potd.utils.PageFactory;
import com.potd.utils.PlaywrightFactory;
import com.potd.utils.PlaywrightManager;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.*;

/**
 * Step definitions for the end-to-end GFG Problem of the Day automation flow.
 *
 * <pre>
 *  Given user opens GFG problem of the day page
 *  When  user logs in using credentials
 *  Then  user clicks on Solve Problem
 *  Then  user switches to new tab
 *  Then  user selects Java (21)
 *  Then  user enters solution code
 *  And   user submits the solution
 * </pre>
 *
 * Credentials are read from {@code config.properties} (or env vars).
 * Solution code is generated via the GitHub Copilot API when
 * {@code copilot.api.token} is set; a placeholder is used otherwise.
 */
public class GFGPOTDSteps {

    // Page objects — initialised lazily as steps execute
    private LoginPage          loginPage;
    private ProblemOfTheDayPage potdPage;
    private CodingPage          codingPage;
    private Page                problemEditorTab;
    private String              editorBoilerplate = "";

    // -----------------------------------------------------------------------
    // Step 1 — open POTD page
    // -----------------------------------------------------------------------

    /**
     * Navigates the browser to the GFG Problem of the Day page.
     * The URL is read from {@code baseUrl} in config.properties.
     */
    @Given("user opens GFG problem of the day page")
    public void userOpensGFGProblemOfTheDayPage() {
        loginPage = PageFactory.initPage(LoginPage.class, PlaywrightManager.getPage());
        loginPage.navigateToLoginPage();   // navigates to baseUrl (POTD page)
        System.out.println("[GFGPOTDSteps] Opened: " + PlaywrightManager.getPage().url());
    }

    // -----------------------------------------------------------------------
    // Step 2 — login
    // -----------------------------------------------------------------------

    /**
     * Reads credentials from config / env vars, clicks the header "Sign In"
     * link, fills the auth form, and submits it.
     *
     * <p>Credential resolution order:
     * <ol>
     *   <li>Environment variables: {@code GFG_USERNAME} / {@code GFG_PASSWORD}</li>
     *   <li>JVM system properties: {@code username} / {@code password}</li>
     *   <li>config.properties: {@code username} / {@code password}</li>
     * </ol>
     */
    @When("user logs in using credentials")
    public void userLogsInUsingCredentials() {
        String username = ConfigReader.getProperty("username");
        String password = ConfigReader.getProperty("password");

        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "GFG username not configured. " +
                    "Set 'username' in config.properties or export GFG_USERNAME.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "GFG password not configured. " +
                    "Set 'password' in config.properties or export GFG_PASSWORD.");
        }

        // Click "Sign In" in the GFG header → redirects to auth.geeksforgeeks.org
        loginPage.clickSignIn();

        // Fill username / password and submit
        loginPage.login(username, password);

        assertTrue("Login failed — browser is still on the auth page",
                loginPage.isLoginSuccessful());
        System.out.println("[GFGPOTDSteps] Login successful — current URL: "
                + PlaywrightManager.getPage().url());
    }

    // -----------------------------------------------------------------------
    // Step 3 — click Solve Problem
    // -----------------------------------------------------------------------

    /**
     * Navigates to the POTD page (in case login redirected elsewhere),
     * then clicks the "Solve Problem" button.
     *
     * <p>Internally, {@link ProblemOfTheDayPage#clickSolveProblem()} uses
     * {@code BrowserContext.waitForPage()} to intercept the new tab before
     * it navigates, waits for DOMContentLoaded + NetworkIdle, then registers
     * the tab with {@link PlaywrightFactory}.
     */
    @Then("user clicks on Solve Problem")
    public void userClicksOnSolveProblem() {
        potdPage = PageFactory.initPage(ProblemOfTheDayPage.class,
                PlaywrightManager.getPage());
        potdPage.navigateToPOTD();   // ensures we are on the POTD page

        System.out.println("[GFGPOTDSteps] Today's problem: " + potdPage.getProblemTitle());

        // Click → new tab opens, loads, and is registered as active tab
        problemEditorTab = potdPage.clickSolveProblem();

        assertNotNull("'Solve Problem' must open a new tab", problemEditorTab);
        System.out.println("[GFGPOTDSteps] Problem editor tab URL: " + problemEditorTab.url());
    }

    // -----------------------------------------------------------------------
    // Step 4 — switch to new tab
    // -----------------------------------------------------------------------

    /**
     * Verifies that control has been handed to the problem editor tab and
     * brings it to the foreground.
     *
     * <p>The tab was already registered and set as active during
     * {@link #userClicksOnSolveProblem()}. This step explicitly confirms
     * the switch and calls {@code bringToFront()} so the browser window
     * visually focuses the new tab.
     */
    @Then("user switches to new tab")
    public void userSwitchesToNewTab() {
        int tabCount = PlaywrightFactory.getTabCount();
        assertEquals("Expected 2 open tabs after clicking Solve Problem",
                2, tabCount);

        Page activePage = PlaywrightFactory.getPage();
        assertSame("Active tab must be the problem editor tab",
                problemEditorTab, activePage);

        activePage.bringToFront();
        System.out.println("[GFGPOTDSteps] Switched to new tab (" + tabCount +
                " total) — URL: " + activePage.url());
    }

    // -----------------------------------------------------------------------
    // Step 5 — enter solution code (C++ 17, no language switch needed)
    // -----------------------------------------------------------------------

    /**
     * Gets the C++17 solution via GitHub Copilot API and enters it into the editor.
     * The editor defaults to C++ (17) — no language switch required.
     */
    @Then("user enters solution code")
    public void userEntersSolutionCode() {
        codingPage = PageFactory.initPage(CodingPage.class, PlaywrightFactory.getPage());

        // Debug screenshot — shows editor state before code entry
        try {
            java.nio.file.Files.createDirectories(
                    java.nio.file.Paths.get("target/screenshots"));
            PlaywrightFactory.getPage().screenshot(
                    new com.microsoft.playwright.Page.ScreenshotOptions()
                            .setPath(java.nio.file.Paths.get(
                                    "target/screenshots/debug_before_code_entry.png")));
            System.out.println("[GFGPOTDSteps] Pre-code-entry screenshot saved.");
        } catch (Exception ignored) {}

        String solution;
        String title = "";
        String description = "";
        String boilerplate = "";
        try {
            title       = codingPage.extractProblemTitle();
            description = codingPage.extractProblemDescription();
            boilerplate = codingPage.getCurrentEditorCode();
            editorBoilerplate = boilerplate;  // save for retry
            String problemUrl = PlaywrightFactory.getPage().url();
            System.out.println("[GFGPOTDSteps] Problem title: " + title);
            System.out.println("[GFGPOTDSteps] Problem URL: " + problemUrl);
            System.out.println("[GFGPOTDSteps] Description length: " + description.length() + " chars");
            System.out.println("[GFGPOTDSteps] Boilerplate:\n" + boilerplate);
            System.out.println("[GFGPOTDSteps] Requesting Copilot C++ solution for: " + title);
            solution = CopilotClient.generateCppSolution(title, description, boilerplate, problemUrl);
            System.out.println("[GFGPOTDSteps] Copilot solution received ("
                    + solution.lines().count() + " lines)");
        } catch (Exception apiError) {
            System.out.println("[GFGPOTDSteps] Copilot API unavailable — using boilerplate. "
                    + apiError.getMessage());
            solution = codingPage.getCurrentEditorCode();
            if (solution == null || solution.isBlank()) {
                solution = buildPlaceholderCppSolution();
            }
            System.out.println("[GFGPOTDSteps] Using existing editor code ("
                    + solution.lines().count() + " lines)");
        }

        codingPage.clearAndEnterCode(solution);
        System.out.println("[GFGPOTDSteps] Solution entered into editor");
    }

    // -----------------------------------------------------------------------
    // Step 7 — submit and verify verdict
    // -----------------------------------------------------------------------

    /**
     * Clicks the Submit button. If the verdict is not "Accepted", asks Copilot
     * to fix the solution and retries once before failing.
     */
    @And("user submits the solution")
    public void userSubmitsTheSolution() {
        if (codingPage == null) {
            codingPage = PageFactory.initPage(CodingPage.class,
                    PlaywrightFactory.getPage());
        }

        codingPage.clickSubmit();
        String verdict = codingPage.waitForResult();
        System.out.println("[GFGPOTDSteps] Verdict (attempt 1): " + verdict);

        if (!"Accepted".equals(verdict)) {
            System.out.println("[GFGPOTDSteps] Not accepted — asking Copilot to fix the solution...");
            try {
                String title       = codingPage.extractProblemTitle();
                String description = codingPage.extractProblemDescription();
                String current     = codingPage.getCurrentEditorCode();
                String fixed = CopilotClient.generateFixedCppSolution(
                        title, description, current, verdict, editorBoilerplate);
                System.out.println("[GFGPOTDSteps] Fixed solution (" +
                        fixed.lines().count() + " lines):\n" + fixed);
                codingPage.clearAndEnterCode(fixed);
                codingPage.clickSubmit();
                verdict = codingPage.waitForResult();
                System.out.println("[GFGPOTDSteps] Verdict (attempt 2): " + verdict);
            } catch (Exception e) {
                System.out.println("[GFGPOTDSteps] Retry failed: " + e.getMessage());
            }
        }

        assertNotNull("Verdict must not be null after submission", verdict);
        assertEquals(
            "Expected 'Accepted' but got: " + verdict,
            "Accepted", verdict);
    }

    // -----------------------------------------------------------------------
    // Private helper
    // -----------------------------------------------------------------------

    /**
     * Returns a minimal compilable C++17 stub used when the Copilot API
     * token is not configured.
     */
    private String buildPlaceholderCppSolution() {
        return "#include <bits/stdc++.h>\n" +
               "using namespace std;\n\n" +
               "class Solution {\n" +
               "  public:\n" +
               "    // Placeholder — set copilot.api.token in config.properties\n" +
               "    // to enable AI-powered solution generation via GitHub Copilot.\n" +
               "    int solve(vector<int>& arr) {\n" +
               "        return 0;\n" +
               "    }\n" +
               "};\n";
    }

    // -----------------------------------------------------------------------
    // Alternative step phrasings (Feature: Solve Problem of the Day)
    // -----------------------------------------------------------------------

    /**
     * Alias: "When user logs in"
     * Reads credentials from config / env vars and completes the full
     * auth flow (Sign In link → auth form → submit).
     */
    @When("user logs in")
    public void userLogsIn() {
        userLogsInUsingCredentials();
    }

    /**
     * Alias: "Then user clicks Solve Problem"
     * Clicks the Solve Problem button and captures the new coding tab.
     */
    @Then("user clicks Solve Problem")
    public void userClicksSolveProblem() {
        userClicksOnSolveProblem();
    }

    /**
     * Alias: "And switches to coding tab"
     * Verifies the coding tab is now active and brings it to the foreground.
     */
    @And("switches to coding tab")
    public void switchesToCodingTab() {
        userSwitchesToNewTab();
    }

    /**
     * Alias: "Then user selects Java language" — no-op now that we stay on C++ 17.
     */
    @Then("user selects Java language")
    public void userSelectsJavaLanguage() {
        // Language stays at the default C++ (17) — no action needed
        System.out.println("[GFGPOTDSteps] Language selection skipped — using default C++ (17)");
    }

    /**
     * Alias: "And enters solution"
     * Generates a solution via GitHub Copilot API and enters it into the
     * Monaco editor (falls back to placeholder if token is not configured).
     */
    @And("enters solution")
    public void entersSolution() {
        userEntersSolutionCode();
    }

    /**
     * Alias: "And submits solution"
     * Clicks Submit and waits for the verdict to be displayed.
     */
    @And("submits solution")
    public void submitsSolution() {
        userSubmitsTheSolution();
    }
}
