package com.potd.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.potd.utils.ConfigReader;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Page Object for the GeeksForGeeks login flow.
 *
 * <h3>Updated login flow (GFG now uses an inline modal — no redirect)</h3>
 * <pre>
 *  1. navigateToLoginPage()  → opens https://www.geeksforgeeks.org/problem-of-the-day
 *  2. clickSignIn()          → clicks the header "Sign In" button
 *                              → GFG opens a modal overlay on the SAME page
 *                              → waits for the modal username input to be visible
 *  3. enterUsername(email)   → fills the modal "Username or Email" input
 *  4. enterPassword(pass)    → fills the modal "Enter password" input
 *  5. clickLogin()           → clicks the modal "Sign In" button
 *                              → waits for modal to close + user to be authenticated
 * </pre>
 */
public class LoginPage extends BasePage {

    // -----------------------------------------------------------------------
    // Header button (triggers the modal)
    // -----------------------------------------------------------------------
    private final Locator signInHeaderButton;

    // -----------------------------------------------------------------------
    // Modal form fields  — these appear after clicking the header Sign In
    // -----------------------------------------------------------------------
    /** "Username or Email" input inside the login modal. */
    private final Locator emailInput;

    /** "Enter password" input inside the login modal. */
    private final Locator passwordInput;

    /** Green "Sign In" submit button inside the modal. */
    private final Locator loginSubmitButton;

    /** Close (X) button on the modal — used to detect modal is open. */
    private final Locator modalCloseButton;

    /** Profile/avatar element visible in the header after login. */
    private final Locator userProfileIcon;

    // -----------------------------------------------------------------------
    // Constructor — PageFactory initialises all Locators here
    // -----------------------------------------------------------------------

    public LoginPage(Page page) {
        super(page);

        // Header "Sign In" button (top-right corner, dark background)
        // Use .first() — the last() was resolving to a hidden mobile-only button
        signInHeaderButton = page.locator("button:has-text('Sign In')").first();

        // Modal form inputs (visible only after modal is open)
        emailInput        = page.locator("input[placeholder='Username or Email']").first();
        passwordInput     = page.locator("input[placeholder='Enter password'], input[type='password']").first();

        // Green "Sign In" submit button inside the modal form
        // Scoped to avoid matching the header button
        loginSubmitButton = page.locator(
            "form button:has-text('Sign In'), " +
            "button.btn-primary:has-text('Sign In'), " +
            "button[type='submit']:has-text('Sign In')"
        ).first();

        // Modal close button (X) — its presence confirms the modal is open
        modalCloseButton = page.locator(
            "button[aria-label='Close'], button.close, " +
            "svg[aria-label='close'], [data-dismiss='modal']"
        ).first();

        // Post-login: profile icon/avatar in the GFG header
        userProfileIcon = page.locator(
            "img[alt*='profile'], img[alt*='Profile'], " +
            ".profilePic, [class*='profile_pic'], " +
            "[class*='profilePic'], [class*='user_img']"
        ).first();
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

    /** Navigates to the GFG Problem-of-the-Day page. */
    public void navigateToLoginPage() {
        navigateTo(ConfigReader.getProperty("baseUrl"));
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    /**
     * Clicks the "Sign In" button in the GFG header.
     *
     * GFG now opens an inline modal on the same page (no redirect to auth.geeksforgeeks.org).
     *
     * Explicit waits:
     *   1. NETWORKIDLE (30 s) — ensures React header is fully hydrated
     *   2. Header Sign In button VISIBLE (30 s)
     *   3. Modal email input VISIBLE (15 s — modal animation + React render)
     *   4. Modal password input VISIBLE (5 s)
     */
    public void clickSignIn() {
        // Wait for React header to render — use DOMCONTENTLOADED which is less strict
        // than NETWORKIDLE and avoids timeouts when GFG has background analytics requests.
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(30_000));
        } catch (Exception e) {
            System.out.println("[LoginPage] NETWORKIDLE timed out, continuing with DOMCONTENTLOADED");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(10_000));
        }

        signInHeaderButton.waitFor(new Locator.WaitForOptions()
                .setTimeout(30_000)
                .setState(WaitForSelectorState.VISIBLE));
        signInHeaderButton.click();

        // Wait for the modal's email input to appear
        emailInput.waitFor(new Locator.WaitForOptions()
                .setTimeout(15_000)
                .setState(WaitForSelectorState.VISIBLE));
        passwordInput.waitFor(new Locator.WaitForOptions()
                .setTimeout(5_000)
                .setState(WaitForSelectorState.VISIBLE));

        System.out.println("[LoginPage] Login modal opened successfully");
    }

    /**
     * Types the username or email address into the modal email field.
     *
     * @param username GFG username or registered email address
     */
    public void enterUsername(String username) {
        emailInput.waitFor(new Locator.WaitForOptions()
                .setTimeout(5_000)
                .setState(WaitForSelectorState.VISIBLE));
        emailInput.fill(username);
    }

    /**
     * Types the password into the modal password field.
     *
     * @param password account password
     */
    public void enterPassword(String password) {
        passwordInput.waitFor(new Locator.WaitForOptions()
                .setTimeout(5_000)
                .setState(WaitForSelectorState.VISIBLE));
        passwordInput.fill(password);
    }

    /**
     * Clicks the green "Sign In" submit button inside the modal.
     *
     * Explicit waits:
     *   1. Submit button VISIBLE (5 s)
     *   2. Email input to become hidden (15 s) — confirms modal closed after login
     *   3. DOMContentLoaded (10 s) — page re-renders after auth
     */
    public void clickLogin() {
        loginSubmitButton.waitFor(new Locator.WaitForOptions()
                .setTimeout(5_000)
                .setState(WaitForSelectorState.VISIBLE));
        loginSubmitButton.click();

        // Wait for modal to close — email input disappears when login succeeds
        emailInput.waitFor(new Locator.WaitForOptions()
                .setTimeout(15_000)
                .setState(WaitForSelectorState.HIDDEN));

        // Allow page to re-render with authenticated state
        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                new Page.WaitForLoadStateOptions().setTimeout(10_000));

        System.out.println("[LoginPage] Login submitted — modal closed, page authenticated");
    }

    /**
     * Convenience method: enters credentials and submits the login form.
     * Assumes the modal is already open (call {@link #clickSignIn()} first).
     *
     * @param username GFG username or email
     * @param password account password
     */
    public void login(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
    }

    // -----------------------------------------------------------------------
    // Assertions / state queries
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when the user is logged in.
     * Checks that the URL is still on GFG and the login modal is gone.
     */
    public boolean isLoginSuccessful() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                new Page.WaitForLoadStateOptions().setTimeout(10_000));
        // Login is successful when: still on GFG AND the Sign In modal input is gone
        boolean onGFG = page.url().contains("geeksforgeeks.org");
        boolean modalGone = !emailInput.isVisible();
        return onGFG && modalGone;
    }

    /**
     * Returns {@code true} when an error message is visible in the modal.
     */
    public boolean isErrorDisplayed() {
        Locator errorAlert = page.locator(
            ".error-message, [class*='error'], " +
            "p:has-text('Invalid'), span:has-text('Invalid'), " +
            "p:has-text('incorrect'), div:has-text('Wrong password')"
        ).first();
        return errorAlert.isVisible();
    }

    public String getErrorMessage() {
        Locator errorAlert = page.locator(
            ".error-message, [class*='error'], " +
            "p:has-text('Invalid'), span:has-text('Invalid'), " +
            "p:has-text('incorrect'), div:has-text('Wrong password')"
        ).first();
        return errorAlert.isVisible() ? errorAlert.textContent().trim() : "";
    }
}