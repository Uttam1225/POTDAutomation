package com.potd.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for the GitHub Copilot Chat completions API.
 *
 * <h3>Setup</h3>
 * Set {@code copilot.api.token} in {@code config.properties} (or export the
 * {@code COPILOT_API_TOKEN} environment variable) to a GitHub Personal Access
 * Token that has the {@code copilot} scope enabled under
 * <a href="https://github.com/settings/tokens">github.com/settings/tokens</a>.
 *
 * <h3>Quick usage</h3>
 * <pre>
 *   String title       = codingPage.extractProblemTitle();
 *   String description = codingPage.extractProblemDescription();
 *   String solution    = CopilotClient.generateJavaSolution(title, description);
 *   codingPage.clearAndEnterCode(solution);
 * </pre>
 */
public class CopilotClient {

    // GitHub Models (azure inference) endpoint — supports regular PATs
    private static final String API_URL =
            "https://models.inference.ai.azure.com/chat/completions";

    // Model to use — gpt-4o gives better quality for competitive programming
    private static final String MODEL = "gpt-4o";

    /** System prompt — instructs the model to return raw code only. */
    private static final String SYSTEM_PROMPT_TEMPLATE =
            "You are an expert competitive programmer specialising in %s. " +
            "Solve the given GFG (GeeksForGeeks) coding problem. " +
            "Rules — follow ALL of these exactly:\n" +
            "  1. Output ONLY raw compilable code — no markdown, no ``` fences, no explanation.\n" +
            "  2. Use the EXACT class name and function signature from the provided boilerplate.\n" +
            "  3. Implement a CORRECT and efficient solution — it must pass ALL test cases on GFG.\n" +
            "  4. Include #include<bits/stdc++.h> and 'using namespace std;' at the top.\n" +
            "  5. Return the FULL file content, not just the function body.\n" +
            "  6. Do NOT change the class name or function signature.\n" +
            "  7. Use modular arithmetic (1e9+7) only if required by the problem.";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CopilotClient() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Generates a C++ 17 solution for the given problem via GitHub Copilot API.
     * Passes the existing editor boilerplate so Copilot uses the correct function signature.
     */
    public static String generateCppSolution(String problemTitle,
                                             String problemDescription,
                                             String boilerplate,
                                             String problemUrl) {
        return callApi(problemTitle, problemDescription, boilerplate, problemUrl, "C++17");
    }

    /** Overload without URL for backward compatibility. */
    public static String generateCppSolution(String problemTitle,
                                             String problemDescription,
                                             String boilerplate) {
        return callApi(problemTitle, problemDescription, boilerplate, "", "C++17");
    }

    /**
     * Asks the model to fix a C++ solution that received a wrong verdict.
     * Provides the existing (wrong) code + verdict so the model knows what to correct.
     */
    public static String generateFixedCppSolution(String problemTitle,
                                                   String problemDescription,
                                                   String wrongSolution,
                                                   String verdict) {
        return callApiWithRetryContext(
                problemTitle, problemDescription, wrongSolution, verdict, "C++17");
    }

    /**
     * Generates a Java 21 solution for the given problem via GitHub Copilot API.
     */
    public static String generateJavaSolution(String problemTitle,
                                              String problemDescription) {
        return callApi(problemTitle, problemDescription, "", "", "Java 21");
    }

    private static String callApi(String problemTitle, String problemDescription,
                                   String boilerplate, String problemUrl, String language) {
        // Build a prioritised list of tokens to try.
        // GITHUB_TOKEN (classic PAT with models scope) works with GitHub Models API.
        // Fine-grained PATs (GFG_COPILOT_API_TOKEN) need explicit 'models' permission.
        String githubToken    = System.getenv("GITHUB_TOKEN");
        String gfgToken       = System.getenv("GFG_COPILOT_API_TOKEN");
        String configToken    = ConfigReader.getProperty("copilot.api.token");

        String token = null;
        // Prefer GITHUB_TOKEN first — known to have models scope
        for (String c : new String[]{ githubToken, configToken, gfgToken }) {
            if (c != null && !c.isBlank() && !c.startsWith("YOUR_")) {
                token = c;
                break;
            }
        }

        if (token == null) {
            throw new IllegalStateException(
                    "No GitHub token found.\n" +
                    "  Set GITHUB_TOKEN or GFG_COPILOT_API_TOKEN env var (needs 'models' scope),\n" +
                    "  or add copilot.api.token in config.properties.");
        }
        System.out.println("[CopilotClient] Using token starting with: " + token.substring(0, Math.min(10, token.length())));

        String body = buildRequestBody(problemTitle, problemDescription, boilerplate, problemUrl, language);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type",  "application/json")
                    .header("Accept",        "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "GitHub Models API returned HTTP " + response.statusCode() +
                        ".\nBody: " + response.body());
            }

            String solution = extractContent(response.body());
            System.out.println("[CopilotClient] " + language + " solution received (" +
                    solution.lines().count() + " lines):\n" + solution);
            return solution;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to call GitHub Models API: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String callApiWithRetryContext(String problemTitle,
                                                   String problemDescription,
                                                   String wrongSolution,
                                                   String verdict,
                                                   String language) {
        String githubToken = System.getenv("GITHUB_TOKEN");
        String gfgToken    = System.getenv("GFG_COPILOT_API_TOKEN");
        String configToken = ConfigReader.getProperty("copilot.api.token");
        String token = null;
        for (String c : new String[]{ githubToken, configToken, gfgToken }) {
            if (c != null && !c.isBlank() && !c.startsWith("YOUR_")) { token = c; break; }
        }
        if (token == null) throw new IllegalStateException("No GitHub token found.");

        try {
            String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, language);
            ObjectNode root = MAPPER.createObjectNode();
            root.put("model",       MODEL);
            root.put("temperature", 0.2);
            root.put("max_tokens",  2000);
            ArrayNode messages = root.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            String userContent =
                "Problem title: " + problemTitle + "\n\n" +
                "Problem description:\n" + problemDescription + "\n\n" +
                "The following C++ solution got '" + verdict + "' on GFG. " +
                "Find the bug(s) and rewrite a correct solution using the same class/function signature:\n\n" +
                wrongSolution + "\n\n" +
                "Output ONLY the corrected, complete compilable code. No explanation.";
            messages.addObject().put("role", "user").put("content", userContent);
            String body = MAPPER.writeValueAsString(root);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("GitHub Models API returned HTTP " +
                        response.statusCode() + ".\nBody: " + response.body());
            }
            return extractContent(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call GitHub Models API: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the JSON request body for the Chat completions endpoint.
     */
    private static String buildRequestBody(String title, String description,
                                            String boilerplate, String problemUrl,
                                            String language) {
        try {
            String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, language);

            ObjectNode root = MAPPER.createObjectNode();
            root.put("model",       MODEL);
            root.put("temperature", 0.1);
            root.put("max_tokens",  2000);

            ArrayNode messages = root.putArray("messages");

            ObjectNode system = messages.addObject();
            system.put("role",    "system");
            system.put("content", systemPrompt);

            ObjectNode user = messages.addObject();
            user.put("role", "user");
            String userContent = "Problem title: " + title + "\n";
            if (problemUrl != null && !problemUrl.isBlank()) {
                userContent += "Problem URL: " + problemUrl + "\n";
            }
            userContent += "\n";
            if (description != null && description.length() > 30) {
                userContent += "Problem description:\n" + description + "\n\n";
            } else {
                userContent += "(No description scraped — use your knowledge of this GFG problem.)\n\n";
            }
            if (boilerplate != null && !boilerplate.isBlank()) {
                userContent +=
                    "GFG editor boilerplate (keep this exact class and function signature):\n" +
                    boilerplate + "\n\n";
            }
            userContent += "Provide the complete, correct, optimised " + language + " solution. " +
                           "Handle all edge cases and use modular arithmetic (1e9+7) if needed.";
            user.put("content", userContent);

            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Copilot request body", e);
        }
    }

    /**
     * Parses the response JSON and extracts the assistant's message content.
     * Also strips stray markdown code fences that some model responses include.
     */
    private static String extractContent(String responseBody) {
        try {
            JsonNode root    = MAPPER.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException(
                        "Copilot API response contained no choices.\nBody: " + responseBody);
            }

            String content = choices.get(0)
                    .path("message")
                    .path("content")
                    .asText();

            // Strip markdown code fences the model may include despite the prompt
            content = content.replaceAll("(?m)^```[a-zA-Z]*\\s*$", "")
                             .replaceAll("(?m)^```\\s*$", "")
                             .trim();

            if (content.isBlank()) {
                throw new RuntimeException(
                        "Copilot API returned an empty solution.\nBody: " + responseBody);
            }
            return content;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Copilot API response", e);
        }
    }
}
