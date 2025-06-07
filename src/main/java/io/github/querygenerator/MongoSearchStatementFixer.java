package io.github.querygenerator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;


public class MongoSearchStatementFixer {

    private static final Map<String, String> districtMap = Map.ofEntries(
            Map.entry("台南市", "台南市"),
            Map.entry("臺南市", "台南市"),
            Map.entry("台南", "台南市"),
            Map.entry("臺南", "台南市"),
            Map.entry("高雄市", "高雄市"),
            Map.entry("台北市", "台北市"),
            Map.entry("臺北市", "台北市")
            // more for extensions
    );

    private static String normalizeDistrict(String location) {
        return districtMap.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))  // 長的優先
                .reduce(location, (loc, key) -> loc.replace(key, districtMap.get(key)));
    }


    public static List<String> splitAddress(String address) {
        List<String> result = new ArrayList<>();
        // 找出所有的界標字（市、區、鄉、鎮、村、路、街、巷、弄）的位置
        List<Integer> splitPositions = new ArrayList<>();
        String delimiters = "市區鄉鎮村路街巷弄";

        for (int i = 0; i < address.length(); i++) {
            if (delimiters.indexOf(address.charAt(i)) >= 0) {
                splitPositions.add(i);
            }
        }

        int start = 0;
        for (int pos : splitPositions) {
            String part = address.substring(start, pos + 1).trim();
            if (!part.isEmpty()) {
                result.add(part);
            }
            start = pos + 1;
        }

        // 如果最後還有剩下的字串（如街道後面還有門牌），也加進來
        if (start < address.length()) {
            String tail = address.substring(start).trim();
            if (!tail.isEmpty()) {
                result.add(tail);
            }
        }

        if (result.isEmpty()) {
            result.add(address);
        }

        return result;
    }



    public static boolean isSinglePlaceName(String address) {
        return address.length() <= 4 && !address.matches(".*[市區路街巷弄].*.*"); // 避免短詞誤判
    }



    public static JSONObject fixAddressQuery(JSONObject query) {
        if (!query.has("$and")) return query;

        JSONArray andArray = query.getJSONArray("$and");
        JSONArray newAndArray = new JSONArray();

        for (int i = 0; i < andArray.length(); i++) {
            JSONObject condition = andArray.getJSONObject(i);

            // 處理地址欄位
            if (condition.has("地址")) {
                JSONObject addrCondition = condition.getJSONObject("地址");

                if (addrCondition.has("$regex")) {
                    String raw = addrCondition.getString("$regex");

                    // 正規化（例：臺南 -> 台南市）
                    raw = normalizeDistrict(raw);

                    // 長地址才拆解
                    if (raw.length() > 4 && raw.matches(".*[市區鄉鎮村路街巷弄]+.*")) {
                        List<String> parts = splitAddress(raw);

                        for (String part : parts) {
                            if (part.length() >= 2) { // 避免拆成「市」、「區」這種短詞
                                JSONObject regex = new JSONObject();
                                regex.put("地址", new JSONObject()
                                        .put("$regex", part.replace("市", ""))
                                        .put("$options", "i"));
                                newAndArray.put(regex);
                            }
                        }
                        continue; // 原條件已拆，不加入
                    }
                }
            }

            // 不是地址，或已簡單處理，原樣加入
            newAndArray.put(condition);
        }

        JSONObject newQuery = new JSONObject();
        newQuery.put("$and", newAndArray);
        return newQuery;
    }




    public static String fixRentalQuery(String query) {
        return fixRentalQuery(new JSONObject(query)).toString();
    }

    public static JSONObject fixRentalQuery(JSONObject query) {
        if (query.has("rental")) {
            Object rentalObj = query.get("rental");
            if (rentalObj instanceof JSONObject) {
                JSONObject rental = (JSONObject) rentalObj;

                if (rental.has("$gte") || rental.has("$lte")) {
                    if (rental.has("$gte")) {
                        query.put("租金.minRental", new JSONObject().put("$gte", rental.getInt("$gte")));
                    }
                    if (rental.has("$lte")) {
                        query.put("租金.maxRental", new JSONObject().put("$lte", rental.getInt("$lte")));
                    }
                    query.remove("rental");
                }

                // 處理 rental.$elemMatch 錯誤格式
                else if (rental.has("$elemMatch")) {
                    JSONObject elemMatch = rental.getJSONObject("$elemMatch");
                    if (elemMatch.has("$and")) {
                        JSONArray conditions = elemMatch.getJSONArray("$and");
                        for (int i = 0; i < conditions.length(); i++) {
                            JSONObject cond = conditions.getJSONObject(i);
                            if (cond.has("minRental")) {
                                query.put("租金.minRental", cond.getJSONObject("minRental"));
                            }
                            if (cond.has("maxRental")) {
                                query.put("租金.maxRental", cond.getJSONObject("maxRental"));
                            }
                        }
                        query.remove("rental");
                    }
                }
            }
        }
        for (String key : query.keySet()) {
            if (key.contains("rental")) {
                String newQuery = query.toString().replace("rental", "租金");
                query = new JSONObject(newQuery);
                break;
            }
        }

        return query;
    }

    public static JSONObject fixQuery(JSONObject query) {
        query = fixRentalQuery(query);
        return fixAddressQuery(query);
    }

}
