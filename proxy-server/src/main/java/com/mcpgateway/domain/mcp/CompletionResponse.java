package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record CompletionResponse(
    List<String> values,
    Integer total,
    Boolean hasMore
) {
    public static CompletionResponse from(JsonObject json) {
        List<String> vals = new ArrayList<>();
        JsonArray arr = json.getJsonArray("values");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                vals.add(arr.getString(i));
            }
        }
        return new CompletionResponse(
            vals,
            json.getInteger("total"),
            json.getBoolean("hasMore"));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        json.put("values", arr);
        if (total != null) json.put("total", total);
        if (hasMore != null) json.put("hasMore", hasMore);
        return json;
    }
}
