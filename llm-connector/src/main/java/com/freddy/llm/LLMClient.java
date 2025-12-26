package com.freddy.llm;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class LLMClient {

    public static String ask(String prompt) {
        try {
            URL url = new URL("http://localhost:11434/api/generate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Properly escape JSON string
            String escapedPrompt = escapeJson(prompt);
            String body = "{\"model\":\"llama3.2\",\"prompt\":\"" + escapedPrompt + "\",\"stream\":false}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream())
                );
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                errorReader.close();
                System.err.println("[LLMClient] HTTP " + responseCode + ": " + errorResponse.toString());
                return "⚠️ Freddy is thinking too hard...";
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8")
            );

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            // Parse JSON response
            String json = response.toString();
            int start = json.indexOf("\"response\":\"");
            if (start == -1) {
                System.err.println("[LLMClient] Invalid response format: " + json);
                return "⚠️ Freddy is thinking too hard...";
            }
            start += 12;
            int end = json.indexOf("\",\"done\"", start);
            if (end == -1) {
                end = json.lastIndexOf("\"");
            }
            
            String result = json.substring(start, end);
            // Unescape JSON
            result = result.replace("\\n", "\n").replace("\\\"", "\"");
            return result.trim();

        } catch (Exception e) {
            System.err.println("[LLMClient] Error: " + e.getMessage());
            e.printStackTrace();
            return "⚠️ Freddy is thinking too hard...";
        }
    }
    
    /**
     * Escape string for JSON (handles quotes, newlines, backslashes, etc.)
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str
            .replace("\\", "\\\\")  // Backslash
            .replace("\"", "\\\"")  // Quote
            .replace("\n", "\\n")   // Newline
            .replace("\r", "\\r")   // Carriage return
            .replace("\t", "\\t");  // Tab
    }
}
