package io.github.querygenerator;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.github.studentrentalsystem.LLMClient;

import static io.github.querygenerator.Settings.*;
import static io.github.studentrentalsystem.Utils.getStringJSON;


public class MiniRagApp {
    private static String queryPromptTemplate;
    private static String mongoDBQueryPromptTemplate;
    private final boolean stream;


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

    public MiniRagApp(boolean stream) {
        this.stream = stream;
        try {
            parseQueryPrompt();
            parseMongoDBQueryPrompt();
        } catch (IOException e) {
            throw new RuntimeException("Read Rag prompt or Query prompt error: " + e.getMessage());
        }
    }

    public MiniRagApp() {
        this(false);
    }

    public String formatQuery(String query) {
        String formattedQueryPrompt = queryPromptTemplate.replace("{query}", query);
        String response = LLMClient.callLocalModel(formattedQueryPrompt, LLMClient.ModelType.LLAMA3_8B, "http://localhost:11434/api/generate");
        response = response.replace("可", "是否可");
        response = response.replace("有", "是否有");
        return response;
    }


    public String formatMongoDBQuery(String query) {
        String formattedMongoDBQueryPrompt = mongoDBQueryPromptTemplate.replace("{query}", query);

        return LLMClient.callLocalModel(formattedMongoDBQueryPrompt, LLMClient.ModelType.LLAMA3_8B, "http://localhost:11434/api/generate");
    }


    public String getMongoDBSearchCmd(String query) {
        query = formatMongoDBQuery(query);
        query = query.replace("可", "是否可");
        query = query.replace("有", "是否有");
        return query;
    }

    public JSONObject getMongoDBSearchCmdJSON(String query) throws JSONException {
        String response = getMongoDBSearchCmd(query);

        JSONObject responseBody = new JSONObject(response);
        response = responseBody.getString("response");
        return getStringJSON(response);
    }

    public JSONObject getFixedMongoQueryCmd(JSONObject query) throws JSONException {
        if (query == null) return null;
        return MongoSearchStatementFixer.fixQuery(query);
    }


    public static void main(String[] args) {
        MiniRagApp miniRag = new MiniRagApp();

        String response = "";
        JSONObject jsonResponse = null;

        boolean useMongoDB = true;

        JSONObject test = miniRag.getFixedMongoQueryCmd(new JSONObject("{\"rental.minRental\":{\"$gte\":5000},\"rental.maxRental\":{\"$lte\":5000}}"));
        System.out.println(test);

        String testAddress = "{\"$and\":[\n" +
                "  {\"地址\":{\"$regex\":\"台南市東區勝利路\",\"$options\":\"i\"}},\n" +
                "  {\"租金.minRental\":{\"$gte\":5000}},\n" +
                "  {\"租金.maxRental\":{\"$lte\":10000}},\n" +
                "  {\"是否有電梯\":1}\n" +
                "]}";

        System.out.println(MongoSearchStatementFixer.fixAddressQuery(new JSONObject(testAddress)));

        try {
            Scanner scanner = new Scanner(System.in);

            System.out.print("MiniRAG 啟動！請輸入租屋需求（輸入 exit 離開, others 選擇其他服務, rental 選擇租屋服務, 預設為 rental）：");

            while (true) {
                System.out.print(useMongoDB ? "\n請輸入租屋需求：" : "\n請輸入其他需求：");
                String userQuery = scanner.nextLine();

                if (userQuery.equalsIgnoreCase("exit")) {
                    break;
                } else if (userQuery.equalsIgnoreCase("others")) {
                    useMongoDB = false;
                    continue;
                } else if (userQuery.equalsIgnoreCase("rental")) {
                    useMongoDB = true;
                    continue;
                }

                if (useMongoDB) {
                    // Get mongoDB result
                    jsonResponse = miniRag.getMongoDBSearchCmdJSON(userQuery);
                    System.out.println(jsonResponse);
                    if ((jsonResponse = miniRag.getFixedMongoQueryCmd(jsonResponse)) != null) System.out.println(jsonResponse);
                } else {
                    BlockingQueue<LLMClient.StreamData> queue = new LinkedBlockingQueue<>();
                    String finalUserQuery = userQuery;

                    Thread worker = new Thread(() -> {
                        LLMClient.callLocalModel(finalUserQuery, LLMClient.ModelType.LLAMA3_8B, "http://localhost:11434/api/generate", true, queue);
                    });

                    // Main thread to listen token
                    Thread listener = new Thread(() -> {
                        while (true) {
                            try {
                                LLMClient.StreamData data = queue.take(); // blocking until the data exists
                                if (data.token != null) {
                                    System.out.print(data.token);
                                } else {
                                    break;
                                }
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                        System.out.println();
                    });

                    worker.start();
                    listener.start();

                    try {
                        worker.join();
                        listener.join();
                    } catch (InterruptedException e) {
                        System.out.println("⚠️ 執行緒中斷！");
                    }

                }
            }

            System.out.println("👋 再見！");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            if (response != null) System.out.println(response);
            if (jsonResponse != null) System.out.println(jsonResponse);
            e.printStackTrace();
        }
    }


}
