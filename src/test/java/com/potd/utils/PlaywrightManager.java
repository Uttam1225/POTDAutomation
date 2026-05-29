package com.potd.utils;

import com.microsoft.playwright.Page;

/**
 * Thin facade over {@link PlaywrightFactory} used by Cucumber {@code Hooks}.
 *
 * <p>All heavy lifting (ThreadLocal management, browser launch, context and
 * tab lifecycle) is handled by {@link PlaywrightFactory}. This class exists
 * solely to preserve a stable API for the Cucumber hook layer.
 */
public class PlaywrightManager {

    private PlaywrightManager() {}

    /** Initialises the browser session for the current thread. */
    public static void initBrowser() {
        PlaywrightFactory.launchBrowser();
    }

    /** Returns the active {@link Page} for the current thread. */
    public static Page getPage() {
        return PlaywrightFactory.getPage();
    }

    /** Closes the browser session for the current thread. */
    public static void closeBrowser() {
        PlaywrightFactory.quitBrowser();
    }
}
