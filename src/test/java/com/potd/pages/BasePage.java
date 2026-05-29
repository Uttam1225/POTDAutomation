package com.potd.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;

/**
 * Base class for all page objects.
 * Provides common Playwright interactions used across pages.
 */
public abstract class BasePage {

    protected final Page page;

    public BasePage(Page page) {
        this.page = page;
    }

    public void navigateTo(String url) {
        page.navigate(url);
    }

    public void clickElement(String selector) {
        page.click(selector);
    }

    public void fillText(String selector, String text) {
        page.fill(selector, text);
    }

    public String getText(String selector) {
        return page.textContent(selector);
    }

    public boolean isVisible(String selector) {
        return page.isVisible(selector);
    }

    public void waitForSelector(String selector) {
        page.waitForSelector(selector,
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));
    }

    public String getTitle() {
        return page.title();
    }

    public String getCurrentUrl() {
        return page.url();
    }

    public Locator getLocator(String selector) {
        return page.locator(selector);
    }

    public void takeScreenshot(String filePath) {
        page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(filePath)));
    }
}
