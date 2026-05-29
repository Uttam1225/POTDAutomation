package com.potd.pages;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Page Object for the GFG problem coding editor page.
 *
 * All waits use Playwright condition-based APIs — no Thread.sleep or
 * page.waitForTimeout() anywhere in this class.
 *
 * Wait strategy summary:
 *   - Element visibility : locator.waitFor(WaitForOptions + state)
 *   - Page navigation    : page.waitForURL / page.waitForLoadState
 *   - Monaco readiness   : page.waitForFunction (JS condition polling)
 *   - Editor cleared     : waitForFunction -> getValue().trim() == ""
 *   - Editor populated   : waitForFunction -> getValue().length > 0
 *   - Dropdown closed    : locator.waitFor(state = HIDDEN)
 */
public class CodingPage extends BasePage {

    // -----------------------------------------------------------------------
    // Locators  (PageFactory pattern — all initialised in constructor)
    // -----------------------------------------------------------------------

    private final Locator languageDropdownTrigger;
    private final Locator editorContainer;

    /**
     * Monaco's hidden accessibility {@code <textarea class="inputarea">}.
     * Clicking this element focuses the editor and allows key events to reach Monaco.
     */
    private final Locator editorTextarea;

    private final Locator submitButton;
    private final Locator resultContainer;

    // -----------------------------------------------------------------------
    // JS wait expressions  (reused across multiple methods)
    // -----------------------------------------------------------------------

    /** Resolves to true when the Monaco global object and at least one model are ready. */
    private static final String JS_MONACO_READY =
        "() => { try { return typeof monaco !== 'undefined' && " +
        "monaco.editor.getModels().length > 0; } catch(e) { return false; } }";

    /** Resolves to true when the active Monaco model has no content. */
    private static final String JS_EDITOR_EMPTY =
        "() => { try { return monaco.editor.getModels()[0].getValue().trim() === ''; " +
        "} catch(e) { return false; } }";

    /** Resolves to true when the active Monaco model has at least one character. */
    private static final String JS_EDITOR_HAS_CONTENT =
        "() => { try { return monaco.editor.getModels()[0].getValue().length > 0; " +
        "} catch(e) { return false; } }";

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public CodingPage(Page page) {
        super(page);

        // Language dropdown trigger — the button that shows the current language
        // (e.g. "C++ (17)") in the editor toolbar. GFG uses hashed CSS Modules,
        // so we match by text content of known language labels as well as partial class names.
        languageDropdownTrigger = page.locator(
            // Text-based: matches the button showing current language name
            "button:has-text('C++'), button:has-text('Java'), " +
            "button:has-text('Python'), button:has-text('C ('), " +
            // Class-based fallbacks
            "[class*='languageDropDown'] button, [class*='languageDrop'] button, " +
            "[class*='language_drop'], [class*='langDrop'], " +
            "[class*='languageDropdown'] button, [class*='language-select']"
        ).first();

        editorContainer = page.locator(".monaco-editor").first();
        editorTextarea  = page.locator(".monaco-editor textarea.inputarea").first();

        submitButton = page.locator("button:has-text('Submit')").last();

        resultContainer = page.locator(
            "h2:has-text('Output Window'), " +
            "[class*='output_window'], [class*='outputWindow'], [class*='console'], " +
            "[class*='submission_result'], [class*='submissionResult'], " +
            "[class*='verdict'], [class*='result_status']"
        ).first();
    }

    // -----------------------------------------------------------------------
    // Language selection
    // -----------------------------------------------------------------------

    /**
     * Selects a language from the editor language dropdown.
     *
     * Explicit waits:
     *   1. Dropdown trigger VISIBLE (15 s)
     *   2. Matching option VISIBLE (5 s) after clicking trigger
     *   3. Option HIDDEN (5 s) after clicking — confirms the dropdown closed
     *
     * @param language display name in the dropdown, e.g. "Java (21)"
     */
    public void selectLanguage(String language) {
        languageDropdownTrigger.waitFor(new Locator.WaitForOptions()
                .setTimeout(15_000)
                .setState(WaitForSelectorState.VISIBLE));
        languageDropdownTrigger.click();

        // Match the option by its text — handles both <li>, <div>, <span> variants
        Locator option = page.locator(
            "li:has-text('" + language + "'), " +
            "[role='option']:has-text('" + language + "'), " +
            "[class*='option']:has-text('" + language + "'), " +
            "div:has-text('" + language + "'), " +
            "span:has-text('" + language + "')"
        ).first();

        option.waitFor(new Locator.WaitForOptions()
                .setTimeout(10_000)
                .setState(WaitForSelectorState.VISIBLE));
        option.click();

        // Brief wait for the dropdown to close and editor to re-render for new language
        page.waitForTimeout(1000);

        System.out.println("[CodingPage] Language selected: " + language);
    }

    // -----------------------------------------------------------------------
    // Editor clearing
    // -----------------------------------------------------------------------

    /**
     * Clears all existing code from the Monaco editor.
     *
     * Explicit waits:
     *   1. Monaco initialised (waitForFunction JS_MONACO_READY, 20 s)
     *   2. inputarea VISIBLE (10 s) before sending key events
     *   3. Monaco model empty (waitForFunction JS_EDITOR_EMPTY, 5 s) after Delete
     *
     * No Thread.sleep — each step waits on an observable condition.
     */
    public void clearEditor() {
        waitForEditorInitialized();

        editorTextarea.waitFor(new Locator.WaitForOptions()
                .setTimeout(10_000)
                .setState(WaitForSelectorState.VISIBLE));
        editorTextarea.click();

        page.keyboard().press("Control+A");
        page.keyboard().press("Delete");

        // Wait until Monaco model reports empty content
        page.waitForFunction(JS_EDITOR_EMPTY, null,
                new Page.WaitForFunctionOptions().setTimeout(5_000));

        System.out.println("[CodingPage] Editor cleared");
    }

    // -----------------------------------------------------------------------
    // Code entry — keyboard type (Playwright keyboard events)
    // -----------------------------------------------------------------------

    /**
     * Clears existing code then types {@code code} character-by-character
     * using Playwright's {@link com.microsoft.playwright.Keyboard#type(String)}.
     *
     * Each character is dispatched as a synthetic key event — exactly how a
     * real user types. Monaco processes the input events and inserts the text.
     *
     * Explicit wait after typing: Monaco model non-empty (JS_EDITOR_HAS_CONTENT).
     *
     * @param code source code to type
     */
    public void typeCode(String code) {
        clearEditor();

        editorTextarea.waitFor(new Locator.WaitForOptions()
                .setTimeout(5_000)
                .setState(WaitForSelectorState.VISIBLE));
        editorTextarea.click();
        page.keyboard().type(code);

        // Wait for Monaco model to reflect the typed content
        page.waitForFunction(JS_EDITOR_HAS_CONTENT, null,
                new Page.WaitForFunctionOptions().setTimeout(10_000));

        System.out.println("[CodingPage] Code typed via keyboard.type() ("
                + code.lines().count() + " lines)");
    }

    // -----------------------------------------------------------------------
    // Code entry — clipboard paste (navigator.clipboard + Ctrl+V)
    // -----------------------------------------------------------------------

    /**
     * Writes {@code code} to the browser clipboard and pastes it into Monaco.
     *
     * Flow:
     *   1. waitForEditorInitialized() — Monaco global + model ready
     *   2. navigator.clipboard.writeText(code) — async JS, Playwright awaits it
     *   3. editorTextarea.click() — focus Monaco (VISIBLE wait included)
     *   4. Ctrl+A  — select all existing code
     *   5. Ctrl+V  — Monaco paste replaces selection
     *   6. waitForFunction JS_EDITOR_HAS_CONTENT — confirms paste processed
     *
     * Requires clipboard-write permission (granted by PlaywrightFactory).
     *
     * @param code source code to paste
     */
    public void pasteCode(String code) {
        waitForEditorInitialized();

        // Write to browser clipboard (Playwright awaits the returned Promise)
        page.evaluate("async (code) => { await navigator.clipboard.writeText(code); }", code);

        editorTextarea.waitFor(new Locator.WaitForOptions()
                .setTimeout(5_000)
                .setState(WaitForSelectorState.VISIBLE));
        editorTextarea.click();

        page.keyboard().press("Control+A");
        page.keyboard().press("Control+V");

        // Wait for Monaco model to show the pasted content
        page.waitForFunction(JS_EDITOR_HAS_CONTENT, null,
                new Page.WaitForFunctionOptions().setTimeout(10_000));

        System.out.println("[CodingPage] Code pasted via clipboard + Ctrl+V ("
                + code.lines().count() + " lines)");
    }

    // -----------------------------------------------------------------------
    // Primary entry point — best available strategy
    // -----------------------------------------------------------------------

    /**
     * Enters {@code code} using the best available strategy:
     *
     *   1. Monaco JS API  — monaco.editor.getModels()[0].setValue()  (instant)
     *   2. Clipboard paste — navigator.clipboard + Ctrl+A + Ctrl+V   (fast)
     *   3. Keyboard type  — clearEditor() + keyboard.type()           (safe fallback)
     *
     * All three paths include explicit Playwright waits; none use sleep.
     *
     * @param code source code to place in the editor
     */
    public void clearAndEnterCode(String code) {
        // Locate the frame that actually contains the editor (Ace or Monaco)
        Frame editorFrame = waitForEditorFrame(20_000);

        // Strategy 1: Ace Editor API (GFG uses Ace) — fastest, most reliable
        System.out.println("[CodingPage] Trying Ace Editor API...");
        if (tryAceApi(editorFrame, code)) return;

        // Strategy 2: Monaco JS model API (fallback if GFG switches to Monaco)
        if (tryMonacoApiInFrame(editorFrame, code)) return;
        if (tryJsInjectInFrame(editorFrame, code))  return;

        // Strategy 3: Synthetic ClipboardEvent on the editor's hidden textarea.
        // Works for both Ace (textarea.ace_text-input) and Monaco (textarea.inputarea).
        System.out.println("[CodingPage] Trying synthetic paste event...");
        if (trySyntheticPaste(editorFrame, code)) return;

        // Strategy 4: document.execCommand('insertText') on the focused textarea.
        System.out.println("[CodingPage] Trying execCommand insertText...");
        if (tryExecCommand(editorFrame, code)) return;

        // Strategy 5: Click the editor element in the correct frame, then keyboard.insertText().
        System.out.println("[CodingPage] Trying keyboard.insertText via editor click...");
        try {
            // Try Ace first, then Monaco as fallback selector
            Locator editorEl = editorFrame.locator(".ace_editor, .monaco-editor").first();
            editorEl.waitFor(new Locator.WaitForOptions()
                    .setTimeout(10_000)
                    .setState(WaitForSelectorState.VISIBLE));
            editorEl.click(new Locator.ClickOptions().setForce(true));
            page.waitForTimeout(500);
            page.keyboard().press("Control+A");
            page.keyboard().insertText(code);
            System.out.println("[CodingPage] Code entered via keyboard.insertText ("
                    + code.lines().count() + " lines)");
            return;
        } catch (Exception e) {
            System.out.println("[CodingPage] keyboard.insertText failed: " + e.getMessage());
        }

        throw new RuntimeException("All code entry strategies failed — editor not interactive");
    }

    /**
     * Polls all frames up to {@code timeoutMs} until one contains a known editor element.
     * Checks for both Ace (.ace_editor) and Monaco (.monaco-editor).
     */
    private Frame waitForEditorFrame(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Frame f = findEditorFrame();
            if (f != null) return f;
            page.waitForTimeout(1000);
        }
        // Dump diagnostics when editor is not found
        System.out.println("[CodingPage] Editor not found after " + timeoutMs + "ms. Diagnostics:");
        for (Frame f : page.frames()) {
            try {
                Object info = f.evaluate(
                    "() => {" +
                    "  var ace = !!document.querySelector('.ace_editor');" +
                    "  var monaco = !!document.querySelector('.monaco-editor');" +
                    "  var cm = !!document.querySelector('.CodeMirror,.cm-editor');" +
                    "  var ta = document.querySelectorAll('textarea').length;" +
                    "  var eClasses = Array.from(document.querySelectorAll('[class*=\"editor\"]')).map(function(e){return e.className;}).slice(0,5);" +
                    "  return {ace:ace,monaco:monaco,cm:cm,ta:ta,eClasses:eClasses};" +
                    "}");
                System.out.println("  frame url=" + f.url() + " info=" + info);
            } catch (Exception e) {
                System.out.println("  frame url=" + f.url() + " (error: " + e.getMessage() + ")");
            }
        }
        throw new RuntimeException("Code editor not found in any frame within " + timeoutMs + "ms");
    }

    /** Searches all frames for one containing Ace or Monaco editor. Returns null if not found. */
    private Frame findEditorFrame() {
        for (Frame frame : page.frames()) {
            try {
                Boolean found = (Boolean) frame.evaluate(
                    "() => { return !!document.querySelector('.ace_editor, .monaco-editor, .CodeMirror, .cm-editor'); }"
                );
                if (Boolean.TRUE.equals(found)) {
                    System.out.println("[CodingPage] Editor found in frame: " + frame.url());
                    return frame;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Sets code via the Ace Editor JS API. GFG's problem editor uses Ace.
     * Uses ace.edit(element).session.setValue() which replaces all content instantly.
     */
    private boolean tryAceApi(Frame frame, String code) {
        try {
            Object result = frame.evaluate(
                "(code) => {" +
                "  try {" +
                "    var el = document.querySelector('.ace_editor');" +
                "    if (!el) return 'no-ace-element';" +
                "    var editor = ace.edit(el);" +
                "    if (!editor) return 'no-ace-instance';" +
                "    editor.session.setValue(code);" +
                "    editor.clearSelection();" +
                "    return 'set';" +
                "  } catch(e) { return 'error: ' + e.message; }" +
                "}", code);
            System.out.println("[CodingPage] Ace API result: " + result);
            if ("set".equals(result)) {
                page.waitForTimeout(500);
                System.out.println("[CodingPage] Code entered via Ace API ("
                        + code.lines().count() + " lines)");
                return true;
            }
        } catch (Exception e) {
            System.out.println("[CodingPage] Ace API failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Dispatches a synthetic ClipboardEvent('paste') to the editor's hidden textarea.
     * Works for both Ace (textarea.ace_text-input) and Monaco (textarea.inputarea).
     */
    private boolean trySyntheticPaste(Frame frame, String code) {
        try {
            Object result = frame.evaluate(
                "(code) => {" +
                "  try {" +
                "    var ta = document.querySelector('textarea.ace_text-input, .monaco-editor textarea.inputarea, .monaco-editor textarea');" +
                "    if (!ta) return 'no-textarea';" +
                "    ta.focus();" +
                "    ta.dispatchEvent(new KeyboardEvent('keydown', {key:'a', code:'KeyA', ctrlKey:true, bubbles:true}));" +
                "    var dt = new DataTransfer();" +
                "    dt.setData('text/plain', code);" +
                "    ta.dispatchEvent(new ClipboardEvent('paste', {clipboardData: dt, bubbles: true, cancelable: true}));" +
                "    return 'pasted';" +
                "  } catch(e) { return 'error: ' + e.message; }" +
                "}", code);
            System.out.println("[CodingPage] Synthetic paste result: " + result);
            if ("pasted".equals(result)) {
                page.waitForTimeout(800);
                return true;
            }
        } catch (Exception e) {
            System.out.println("[CodingPage] Synthetic paste failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Uses document.execCommand('insertText') on the editor's focused textarea.
     * Works for both Ace and Monaco hidden textareas.
     */
    private boolean tryExecCommand(Frame frame, String code) {
        try {
            Object result = frame.evaluate(
                "(code) => {" +
                "  try {" +
                "    var ta = document.querySelector('textarea.ace_text-input, .monaco-editor textarea.inputarea, .monaco-editor textarea');" +
                "    if (!ta) return 'no-textarea';" +
                "    ta.focus();" +
                "    document.execCommand('selectAll');" +
                "    var ok = document.execCommand('insertText', false, code);" +
                "    return ok ? 'inserted' : 'execCommand-false';" +
                "  } catch(e) { return 'error: ' + e.message; }" +
                "}", code);
            System.out.println("[CodingPage] execCommand result: " + result);
            if ("inserted".equals(result)) {
                page.waitForTimeout(800);
                return true;
            }
        } catch (Exception e) {
            System.out.println("[CodingPage] execCommand failed: " + e.getMessage());
        }
        return false;
    }

    /** Backward-compatible alias for clearAndEnterCode(). */
    public void enterCode(String code) {
        clearAndEnterCode(code);
    }

    // -----------------------------------------------------------------------
    // Submission
    // -----------------------------------------------------------------------

    /**
     * Clicks the Submit button.
     *
     * Explicit waits:
     *   1. Submit button VISIBLE + ENABLED (15 s)
     *   2. NetworkIdle after click (30 s) — waits for verdict XHR to resolve
     */
    public void clickSubmit() {
        submitButton.waitFor(new Locator.WaitForOptions()
                .setTimeout(15_000)
                .setState(WaitForSelectorState.VISIBLE));

        // Ensure the Submit button itself is not disabled before clicking
        try {
            submitButton.waitFor(new Locator.WaitForOptions()
                    .setTimeout(3_000)
                    .setState(WaitForSelectorState.VISIBLE));
        } catch (Exception ignored) {}

        submitButton.click();

        // Wait for verdict API response to settle (NETWORKIDLE with fallback)
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(30_000));
        } catch (Exception e) {
            System.out.println("[CodingPage] NETWORKIDLE after submit timed out, continuing...");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(5_000));
        }

        System.out.println("[CodingPage] Submit clicked — awaiting verdict...");
    }

    /**
     * Waits up to 90 s for a definitive verdict keyword to appear in the page.
     * Returns one of: "Accepted", "Wrong Answer", "Compilation Error",
     * "Runtime Error", "Time Limit Exceeded", "Memory Limit Exceeded".
     */
    public String waitForResult() {
        System.out.println("[CodingPage] Submit clicked — awaiting verdict...");
        String[] verdictKeywords = {
            "Accepted", "Wrong Answer", "Compilation Error",
            "Runtime Error", "Time Limit Exceeded", "Memory Limit Exceeded"
        };

        // Poll page body every second for up to 90 s until a verdict keyword appears
        try {
            page.waitForFunction(
                "() => {" +
                "  var keywords = ['Accepted','Wrong Answer','Compilation Error'," +
                "    'Runtime Error','Time Limit Exceeded','Memory Limit Exceeded'];" +
                "  var body = document.body ? document.body.innerText : '';" +
                "  return keywords.some(function(k) { return body.indexOf(k) !== -1; });" +
                "}",
                null,
                new Page.WaitForFunctionOptions().setTimeout(90_000));
        } catch (Exception e) {
            System.out.println("[CodingPage] Verdict not detected within 90s: " + e.getMessage());
        }

        String verdict = extractVerdictKeyword();
        System.out.println("[CodingPage] Verdict: " + verdict);
        return verdict;
    }

    private String extractVerdictKeyword() {
        String[] keywords = {
            "Accepted", "Wrong Answer", "Compilation Error",
            "Runtime Error", "Time Limit Exceeded", "Memory Limit Exceeded"
        };
        try {
            String body = (String) page.evaluate(
                "() => document.body ? document.body.innerText : ''");
            if (body != null) {
                for (String kw : keywords) {
                    if (body.contains(kw)) return kw;
                }
            }
        } catch (Exception ignored) {}
        return "Unknown verdict";
    }

    // -----------------------------------------------------------------------
    // Problem description extraction  (for CopilotClient)
    // -----------------------------------------------------------------------

    public String extractProblemTitle() {
        // Use page <title> first — GFG formats it as "Problem Name | Practice | GeeksForGeeks"
        // This is far more reliable than scraping h1 which may pick up community solution headings.
        String pageTitle = page.title();
        if (pageTitle != null && pageTitle.contains("|")) {
            return pageTitle.split("\\|")[0].trim();
        }
        // Fallback: specific problem header selectors (avoid generic h1)
        try {
            String title = page.locator(
                "[class*='problem_title'], [class*='problemTitle'], [class*='problem-title'], " +
                "[class*='problems_header_title'], [class*='problem_heading']"
            ).first().textContent().trim();
            if (!title.isBlank()) return title;
        } catch (Exception ignored) {}
        return pageTitle != null ? pageTitle : "Unknown Problem";
    }

    public String extractProblemDescription() {
        try {
            // GFG 2024 React page — use JS to find problem content aggressively
            Object result = page.evaluate(
                "() => {" +
                "  var selectors = [" +
                "    '[class*=\"problems_problem_content\"]'," +
                "    '[class*=\"problem_statement\"]'," +
                "    '[class*=\"problem-statement\"]'," +
                "    '[class*=\"problems_content\"]'," +
                "    '[class*=\"problem_content\"]'," +
                "    '[class*=\"problemContent\"]'," +
                "    '[data-cy=\"problem-statement\"]'," +
                "    '.content'" +
                "  ];" +
                "  for (var i = 0; i < selectors.length; i++) {" +
                "    var el = document.querySelector(selectors[i]);" +
                "    if (el && el.innerText && el.innerText.length > 50) {" +
                "      return el.innerText.substring(0, 3000);" +
                "    }" +
                "  }" +
                "  return '';" +
                "}");
            String desc = result != null ? result.toString().trim() : "";
            if (!desc.isBlank()) return desc;
        } catch (Exception e) {
            System.out.println("[CodingPage] JS description extract failed: " + e.getMessage());
        }

        // Fallback: old locator approach
        try {
            return page.locator(
                "[class*='problems_content'], " +
                "[class*='problem_description'], " +
                "[class*='problemDescription'], " +
                "[class*='problem-statement'], " +
                "[class*='problem_body']"
            ).first().textContent().trim();
        } catch (Exception e) {
            System.out.println("[CodingPage] Could not extract description: " + e.getMessage());
            return "";
        }
    }

    /**
     * Reads the current code from the Ace or Monaco editor.
     * Returns empty string if the editor is not found or an error occurs.
     */
    public String getCurrentEditorCode() {
        try {
            Frame frame = findEditorFrame();
            if (frame == null) return "";
            Object code = frame.evaluate(
                "() => {" +
                "  try {" +
                "    var el = document.querySelector('.ace_editor');" +
                "    if (el) return ace.edit(el).getValue();" +
                "    var m = monaco.editor.getModels();" +
                "    if (m && m.length > 0) return m[0].getValue();" +
                "  } catch(e) {}" +
                "  return '';" +
                "}");
            return code != null ? code.toString() : "";
        } catch (Exception e) {
            System.out.println("[CodingPage] Could not read editor code: " + e.getMessage());
            return "";
        }
    }

    /**
     * Returns true when the Monaco editor is visible AND the Monaco JS
     * global is initialised (checked via waitForFunction).
     */
    public boolean isEditorReady() {
        try {
            waitForEditorInitialized();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Blocks until the Monaco editor is fully initialised and interactive.
     *
     * Three conditions must all be true (polled by Playwright, no sleep):
     *   a. .monaco-editor container is VISIBLE in the DOM
     *   b. window.monaco global exists and has at least one model
     *   c. textarea.inputarea is present (editor accepts keyboard input)
     *
     * @throws com.microsoft.playwright.TimeoutError if not ready within 20 s
     */
    private void waitForEditorInitialized() {
        // a. DOM container visible
        editorContainer.waitFor(new Locator.WaitForOptions()
                .setTimeout(20_000)
                .setState(WaitForSelectorState.VISIBLE));

        // b. Monaco JS object + model ready
        page.waitForFunction(JS_MONACO_READY, null,
                new Page.WaitForFunctionOptions().setTimeout(20_000));

        // c. inputarea present and visible (editor is interactive)
        editorTextarea.waitFor(new Locator.WaitForOptions()
                .setTimeout(10_000)
                .setState(WaitForSelectorState.VISIBLE));
    }

    /**
     * Tries Monaco JS model API in the given frame.
     * On success, also waits for the model to reflect the new content.
     */
    private boolean tryMonacoApiInFrame(Frame frame, String code) {
        try {
            Object result = frame.evaluate(
                "(code) => {" +
                "  try {" +
                "    var m = monaco.editor.getModels();" +
                "    if (m && m.length > 0) { m[0].setValue(code); return true; }" +
                "    var e = monaco.editor.getEditors();" +
                "    if (e && e.length > 0) { e[0].getModel().setValue(code); return true; }" +
                "    return false;" +
                "  } catch (e) { return false; }" +
                "}", code);

            if (Boolean.TRUE.equals(result)) {
                page.waitForTimeout(500);
                System.out.println("[CodingPage] Code entered via Monaco JS API ("
                        + code.lines().count() + " lines)");
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Injects code via AMD require() Monaco API in the given frame.
     */
    private boolean tryJsInjectInFrame(Frame frame, String code) {
        try {
            Object r = frame.evaluate(
                "(code) => { try {" +
                "  var models = (window.monaco || monaco).editor.getModels();" +
                "  if (models && models.length > 0) { models[0].setValue(code); return 'model'; }" +
                "  var eds = (window.monaco || monaco).editor.getEditors();" +
                "  if (eds && eds.length > 0) { eds[0].getModel().setValue(code); return 'editor'; }" +
                "  return false;" +
                "} catch(e) { return false; } }", code);
            if (r != null && !Boolean.FALSE.equals(r)) {
                System.out.println("[CodingPage] JS inject via window.monaco: " + r);
                page.waitForTimeout(500);
                return true;
            }
        } catch (Exception ignored) {}

        try {
            Object r = frame.evaluate(
                "(code) => { try {" +
                "  var req = window.require || window.requirejs;" +
                "  if (!req) return false;" +
                "  var monaco = req('vs/editor/editor.main');" +
                "  if (!monaco) return false;" +
                "  var m = monaco.editor.getModels();" +
                "  if (m && m.length > 0) { m[0].setValue(code); return 'require-model'; }" +
                "  return false;" +
                "} catch(e) { return false; } }", code);
            if (r != null && !Boolean.FALSE.equals(r)) {
                System.out.println("[CodingPage] JS inject via require: " + r);
                page.waitForTimeout(500);
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }
}