package io.github.querygenerator;

import org.json.JSONArray;
import org.json.JSONObject;


public class MongoSearchStatementFixer {
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

        return query;
    }

}
