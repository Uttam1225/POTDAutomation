package com.potd.hooks;

import com.microsoft.playwright.Page;
import com.potd.utils.PlaywrightManager;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Cucumber lifecycle hooks — browser setup and teardown with auto-screenshot on failure.
 */
public class Hooks {

    @Before
    public void setUp(Scenario scenario) {
        System.out.println("\n>>> Starting scenario: " + scenario.getName());
        PlaywrightManager.initBrowser();
    }

    @After
    public void tearDown(Scenario scenario) {
        if (scenario.isFailed()) {
            captureScreenshot(scenario);
        }

        PlaywrightManager.closeBrowser();
        System.out.println(">>> Finished scenario: " + scenario.getName()
                + " [" + (scenario.isFailed() ? "FAILED" : "PASSED") + "]\n");
    }

    private void captureScreenshot(Scenario scenario) {
        try {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeName = scenario.getName().replaceAll("[^a-zA-Z0-9]", "_");
            String screenshotPath = "target/screenshots/" + safeName + "_" + timestamp + ".png";

            new File("target/screenshots").mkdirs();

            PlaywrightManager.getPage().screenshot(
                    new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)));

            System.out.println("Screenshot saved: " + screenshotPath);
        } catch (Exception e) {
            System.err.println("Could not capture screenshot: " + e.getMessage());
        }
    }
}
