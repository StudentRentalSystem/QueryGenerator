package io.github.querygenerator;


import org.json.JSONException;
import org.json.JSONObject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import io.github.studentrentalsystem.LLMClient;

import static io.github.querygenerator.Settings.*;
import static io.github.studentrentalsystem.Settings.llama3_8b;
import static io.github.studentrentalsystem.Utils.getStringJSON;


public class MiniRagApp {
    private static String queryPromptTemplate;
    private static String mongoDBQueryPromptTemplate;
    private final boolean stream = false;


    private void parseQueryPrompt() throws IOException {
        InputStream in = MiniRagApp.class.getClassLoader().getResourceAsStream(queryPromptPath);
        if (in == null) {
            throw new FileNotFoundException("Cannot find query_prompt.txt!");
        }
        queryPromptTemplate = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void parseMongoDBQueryPrompt() throws IOException {
        InputStream in = MiniRagApp.class.getClassLoader().getResourceAsStream(mongoDBQueryPromptPath);
        if (in == null) {
            throw new FileNotFoundException("Cannot find mongo_query_prompt.txt!");
        }
        mongoDBQueryPromptTemplate = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    public MiniRagApp() {
        try {
            parseQueryPrompt();
            parseMongoDBQueryPrompt();
        } catch (IOException e) {
            throw new RuntimeException("Read Rag prompt or Query prompt error: " + e.getMessage());
        }
    }

    public String formatQuery(String query) {
        String formattedQueryPrompt = queryPromptTemplate.replace("{query}", query);
        return LLMClient.callLocalModel(formattedQueryPrompt, llama3_8b, "http://localhost:11434/api/generate");
    }


    public String formatMongoDBQuery(String query) {
        String formattedMongoDBQueryPrompt = mongoDBQueryPromptTemplate.replace("{query}", query);

        return LLMClient.callLocalModel(formattedMongoDBQueryPrompt, llama3_8b, "http://localhost:11434/api/generate");
    }


    public String getMongoDBSearchCmd(String query) {
        try {
            query = formatMongoDBQuery(query);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return query;
    }


    public static void main(String[] args) {
        MiniRagApp miniRag = new MiniRagApp();

        String response = "";

        try {
            Scanner scanner = new Scanner(System.in);

            System.out.println("MiniRAG 啟動！請輸入租屋需求（輸入 exit 離開）：");

            while (true) {
                System.out.print("\n請輸入租屋需求：");
                String userQuery = scanner.nextLine();
                if (userQuery.equalsIgnoreCase("exit")) {
                    break;
                }

                // Get mongoDB result
                response = miniRag.getMongoDBSearchCmd(userQuery);

                if (miniRag.stream) continue;

                if (response == null || response.isEmpty()) continue;


                JSONObject responseBody = new JSONObject(response);

                response = responseBody.getString("response");

                response = getStringJSON(response).toString();

                System.out.println(response);

            }

            System.out.println("👋 再見！");
        } catch (Exception e) {
            if (response != null) System.out.println(response);
            e.printStackTrace();
        }
    }


}
