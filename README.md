# 🤖 GFG POTD Automation — Powered by Playwright + Cucumber + GitHub Copilot

> Automatically solves the **GeeksForGeeks Problem of the Day (POTD)** every day using
> browser automation (Playwright) and AI-generated C++ solutions (GitHub Copilot via GitHub Models API).

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [BDD Approach](#-bdd-approach)
- [How It Works — Step by Step](#-how-it-works--step-by-step)
- [Prerequisites](#-prerequisites)
- [Configuration](#-configuration)
- [Running the Automation](#-running-the-automation)
- [Page Object Model (POM)](#-page-object-model-pom)
- [GitHub Copilot Integration](#-github-copilot-integration)
- [Verdict Validation](#-verdict-validation)
- [Screenshots](#-screenshots)
- [Troubleshooting](#-troubleshooting)

---

## 🌟 Overview

This framework automates the full end-to-end flow of solving GFG's daily coding problem:

1. Opens [https://www.geeksforgeeks.org/problem-of-the-day](https://www.geeksforgeeks.org/problem-of-the-day)
2. Logs in with your GFG credentials
3. Dismisses any guided tour popups
4. Clicks **"Solve Problem"** — intercepts the new tab
5. Reads today's problem title, description, and URL
6. Calls **GitHub Models API (gpt-4o)** to generate a correct C++ (17) solution
7. Pastes the solution into the **Ace code editor**
8. Clicks **Submit**
9. Waits for and validates the verdict — asserts **"Accepted"**
10. If the first attempt gets "Wrong Answer", Copilot is asked to fix the solution and retries once

---

## 🛠 Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| **Java** | 11 | Language |
| **Maven** | 3.x | Build & dependency management |
| **Playwright (Java)** | 1.59.0 | Browser automation |
| **Cucumber** | 7.18.0 | BDD framework |
| **JUnit** | 4.13.2 | Test runner |
| **Jackson** | 2.17.1 | JSON parsing for API responses |
| **GitHub Models API** | gpt-4o | AI solution generation |
| **SLF4J** | 2.0.13 | Logging |

---

## 📁 Project Structure

```
POTDAutomation/
│
├── pom.xml                                          # Maven dependencies & build config
│
└── src/
    └── test/
        ├── java/
        │   └── com/potd/
        │       │
        │       ├── base/
        │       │   └── BaseTest.java                # Base test setup/teardown
        │       │
        │       ├── hooks/
        │       │   └── Hooks.java                   # Cucumber Before/After hooks
        │       │                                    # (screenshot on failure, browser lifecycle)
        │       │
        │       ├── pages/                           # Page Object Model classes
        │       │   ├── BasePage.java                # Common Playwright utilities
        │       │   ├── LoginPage.java               # GFG login flow
        │       │   ├── ProblemOfTheDayPage.java     # POTD page + guided tour + new tab
        │       │   └── CodingPage.java              # Code editor + submit + verdict
        │       │
        │       ├── stepdefinitions/                 # Cucumber step definitions
        │       │   ├── GFGPOTDSteps.java            # Main E2E steps (primary)
        │       │   ├── LoginSteps.java              # Login-specific steps
        │       │   └── POTDSteps.java               # POTD-specific steps
        │       │
        │       ├── runner/
        │       │   └── TestRunner.java              # Cucumber JUnit runner
        │       │
        │       └── utils/
        │           ├── ConfigReader.java            # Reads config.properties + env vars
        │           ├── CopilotClient.java           # GitHub Models API (gpt-4o) client
        │           ├── PageFactory.java             # Page object initialisation helper
        │           ├── PlaywrightFactory.java       # Browser/context/page lifecycle
        │           └── PlaywrightManager.java       # Thread-local page management
        │
        └── resources/
            ├── config.properties                    # App configuration (credentials, URL)
            └── features/
                ├── gfg_potd_flow.feature            # ✅ Main E2E feature (runs daily)
                ├── login.feature                    # Login-only scenarios
                ├── potd.feature                     # POTD page scenarios
                └── solve_potd.feature               # Solve flow scenarios
```

---

## 🥒 BDD Approach

This project follows **Behaviour-Driven Development (BDD)** using the **Gherkin** language.
Each user-facing behaviour is written as a **Feature → Scenario → Steps** in `.feature` files,
then backed by Java **Step Definitions**.

### Feature File — `gfg_potd_flow.feature`

```gherkin
@regression @potd @e2e
Feature: GeeksForGeeks POTD End-to-End Flow
  As a GFG user
  I want to open, solve, and submit the Problem of the Day
  So that I can practice daily competitive programming

  @smoke
  Scenario: Complete POTD solve flow — from login to submission
    Given user opens GFG problem of the day page
    When  user logs in using credentials
    Then  user clicks on Solve Problem
    Then  user switches to new tab
    Then  user enters solution code
    And   user submits the solution
```

### Step-by-Step BDD Mapping

| Gherkin Step | Java Method | What Happens |
|---|---|---|
| `Given user opens GFG problem of the day page` | `userOpensGFGProblemOfTheDayPage()` | Browser navigates to `baseUrl` from config |
| `When user logs in using credentials` | `userLogsInUsingCredentials()` | Reads username/password from config, fills login modal |
| `Then user clicks on Solve Problem` | `userClicksOnSolveProblem()` | Dismisses tour popup, clicks button, intercepts new tab |
| `Then user switches to new tab` | `userSwitchesToNewTab()` | Verifies 2 tabs open, brings coding tab to foreground |
| `Then user enters solution code` | `userEntersSolutionCode()` | Calls Copilot API, pastes C++ code into Ace editor |
| `And user submits the solution` | `userSubmitsTheSolution()` | Clicks Submit, waits for verdict, asserts "Accepted" |

### Cucumber Tags

| Tag | Meaning |
|---|---|
| `@regression` | Full regression suite |
| `@potd` | POTD-related tests |
| `@e2e` | End-to-end tests |
| `@smoke` | Quick smoke test (runs in CI) |

Run only smoke tests:
```powershell
mvn test -Dtest=TestRunner -Dcucumber.filter.tags="@smoke"
```

---

## 🔄 How It Works — Step by Step

### Step 1 — Navigate to POTD Page
```
Browser → https://www.geeksforgeeks.org/problem-of-the-day
```
Playwright opens a Chromium browser (headful by default) and navigates to the GFG POTD page.

### Step 2 — Login
- Clicks the **Sign In** button in the GFG header
- Waits for the login modal to appear
- Fills in `username` and `password` from `config.properties`
- Clicks **Login** and waits for the modal to close
- Verifies the URL is no longer the auth page

### Step 3 — Dismiss Guided Tour & Click Solve Problem
- GFG shows a **guided tour popup** ("1/3 Solve today's problem...") after login
- The automation uses **JavaScript `page.evaluate()`** to find and click the Skip button
  (Playwright's regular `.click()` fails due to overlay stacking — JS click bypasses this)
- Loops up to 10 times until the tour is fully dismissed
- Clicks **"Solve Problem"** using `force: true`
- Uses **`BrowserContext.waitForPage()`** to intercept the new tab before it navigates

### Step 4 — Switch to New Tab
- Verifies exactly 2 tabs are open
- Sets the problem editor tab as the active Playwright page
- Waits for `DOMContentLoaded` + `NetworkIdle` so the editor is fully loaded

### Step 5 — Generate & Enter Solution
1. Reads problem **title** from the page `<title>` tag
2. Reads problem **description** via JavaScript DOM scan (multiple selectors tried)
3. Reads **existing boilerplate** from the Ace editor (`ace.edit().getValue()`)
4. Calls **GitHub Models API** (`gpt-4o`) with:
   - Problem title
   - Problem URL (uniquely identifies the problem in training data)
   - Problem description
   - GFG boilerplate (exact class/function signature)
5. Receives clean C++ solution (no markdown fences)
6. Sets it into the editor via **Ace JS API**: `ace.edit(el).session.setValue(code)`

### Step 6 — Submit & Validate
- Clicks the **Submit** button
- Polls `document.body.innerText` every second for up to 90 seconds
  for verdict keywords: `Accepted`, `Wrong Answer`, `Compilation Error`, etc.
- Extracts the verdict keyword
- **Asserts `"Accepted"`**
- If verdict is `"Wrong Answer"`, asks Copilot to **fix the solution** with the failing code
  as context, re-enters the fixed code, and submits again (one retry)

---

## ✅ Prerequisites

1. **Java 11+**
   ```powershell
   java -version
   ```

2. **Maven 3.x**
   ```powershell
   mvn -version
   ```

3. **Playwright browsers** (auto-downloaded on first run via Maven)

4. **GitHub Classic PAT** with `models` scope
   - Go to [https://github.com/settings/tokens](https://github.com/settings/tokens)
   - Generate a **Classic token**
   - Set as `GITHUB_TOKEN` environment variable (see Configuration below)

5. **GFG Account** with valid credentials

---

## ⚙️ Configuration

### `src/test/resources/config.properties`

```properties
# GFG website base URL
baseUrl=https://www.geeksforgeeks.org/problem-of-the-day

# GFG login credentials (do NOT hardcode — use env vars instead)
username=your_gfg_email@example.com
password=your_gfg_password

# GitHub Models API token (optional — prefer GITHUB_TOKEN env var)
copilot.api.token=YOUR_GITHUB_TOKEN
```

> ⚠️ **Never commit real credentials.** Use environment variables instead.

### Environment Variable Resolution Order

`ConfigReader` resolves each key in this priority order:

| Priority | Source | Example |
|---|---|---|
| 1st | Environment variable (`GFG_` prefix) | `GFG_USERNAME`, `GFG_PASSWORD` |
| 2nd | JVM system property (`-D` flag) | `-Dusername=...` |
| 3rd | `config.properties` file | `username=...` |

### Setting the GitHub Token (Windows)

Set once — persists across terminal sessions:
```powershell
[System.Environment]::SetEnvironmentVariable('GITHUB_TOKEN', 'ghp_yourtoken', 'User')
```

Verify it's set:
```powershell
$t = [System.Environment]::GetEnvironmentVariable('GITHUB_TOKEN', 'User')
if ($t) { Write-Host "✅ Token set: $($t.Substring(0,10))..." } else { Write-Host "❌ Not set" }
```

Check token is valid (calls GitHub API):
```powershell
$token = [System.Environment]::GetEnvironmentVariable('GITHUB_TOKEN', 'User')
$headers = @{Authorization="Bearer $token"; "Accept"="application/vnd.github.v3+json"}
$r = Invoke-RestMethod -Uri "https://api.github.com/user" -Headers $headers
Write-Host "✅ Valid — logged in as: $($r.login)"
```

---

## ▶️ Running the Automation

### Run the Full E2E Test (Daily Use)

```powershell
# Load token into current session first
$env:GITHUB_TOKEN = [System.Environment]::GetEnvironmentVariable('GITHUB_TOKEN', 'User')

# Navigate to project and run
cd C:\Users\singhu00\Documents\POTDAutomation
mvn test -Dtest=TestRunner
```

### Run with Tag Filter

```powershell
# Run only smoke tests
mvn test -Dtest=TestRunner -Dcucumber.filter.tags="@smoke"

# Run only E2E tests
mvn test -Dtest=TestRunner -Dcucumber.filter.tags="@e2e"
```

### Clean Build + Run

```powershell
mvn clean test -Dtest=TestRunner
```

### Expected Console Output (Successful Run)

```
[GFGPOTDSteps] Opened: https://www.geeksforgeeks.org/problem-of-the-day
[LoginPage] Login modal opened successfully
[LoginPage] Login submitted — modal closed, page authenticated
[POTD] Dismissed overlay via JS (pass 1)
[POTD] New tab intercepted — initial URL: https://www.geeksforgeeks.org/problems/...
[GFGPOTDSteps] Problem title: <Today's Problem Name>
[CopilotClient] Using token starting with: ghp_...
[CopilotClient] C++17 solution received (N lines)
[CodingPage] Ace API result: set
[CodingPage] Verdict: Accepted
>>> Finished scenario: Complete POTD solve flow [PASSED]
BUILD SUCCESS
```

---

## 🏗 Page Object Model (POM)

All page interactions are encapsulated in **Page Object** classes under `com.potd.pages`.
Pages are initialised via `PageFactory.initPage()` which uses Playwright's `Page` instance.

### `LoginPage.java`
| Method | Description |
|---|---|
| `navigateToLoginPage()` | Navigates to `baseUrl` |
| `clickSignIn()` | Clicks the GFG header Sign In button, waits for auth redirect |
| `login(username, password)` | Fills credentials and submits login form |
| `isLoginSuccessful()` | Returns `true` if no longer on auth page |

### `ProblemOfTheDayPage.java`
| Method | Description |
|---|---|
| `navigateToPOTD()` | Navigates to the POTD page |
| `getProblemTitle()` | Returns the POTD card title |
| `dismissOverlays()` | JS-based guided tour dismissal (loops 10x) |
| `clickSolveProblem()` | Clicks button + intercepts new tab via `waitForPage()` |

### `CodingPage.java`
| Method | Description |
|---|---|
| `extractProblemTitle()` | Gets problem name from `<title>` tag |
| `extractProblemDescription()` | JS-based DOM scan for problem statement |
| `getCurrentEditorCode()` | Reads existing boilerplate from Ace editor |
| `clearAndEnterCode(code)` | Clears editor and sets new code via Ace JS API |
| `clickSubmit()` | Clicks Submit button, waits for response |
| `waitForResult()` | Polls page body for verdict keyword (up to 90s) |

### `BasePage.java`
Common utilities shared by all page objects:
- `waitForSelector(selector)`
- `click(selector)`
- `fill(selector, text)`
- `getText(selector)`

---

## 🤖 GitHub Copilot Integration

### How `CopilotClient.java` Works

1. **Token Resolution** (in priority order):
   - `GITHUB_TOKEN` env var (classic PAT — recommended, has `models` scope)
   - `copilot.api.token` from `config.properties`
   - `GFG_COPILOT_API_TOKEN` env var (fine-grained PAT — needs `models` permission)

2. **API Endpoint**: `https://models.inference.ai.azure.com/chat/completions`
   - This is the **GitHub Models API** — OpenAI-compatible
   - Works with standard GitHub classic PATs

3. **Model**: `gpt-4o` — best quality for competitive programming

4. **Prompt Design**:
   ```
   System: You are an expert competitive programmer in C++17.
           Output raw compilable code only — no markdown, no explanation.
           Keep the EXACT same class name and function signature as the boilerplate.

   User:   Problem title: <title>
           Problem URL:   <url>          ← uniquely identifies the GFG problem
           Problem description: <desc>   ← scraped from the page
           GFG boilerplate:    <code>    ← correct function signature
           Provide the complete, correct, optimised C++17 solution.
   ```

5. **Retry on Wrong Answer**: If attempt 1 fails with `Wrong Answer`, the wrong code +
   verdict is sent back to the model with "find the bug and fix it" — then resubmits.

---

## 🏆 Verdict Validation

After clicking Submit, the automation:

1. Calls `page.waitForFunction()` — polls `document.body.innerText` every ~100ms
2. Waits up to **90 seconds** for any of these keywords to appear:
   - ✅ `Accepted`
   - ❌ `Wrong Answer`
   - ❌ `Compilation Error`
   - ❌ `Runtime Error`
   - ❌ `Time Limit Exceeded`
   - ❌ `Memory Limit Exceeded`
3. Extracts the exact keyword from the page body
4. **JUnit assertion**: `assertEquals("Accepted", verdict)`
5. If not Accepted → Copilot retry → resubmit → assert again

---

## 📸 Screenshots

Screenshots are automatically saved to `target/screenshots/` on every scenario:

| File | When Saved |
|---|---|
| `debug_before_solve_click.png` | Before clicking "Solve Problem" |
| `debug_problem_tab.png` | After new tab opens (shows loaded editor) |
| `debug_before_code_entry.png` | Before pasting solution |
| `Complete_POTD_solve_flow_*.png` | Final state after scenario passes/fails |

---

## 🔧 Troubleshooting

### `GITHUB_TOKEN not set` / `No GitHub token found`
```powershell
$env:GITHUB_TOKEN = [System.Environment]::GetEnvironmentVariable('GITHUB_TOKEN', 'User')
```
Run this before `mvn test` in every new PowerShell session.

### `HTTP 401 — models permission required`
Your token is a fine-grained PAT without `models` scope. Use a **Classic PAT** instead:
- Go to [https://github.com/settings/tokens](https://github.com/settings/tokens)
- Click **Generate new token (classic)**
- No special scopes needed — just generate and set as `GITHUB_TOKEN`

### `Login failed — browser is still on the auth page`
GFG credentials in `config.properties` are wrong or expired. Update `username` and `password`.

### `Verdict: Unknown verdict` (timeout)
GFG's judge took longer than 90 seconds. This is rare — try re-running.

### `Wrong Answer` on both attempts
The Copilot-generated solution is incorrect for this problem. This can happen if:
- The problem description wasn't scraped correctly
- The problem is very new and not in the model's training data
Re-running usually gives a different (correct) solution.

### Browser doesn't open / Playwright error
Playwright browsers may need reinstalling:
```powershell
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
```

---

## 🗂 Key Design Decisions

| Decision | Reason |
|---|---|
| **Playwright over Selenium** | Better async handling, built-in `waitForPage()` for new tabs, more reliable waits |
| **Ace Editor via JS API** | GFG uses Ace editor — `ace.edit(el).session.setValue(code)` is the only reliable entry method |
| **JS-based tour dismissal** | Playwright `.click()` fails on overlays due to actionability checks — `page.evaluate()` bypasses this |
| **GitHub Models API** | Works with classic PATs — no OAuth flow needed unlike `api.githubcopilot.com` |
| **Page title for problem name** | GFG's `<title>` is `"ProblemName | Practice | GeeksForGeeks"` — far more reliable than scraping `h1` which picks up community solution headings |
| **Retry on Wrong Answer** | Provides one self-healing attempt before failing the test |
| **No `Thread.sleep()`** | All waits use Playwright's built-in `waitForFunction`, `waitForSelector`, `waitForLoadState` |

---

## 👤 Author

**Uttam Singh** — [@Uttam1225](https://github.com/Uttam1225)

---

*Built with ❤️ using Playwright, Cucumber BDD, and GitHub Copilot*
