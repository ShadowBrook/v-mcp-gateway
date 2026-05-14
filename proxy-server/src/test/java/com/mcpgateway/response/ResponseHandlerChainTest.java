package com.mcpgateway.response;

import com.mcpgateway.domain.mcp.TextContent;
import com.mcpgateway.domain.mcp.ImageContent;
import com.mcpgateway.domain.mcp.AudioContent;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseHandlerChainTest {

    private final ResponseHandlerChain chain = new ResponseHandlerChain();

    @Test
    void shouldHandlePlainText() {
        var result = chain.process(Buffer.buffer("hello"), "text/plain");
        assertFalse(result.isError());
        assertEquals(1, result.content().size());
        assertInstanceOf(TextContent.class, result.content().get(0));
        assertEquals("hello", ((TextContent) result.content().get(0)).text());
    }

    @Test
    void shouldHandleHtmlAsText() {
        var result = chain.process(Buffer.buffer("<html></html>"), "text/html");
        assertFalse(result.isError());
        assertInstanceOf(TextContent.class, result.content().get(0));
    }

    @Test
    void shouldHandleJson() {
        var result = chain.process(Buffer.buffer("{\"key\":\"value\"}"), "application/json");
        assertFalse(result.isError());
        assertInstanceOf(TextContent.class, result.content().get(0));
        assertEquals("{\"key\":\"value\"}", ((TextContent) result.content().get(0)).text());
    }

    @Test
    void shouldHandleJsonWithCharset() {
        var result = chain.process(Buffer.buffer("{}"), "application/json; charset=utf-8");
        assertFalse(result.isError());
        assertInstanceOf(TextContent.class, result.content().get(0));
    }

    @Test
    void shouldHandleXml() {
        var result = chain.process(Buffer.buffer("<root/>"), "application/xml");
        assertFalse(result.isError());
        assertInstanceOf(TextContent.class, result.content().get(0));
        assertEquals("<root/>", ((TextContent) result.content().get(0)).text());
    }

    @Test
    void shouldHandleImagePng() {
        byte[] pixel = new byte[]{0x01, 0x02, 0x03};
        var result = chain.process(Buffer.buffer(pixel), "image/png");
        assertFalse(result.isError());
        assertInstanceOf(ImageContent.class, result.content().get(0));
        ImageContent ic = (ImageContent) result.content().get(0);
        assertEquals("image/png", ic.mimeType());
        assertNotNull(ic.data());
    }

    @Test
    void shouldHandleImageJpeg() {
        var result = chain.process(Buffer.buffer("fakejpeg"), "image/jpeg");
        assertFalse(result.isError());
        assertInstanceOf(ImageContent.class, result.content().get(0));
        assertEquals("image/jpeg", ((ImageContent) result.content().get(0)).mimeType());
    }

    @Test
    void shouldHandleAudioMpeg() {
        var result = chain.process(Buffer.buffer("fakeaudio"), "audio/mpeg");
        assertFalse(result.isError());
        assertInstanceOf(AudioContent.class, result.content().get(0));
        assertEquals("audio/mpeg", ((AudioContent) result.content().get(0)).mimeType());
    }

    @Test
    void shouldFallbackToTextForUnknownType() {
        var result = chain.process(Buffer.buffer("binary-data"), "application/octet-stream");
        assertFalse(result.isError());
        assertInstanceOf(TextContent.class, result.content().get(0));
    }

    @Test
    void shouldFallbackToTextForNullContentType() {
        var result = chain.process(Buffer.buffer("data"), null);
        assertFalse(result.isError());
        assertInstanceOf(TextContent.class, result.content().get(0));
        assertEquals("data", ((TextContent) result.content().get(0)).text());
    }
}
