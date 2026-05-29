package com.potd.utils;

import com.microsoft.playwright.Page;
import com.potd.pages.BasePage;

/**
 * PageFactory creates and initialises Page Object instances, injecting the Playwright Page.
 * Mirrors the intent of Selenium's PageFactory.initElements().
 */
public class PageFactory {

    private PageFactory() {}

    /**
     * Instantiates a page object of the given class, injecting the Playwright Page.
     *
     * @param pageClass Class extending BasePage
     * @param page      Active Playwright Page instance
     * @param <T>       Type parameter bounded by BasePage
     * @return Initialised page object
     */
    public static <T extends BasePage> T initPage(Class<T> pageClass, Page page) {
        try {
            return pageClass.getDeclaredConstructor(Page.class).newInstance(page);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise page object: " + pageClass.getName(), e);
        }
    }
}
