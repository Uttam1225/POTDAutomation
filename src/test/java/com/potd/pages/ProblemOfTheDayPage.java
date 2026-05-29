package com.potd.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.potd.utils.ConfigReader;
import com.potd.utils.PlaywrightFactory;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Page Object for the GeeksForGeeks Problem of the Day (POTD) page.
 *
 * <h3>New-tab behaviour</h3>
 * Clicking "Solve Problem" opens the problem's coding page in a <strong>new
 * browser tab</strong> (via {@code target="_blank"}). This class handles that
 * automatically using Playwright's {@code BrowserContext.waitForPage()} API,
 * which intercepts the new page before it navigates so nothing is missed.
 * The new tab is also registered with {@link PlaywrightFactory} so it can be
 * managed (switched to / closed) through the normal tab-management API.
 *
 * <h3>PageFactory pattern</h3>
 * All UI elements are stored as {@link Locator} fields and initialised in the
 * constructor when {@code PageFactory.initPage(ProblemOfTheDayPage.class, page)}
 * is called — mirroring Selenium's {@code PageFactory.initElements()}.
 */
public class ProblemOfTheDayPage extends BasePage {

    // -----------------------------------------------------------------------
    // Locators — initialised in constructor (PageFactory pattern)
    // -----------------------------------------------------------------------

    /**
     * "Solve Problem" button / link on the POTD banner.
     * Text-based selector is used because GFG's CSS class names are hashed
     * (Next.js CSS Modules), making them unstable across builds.
     */
    private final Locator solveProblemButton;

    /** POTD banner — used to confirm the page has fully hydrated. */
    private final Locator potdBanner;

    /** Today's problem title displayed in the banner. */
    private final Locator problemTitle;

    /** Difficulty badge (Easy / Medium / Hard). */
    private final Locator problemDifficulty;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public ProblemOfTheDayPage(Page page) {
        super(page);

        // "Solve Problem" — matches both <a> and <button> variants
        solveProblemButton = page.locator(
                "a:has-text('Solve Problem'), button:has-text('Solve Problem')").first();

        // POTD banner container (id set by GFG for their tour system)
        potdBanner = page.locator("#potdTourStep1");

        // Problem title inside the banner heading
        problemTitle = page.locator("[class*='potd_banner_heading'], [class*='problem_title']").first();

        // Difficulty tag — class names contain 'difficulty' (case-insensitive via two variants)
        problemDifficulty = page.locator(
                "[class*='difficulty']:not([class*='container']):not([class*='wrapper'])").first();
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

    /**
     * Navigates to the POTD page and waits until the page is fully interactive.
     * Explicit waits:
     *   1. POTD banner VISIBLE (20 s) — confirms React has hydrated the page
     *   2. "Solve Problem" button VISIBLE (15 s) — confirms the CTA is ready to click
     */
    public void navigateToPOTD() {
        navigateTo(ConfigReader.getProperty("baseUrl"));
        // Wait for the POTD banner — present in SSR HTML (confirms page started rendering)
        potdBanner.waitFor(new Locator.WaitForOptions()
                .setTimeout(20_000)
                .setState(WaitForSelectorState.VISIBLE));
        solveProblemButton.waitFor(new Locator.WaitForOptions()
                .setTimeout(15_000)
                .setState(WaitForSelectorState.VISIBLE));
        // Wait for React hydration to finish so the full header (Sign In button) is ready
        page.waitForLoadState(LoadState.NETWORKIDLE,
                new Page.WaitForLoadStateOptions().setTimeout(30_000));

        // Dismiss any overlays (GFG tour, cookie banners) that block the button
        dismissOverlays();

        // Confirm Solve Problem button is still visible after overlays cleared
        solveProblemButton.waitFor(new Locator.WaitForOptions()
                .setTimeout(10_000)
                .setState(WaitForSelectorState.VISIBLE));
    }

    /**
     * Dismisses the GFG guided tour and any other overlays using JavaScript clicks,
     * which bypass Playwright's actionability checks (overlay-on-overlay issues).
     * Loops up to 10 times with waits to handle multi-step tours.
     */
    private void dismissOverlays() {
        // Use JS to find and click Skip/close buttons — bypasses actionability checks
        for (int i = 0; i < 10; i++) {
            Boolean dismissed = (Boolean) page.evaluate(
                "() => {" +
                "  var btns = Array.from(document.querySelectorAll('button, [role=\"button\"]'));" +
                "  var skip = btns.find(function(b) { return b.textContent.trim() === 'Skip'; });" +
                "  if (skip && skip.offsetParent !== null) { skip.click(); return true; }" +
                "  var accept = btns.find(function(b) { return /^(Accept|Got it|Accept All|Close)$/i.test(b.textContent.trim()); });" +
                "  if (accept && accept.offsetParent !== null) { accept.click(); return true; }" +
                "  var close = document.querySelector('[class*=\"modal\"] [aria-label=\"Close\"], [class*=\"popup\"] [aria-label=\"Close\"], [class*=\"close_btn\"], [class*=\"closeBtn\"]');" +
                "  if (close && close.offsetParent !== null) { close.click(); return true; }" +
                "  return false;" +
                "}"
            );
            if (Boolean.TRUE.equals(dismissed)) {
                System.out.println("[POTD] Dismissed overlay via JS (pass " + (i + 1) + ")");
                page.waitForTimeout(600);
            } else {
                break; // Nothing left to dismiss
            }
        }

        // Wait for any tour step indicator to disappear (e.g. "1/3", "2/3")
        try {
            page.waitForFunction(
                "() => {" +
                "  var text = document.body ? document.body.innerText : '';" +
                "  return !/\\b[123]\\/3\\b/.test(text);" +
                "}",
                null,
                new Page.WaitForFunctionOptions().setTimeout(5_000)
            );
        } catch (Exception ignored) {}
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    /**
     * Clicks the "Solve Problem" button and fully handles the new browser tab.
     *
     * <h3>Step-by-step</h3>
     * <ol>
     *   <li><b>Intercept</b> — {@code BrowserContext.waitForPage()} registers a
     *       listener on the context <em>before</em> the click fires, so the new
     *       tab is captured the instant the browser creates it (no race condition).</li>
     *   <li><b>Wait — DOMContentLoaded</b> — the HTML is parsed and the DOM is
     *       ready; inline scripts have run.</li>
     *   <li><b>Wait — NetworkIdle</b> — no more than 0 in-flight network requests
     *       for 500 ms; all async resources (JS bundles, API calls) are settled.
     *       Important for GFG's React SPA where the problem editor hydrates after
     *       the initial HTML.</li>
     *   <li><b>Switch control</b> — {@code registerTab()} adds the tab to
     *       PlaywrightFactory's list, sets it as the active tab, and calls
     *       {@code bringToFront()} so the browser window visually focuses it.</li>
     * </ol>
     *
     * @return the {@link Page} of the newly opened problem-editor tab,
     *         which is also set as the active tab in {@link PlaywrightFactory}
     */
    public Page clickSolveProblem() {
        System.out.println("[POTD] Clicking 'Solve Problem' — awaiting new tab...");

        // Debug screenshot before click — shows what is on-screen
        try {
            java.nio.file.Files.createDirectories(
                    java.nio.file.Paths.get("target/screenshots"));
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get(
                            "target/screenshots/debug_before_solve_click.png")));
            System.out.println("[POTD] Pre-click screenshot saved.");
        } catch (Exception ignored) {}

        // Dismiss any overlays (tour popup, cookie banners) that may have appeared
        // AFTER navigateToPOTD() completed — tour shows with a slight delay
        dismissOverlays();

        // Confirm the tour is gone and button is clickable
        try {
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get(
                            "target/screenshots/debug_after_dismiss.png")));
            System.out.println("[POTD] Post-dismiss screenshot saved.");
        } catch (Exception ignored) {}

        // ── Step 1: Intercept the browser-spawned tab ──────────────────────
        // waitForPage() arms the listener BEFORE the click so the new Page
        // is captured before any navigation occurs (no race condition).
        Page problemTab = page.context().waitForPage(
                new com.microsoft.playwright.BrowserContext.WaitForPageOptions()
                        .setTimeout(30_000),
                () -> {
                    try {
                        // force=true bypasses "covered by another element" check —
                        // GFG occasionally shows a translucent overlay on the banner.
                        solveProblemButton.click(
                                new Locator.ClickOptions().setForce(true).setTimeout(10_000));
                    } catch (Exception e) {
                        System.out.println("[POTD] Forced click failed, retrying via JS: " + e.getMessage());
                        // Fallback: dispatch a native click via JS, which bypasses all
                        // actionability checks and always fires regardless of overlays.
                        page.evaluate(
                            "() => {" +
                            "  var el = document.querySelector(\"a:has-text('Solve Problem'), button:has-text('Solve Problem')\");" +
                            "  if (el) el.click();" +
                            "}"
                        );
                    }
                }
        );
        System.out.println("[POTD] New tab intercepted — initial URL: " + problemTab.url());

        // ── Step 2: Wait for DOMContentLoaded ──────────────────────────────
        // HTML is parsed and synchronous scripts have run.
        problemTab.waitForLoadState(LoadState.DOMCONTENTLOADED,
                new Page.WaitForLoadStateOptions().setTimeout(30_000));
        System.out.println("[POTD] DOMContentLoaded — URL: " + problemTab.url());

        // ── Step 3: Wait for NetworkIdle ────────────────────────────────────
        // No in-flight requests for 500 ms — React bundles and API calls done.
        problemTab.waitForLoadState(LoadState.NETWORKIDLE,
                new Page.WaitForLoadStateOptions().setTimeout(30_000));
        System.out.println("[POTD] NetworkIdle — editor page settled: " + problemTab.url());

        // ── Step 4: Switch control to new tab ────────────────────────────────
        // Monaco/editor detection is deferred to CodingPage.waitForEditorInitialized()
        // so ProblemOfTheDayPage has no knowledge of editor internals.
        PlaywrightFactory.registerTab(problemTab);

        // Debug snapshot — helps identify the actual editor DOM on first run
        try {
            problemTab.screenshot(new Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get(
                            "target/screenshots/debug_problem_tab.png")));
            System.out.println("[POTD] Debug screenshot saved: target/screenshots/debug_problem_tab.png");
        } catch (Exception ignored) {}

        return problemTab;
    }

    // -----------------------------------------------------------------------
    // State queries
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when the POTD banner is visible on the page.
     */
    public boolean isPOTDBannerVisible() {
        return potdBanner.isVisible();
    }

    /**
     * Returns the text of today's problem title.
     */
    public String getProblemTitle() {
        problemTitle.waitFor(new Locator.WaitForOptions()
                .setTimeout(10_000)
                .setState(WaitForSelectorState.VISIBLE));
        return problemTitle.textContent().trim();
    }

    /**
     * Returns the difficulty label of today's problem (e.g., "Easy", "Medium", "Hard").
     */
    public String getProblemDifficulty() {
        problemDifficulty.waitFor(new Locator.WaitForOptions()
                .setTimeout(10_000)
                .setState(WaitForSelectorState.VISIBLE));
        return problemDifficulty.textContent().trim();
    }
}
