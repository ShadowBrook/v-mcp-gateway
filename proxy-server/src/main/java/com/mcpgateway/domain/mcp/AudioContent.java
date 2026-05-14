package com.mcpgateway.domain.mcp;

public record AudioContent(String type, String data, String mimeType) implements Content {
    public static final String TYPE = "audio";

    public static AudioContent of(String base64Data, String mimeType) {
        return new AudioContent(TYPE, base64Data, mimeType);
    }
}
