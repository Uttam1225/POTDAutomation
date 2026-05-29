package com.potd.utils;

import com.microsoft.playwright.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Core Playwright factory responsible for the full browser lifecycle.
 *
 * <p>All state is held in {@link ThreadLocal} variables, making this class
 * safe for parallel test execution — each thread owns its own browser,
 * context, and tab list.
 *
 * <h3>Tab model</h3>
 * <pre>
 *  BrowserContext
 *   ├── Tab 0  (first page opened on launchBrowser)
 *   ├── Tab 1  (opened via newTab)
 *   └── Tab N  …
 * </pre>
 * The "active tab" is the tab returned by {@link #getPage()}.
 * Call {@link #switchToTab(int)} to change it.
 *
 * <h3>Context model</h3>
 * {@link #newContext()} creates a fresh, isolated {@link BrowserContext}
 * (separate cookies / storage — useful for multi-user scenarios).
 * It resets the tab list to a single new page in that context.
 */
public class PlaywrightFactory {

    // -----------------------------------------------------------------------
    // ThreadLocal state
    // -----------------------------------------------------------------------

    private static final ThreadLocal<Playwright>      playwrightTL     = new ThreadLocal<>();
    private static final ThreadLocal<Browser>         browserTL        = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext>  contextTL        = new ThreadLocal<>();
    /** All open tabs (pages) for the current thread. */
    private static final ThreadLocal<List<Page>>      tabsTL           = new ThreadLocal<>();
    /** Index into tabsTL that is currently "active". */
    private static final ThreadLocal<Integer>         activeTabIndexTL = new ThreadLocal<>();

    private PlaywrightFactory() {}

    // -----------------------------------------------------------------------
    // Lifecycle — launch / quit
    // -----------------------------------------------------------------------

    /**
     * Launches the configured browser, opens a default context and one tab.
     * Must be called once per thread before any other method.
     *
     * Anti-bot measures applied:
     *  - --disable-blink-features=AutomationControlled  (removes navigator.webdriver CDP flag)
     *  - Real Chrome User-Agent string on the context
     *  - addInitScript patches navigator.webdriver = undefined at the JS level
     *  - Realistic viewport, locale, timezone
     */
    public static void launchBrowser() {
        String browserName = ConfigReader.getProperty("browser", "chromium");
        boolean headless   = Boolean.parseBoolean(ConfigReader.getProperty("headless", "false"));

        Playwright playwright = Playwright.create();
        playwrightTL.set(playwright);

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(Arrays.asList(
                        "--disable-blink-features=AutomationControlled",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-web-security"
                ));

        Browser browser;
        switch (browserName.toLowerCase()) {
            case "firefox":
                browser = playwright.firefox().launch(launchOptions);
                break;
            case "webkit":
                browser = playwright.webkit().launch(launchOptions);
                break;
            default:
                browser = playwright.chromium().launch(launchOptions);
        }
        browserTL.set(browser);

        contextTL.set(createContext(browser));

        List<Page> tabs = new ArrayList<>();
        tabs.add(contextTL.get().newPage());
        tabsTL.set(tabs);
        activeTabIndexTL.set(0);
    }

    /**
     * Closes all tabs, the context, the browser and the Playwright instance
     * for the current thread, then removes all ThreadLocal values.
     */
    public static void quitBrowser() {
        closeTabs();

        if (contextTL.get() != null) {
            contextTL.get().close();
            contextTL.remove();
        }
        if (browserTL.get() != null) {
            browserTL.get().close();
            browserTL.remove();
        }
        if (playwrightTL.get() != null) {
            playwrightTL.get().close();
            playwrightTL.remove();
        }

        tabsTL.remove();
        activeTabIndexTL.remove();
    }

    // -----------------------------------------------------------------------
    // Context management
    // -----------------------------------------------------------------------

    /**
     * Creates a fresh, isolated {@link BrowserContext} within the existing
     * browser (equivalent to a new incognito window with separate cookies /
     * storage). The previous context is closed. The tab list is reset to a
     * single new page in the new context.
     *
     * @return the newly created {@link BrowserContext}
     */
    public static BrowserContext newContext() {
        if (browserTL.get() == null) {
            throw new IllegalStateException("Browser not launched. Call launchBrowser() first.");
        }

        // Close the current context (and all its pages) before creating a new one
        closeTabs();
        if (contextTL.get() != null) {
            contextTL.get().close();
        }

        BrowserContext context = createContext(browserTL.get());
        contextTL.set(context);

        List<Page> tabs = new ArrayList<>();
        tabs.add(context.newPage());
        tabsTL.set(tabs);
        activeTabIndexTL.set(0);

        return context;
    }

    /** Returns the current {@link BrowserContext} for this thread. */
    public static BrowserContext getContext() {
        return contextTL.get();
    }

    // -----------------------------------------------------------------------
    // Tab (Page) management
    // -----------------------------------------------------------------------

    /**
     * Returns the currently active {@link Page} (tab).
     * Use {@link #switchToTab(int)} to change the active tab.
     */
    public static Page getPage() {
        return tabsTL.get().get(activeTabIndexTL.get());
    }

    /**
     * Opens a new tab (page) in the current context, sets it as active,
     * and returns it.
     *
     * @return the newly opened {@link Page}
     */
    public static Page newTab() {
        if (contextTL.get() == null) {
            throw new IllegalStateException("No active context. Call launchBrowser() first.");
        }
        Page newPage = contextTL.get().newPage();
        List<Page> tabs = tabsTL.get();
        tabs.add(newPage);
        activeTabIndexTL.set(tabs.size() - 1);
        return newPage;
    }

    /**
     * Switches the active tab to the given index without closing any tab.
     *
     * @param index zero-based tab index
     * @return the {@link Page} at that index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public static Page switchToTab(int index) {
        validateTabIndex(index);
        activeTabIndexTL.set(index);
        Page page = tabsTL.get().get(index);
        page.bringToFront();
        return page;
    }

    /**
     * Returns the {@link Page} at {@code index} without changing the active tab.
     *
     * @param index zero-based tab index
     * @return the {@link Page} at that index
     */
    public static Page getTab(int index) {
        validateTabIndex(index);
        return tabsTL.get().get(index);
    }

    /**
     * Returns an unmodifiable view of all open tabs for this thread.
     *
     * @return immutable list of open {@link Page} instances
     */
    public static List<Page> getAllTabs() {
        return Collections.unmodifiableList(tabsTL.get());
    }

    /**
     * Registers a {@link Page} that was opened externally by the browser
     * (e.g., via a {@code target="_blank"} link) into this factory's tab list
     * and sets it as the active tab.
     *
     * <p>Use this after intercepting a browser-spawned page with
     * {@code context.waitForPage(...)}.
     *
     * @param externalPage the externally-opened {@link Page}
     */
    public static void registerTab(Page externalPage) {
        List<Page> tabs = tabsTL.get();
        if (tabs == null) {
            throw new IllegalStateException(
                    "No active browser session. Call launchBrowser() first.");
        }
        tabs.add(externalPage);
        activeTabIndexTL.set(tabs.size() - 1);
        // Bring the new tab to the foreground — switches visual control
        externalPage.bringToFront();
    }

    /**
     * Returns the number of currently open tabs.
     */
    public static int getTabCount() {
        return tabsTL.get().size();
    }

    /**
     * Closes the tab at {@code index} and removes it from the list.
     * If the closed tab was the active one, the active index is reset to 0.
     * Cannot close the last remaining tab.
     *
     * @param index zero-based tab index
     * @throws IllegalStateException     if only one tab is open
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public static void closeTab(int index) {
        List<Page> tabs = tabsTL.get();
        if (tabs.size() == 1) {
            throw new IllegalStateException(
                    "Cannot close the last tab. Use quitBrowser() to end the session.");
        }
        validateTabIndex(index);

        tabs.get(index).close();
        tabs.remove(index);

        // Adjust active index if needed
        int active = activeTabIndexTL.get();
        if (active >= tabs.size()) {
            activeTabIndexTL.set(tabs.size() - 1);
        } else if (active == index) {
            activeTabIndexTL.set(0);
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a {@link BrowserContext} with anti-bot settings applied:
     * <ul>
     *   <li>Real Chrome User-Agent (matches the Chromium version bundled with Playwright)</li>
     *   <li>Realistic viewport, locale, timezone</li>
     *   <li>Clipboard permissions granted</li>
     *   <li>{@code addInitScript} patches {@code navigator.webdriver} to {@code undefined}
     *       at the JS engine level — runs before any page script</li>
     * </ul>
     */
    private static BrowserContext createContext(Browser browser) {
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(1280, 800)
                        // Real Chrome UA — prevents "Headless" detection via User-Agent sniffing
                        .setUserAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/125.0.0.0 Safari/537.36")
                        .setLocale("en-IN")
                        .setTimezoneId("Asia/Kolkata")
        );

        // Patch navigator.webdriver BEFORE any page script runs.
        // This neutralises the most common headless-detection check.
        context.addInitScript(
            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });"
        );

        // Grant clipboard permissions so pasteCode() can use navigator.clipboard
        context.grantPermissions(Arrays.asList("clipboard-read", "clipboard-write"));

        return context;
    }

    private static void closeTabs() {
        List<Page> tabs = tabsTL.get();
        if (tabs != null) {
            tabs.forEach(p -> { try { p.close(); } catch (Exception ignored) {} });
            tabs.clear();
        }
    }

    private static void validateTabIndex(int index) {
        List<Page> tabs = tabsTL.get();
        if (tabs == null || index < 0 || index >= tabs.size()) {
            int size = (tabs == null) ? 0 : tabs.size();
            throw new IndexOutOfBoundsException(
                    "Tab index " + index + " is out of range. Open tabs: " + size);
        }
    }
}
