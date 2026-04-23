package com.freddy.llm;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class LLMClient {

    private static String model = "qwen2.5:3b";
    private static String ollamaUrl = "http://localhost:11434/api/generate";
    private static int defaultTimeoutMs = 12000;

    /** Allow runtime configuration from plugin config. */
    public static void configure(String modelName, String url, int timeoutMs) {
        if (modelName != null && !modelName.isBlank()) model = modelName;
        if (url != null && !url.isBlank()) ollamaUrl = url;
        if (timeoutMs > 0) defaultTimeoutMs = timeoutMs;
    }

    public static String getModel() { return model; }
    public static String getUrl() { return ollamaUrl; }
    public static int getDefaultTimeout() { return defaultTimeoutMs; }

    public static String ask(String prompt) {
        return ask(prompt, defaultTimeoutMs);
    }

    public static String ask(String prompt, int timeoutMs) {
        return askWithRetry(prompt, timeoutMs, 2);
    }

    /**
     * Ask with automatic retry on transient failures.
     */
    private static String askWithRetry(String prompt, int timeoutMs, int maxRetries) {
        Exception lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String result = doAsk(prompt, timeoutMs);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            } catch (Exception e) {
                lastError = e;
                System.err.println("[LLMClient] Attempt " + (attempt + 1) + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    try { Thread.sleep(500L * (attempt + 1)); } catch (InterruptedException ignored) {}
                }
            }
        }
        System.err.println("[LLMClient] All retries exhausted: " + (lastError != null ? lastError.getMessage() : "null response"));
        return null;
    }

    private static String doAsk(String prompt, int timeoutMs) throws Exception {
        URL url = new URL(ollamaUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        int safeTimeout = Math.max(2000, timeoutMs);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(safeTimeout);

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Properly escape JSON string
        String escapedPrompt = escapeJson(prompt);
        String body = "{\"model\":\"" + model + "\",\"prompt\":\"" + escapedPrompt + "\",\"stream\":false}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            InputStream errorStream = conn.getErrorStream();
            String errorBody = "";
            if (errorStream != null) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream))) {
                    StringBuilder sb = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        sb.append(errorLine);
                    }
                    errorBody = sb.toString();
                }
            }
            throw new IOException("HTTP " + responseCode + ": " + errorBody);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        return parseJsonResponse(response.toString());
    }

    /**
     * Robust JSON response parser that handles the "response" field properly
     * without relying on field ordering or naive indexOf.
     */
    private static String parseJsonResponse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        // Find the "response" key and extract its string value properly
        String key = "\"response\":\"";
        int start = json.indexOf(key);
        if (start == -1) {
            // Try alternate format: "response": " (with space after colon)
            key = "\"response\": \"";
            start = json.indexOf(key);
        }
        if (start == -1) {
            System.err.println("[LLMClient] No 'response' field found in: " + json.substring(0, Math.min(200, json.length())));
            return null;
        }

        start += key.length();

        // Walk character by character to find the end of the JSON string value,
        // properly handling escape sequences
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case '"': value.append('"'); break;
                    case '\\': value.append('\\'); break;
                    case '/': value.append('/'); break;
                    default: value.append('\\').append(c); break;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                // End of JSON string value
                break;
            } else {
                value.append(c);
            }
        }

        String result = value.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Escape string for JSON (handles quotes, newlines, backslashes, etc.)
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str
            .replace("\\", "\\\\")  // Backslash first
            .replace("\"", "\\\"")  // Quote
            .replace("\n", "\\n")   // Newline
            .replace("\r", "\\r")   // Carriage return
            .replace("\t", "\\t");  // Tab
    }
}
