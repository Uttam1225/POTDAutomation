package com.potd.base;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.potd.utils.PlaywrightFactory;
import org.junit.After;
import org.junit.Before;

import java.util.List;

/**
 * Base class for non-Cucumber (plain JUnit) Playwright tests.
 *
 * <p>Extend this class to inherit automatic browser setup/teardown and
 * convenient tab-management helpers. The browser is launched once before
 * each test method and fully closed after it.
 *
 * <pre>{@code
 * public class HomePageTest extends BaseTest {
 *
 *     @Test
 *     public void titleIsCorrect() {
 *         getPage().navigate("https://example.com");
 *         assertEquals("Example Domain", getPage().title());
 *     }
 *
 *     @Test
 *     public void multiTabFlow() {
 *         getPage().navigate("https://example.com");
 *         Page tab2 = newTab();
 *         tab2.navigate("https://playwright.dev");
 *         switchToTab(0);           // back to tab 0
 *         assertEquals(2, getTabCount());
 *     }
 * }
 * }</pre>
 *
 * <p>For <strong>Cucumber</strong> tests the browser lifecycle is handled by
 * {@code com.potd.hooks.Hooks} instead — do not extend this class from step
 * definition classes.
 */
public abstract class BaseTest {

    // -----------------------------------------------------------------------
    // JUnit lifecycle
    // -----------------------------------------------------------------------

    /**
     * Launches the configured browser before every test method.
     * Browser type and headless mode are read from {@code config.properties}.
     */
    @Before
    public void setUp() {
        PlaywrightFactory.launchBrowser();
    }

    /**
     * Fully closes the browser (all tabs, context, Playwright instance)
     * after every test method, regardless of pass/fail.
     */
    @After
    public void tearDown() {
        PlaywrightFactory.quitBrowser();
    }

    // -----------------------------------------------------------------------
    // Page / Tab helpers (available to every subclass)
    // -----------------------------------------------------------------------

    /**
     * Returns the currently active {@link Page} (tab).
     *
     * @return active Playwright {@link Page}
     */
    protected Page getPage() {
        return PlaywrightFactory.getPage();
    }

    /**
     * Opens a new tab in the current browser context, sets it as active,
     * and returns it.
     *
     * @return the newly opened {@link Page}
     */
    protected Page newTab() {
        return PlaywrightFactory.newTab();
    }

    /**
     * Switches focus to the tab at {@code index} and returns it.
     * Does not close any tab.
     *
     * @param index zero-based tab index
     * @return the {@link Page} at that index
     */
    protected Page switchToTab(int index) {
        return PlaywrightFactory.switchToTab(index);
    }

    /**
     * Returns the tab at {@code index} without changing the active tab.
     *
     * @param index zero-based tab index
     * @return the {@link Page} at that index
     */
    protected Page getTab(int index) {
        return PlaywrightFactory.getTab(index);
    }

    /**
     * Returns an unmodifiable view of all open tabs for this thread.
     *
     * @return immutable list of open {@link Page} instances
     */
    protected List<Page> getAllTabs() {
        return PlaywrightFactory.getAllTabs();
    }

    /**
     * Returns the number of currently open tabs.
     */
    protected int getTabCount() {
        return PlaywrightFactory.getTabCount();
    }

    /**
     * Closes the tab at {@code index}. Cannot close the last remaining tab.
     *
     * @param index zero-based tab index
     */
    protected void closeTab(int index) {
        PlaywrightFactory.closeTab(index);
    }

    /**
     * Creates a new isolated {@link BrowserContext} (fresh cookies / storage)
     * within the existing browser. Useful for multi-user or multi-session tests.
     * The previous context and all its tabs are closed.
     *
     * @return the newly created {@link BrowserContext}
     */
    protected BrowserContext newContext() {
        return PlaywrightFactory.newContext();
    }
}
