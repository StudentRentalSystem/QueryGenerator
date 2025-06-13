package io.github.querygenerator;

import org.json.JSONException;
import org.json.JSONObject;

public class Utils {
    public static JSONObject getStringJSON(String str) throws JSONException {
        int start = str.indexOf("{");
        int end = str.lastIndexOf("}");
        if (start == -1 || end == -1) {
            return null;
        }
        str = str.substring(start, end + 1);
        return new JSONObject(str);
    }
}
