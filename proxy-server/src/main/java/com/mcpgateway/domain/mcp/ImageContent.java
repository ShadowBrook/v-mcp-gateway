package com.mcpgateway.domain.mcp;

public record ImageContent(String type, String data, String mimeType) implements Content {
    public static final String TYPE = "image";

    public static ImageContent of(String base64Data, String mimeType) {
        return new ImageContent(TYPE, base64Data, mimeType);
    }
}
