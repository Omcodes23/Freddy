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

            String body = """
            {
              "model": "llama3.2",
              "prompt": "%s",
              "stream": false
            }
            """.formatted(prompt.replace("\"", "\\\""));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            );

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            // VERY SIMPLE PARSE (safe enough here)
            String json = response.toString();
            int start = json.indexOf("\"response\":\"") + 12;
            int end = json.indexOf("\",\"done\"");
            return json.substring(start, end);

        } catch (Exception e) {
            e.printStackTrace();
            return "⚠️ Freddy is thinking too hard...";
        }
    }
}
