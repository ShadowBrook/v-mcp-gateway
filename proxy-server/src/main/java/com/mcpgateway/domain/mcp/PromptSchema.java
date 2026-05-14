package com.mcpgateway.domain.mcp;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record PromptSchema(String name, String description, List<PromptArgument> arguments) {
    public static PromptSchema from(JsonObject json) {
        List<PromptArgument> args = new ArrayList<>();
        JsonArray arr = json.getJsonArray("arguments");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                args.add(PromptArgument.from(arr.getJsonObject(i)));
            }
        }
        return new PromptSchema(
            json.getString("name", ""),
            json.getString("description", ""),
            args);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("name", name)
            .put("description", description);
        if (!arguments.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (PromptArgument a : arguments) {
                arr.add(new JsonObject()
                    .put("name", a.name())
                    .put("description", a.description())
                    .put("required", a.required()));
            }
            json.put("arguments", arr);
        }
        return json;
    }
}
