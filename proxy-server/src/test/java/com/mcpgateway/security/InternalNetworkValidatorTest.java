package com.mcpgateway.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class InternalNetworkValidatorTest {

    private final InternalNetworkValidator validator = new InternalNetworkValidator();

    @Test
    void shouldBlockLoopback() {
        assertTrue(validator.isInternal("http://127.0.0.1:8080/api"));
        assertTrue(validator.isInternal("http://localhost:8080/api"));
        assertTrue(validator.isInternal("https://[::1]:8080/api"));
    }

    @Test
    void shouldBlockSiteLocal() {
        assertTrue(validator.isInternal("http://10.0.0.1:8080/api"));
        assertTrue(validator.isInternal("http://172.16.0.1:8080/api"));
        assertTrue(validator.isInternal("http://192.168.1.1:8080/api"));
    }

    @Test
    void shouldBlockLinkLocal() {
        assertTrue(validator.isInternal("http://169.254.1.1:8080/api"));
    }

    @Test
    void shouldBlockCgnatRange() {
        assertTrue(validator.isInternal("http://100.64.0.1:8080/api"));
        assertTrue(validator.isInternal("http://100.100.0.1:8080/api"));
        assertTrue(validator.isInternal("http://100.127.255.254:8080/api"));
    }

    @Test
    void shouldNotBlockCgnatEdgeBelow() {
        // 100.63.x.x is not in CGNAT range
        assertFalse(validator.isInternal("http://100.63.255.255:8080/api"));
    }

    @Test
    void shouldNotBlockCgnatEdgeAbove() {
        // 100.128.x.x is not in CGNAT range
        assertFalse(validator.isInternal("http://100.128.0.0:8080/api"));
    }

    @Test
    void shouldBlockZeroNetwork() {
        assertTrue(validator.isInternal("http://0.0.0.0:8080/api"));
    }

    @Test
    void shouldBlockBroadcast() {
        assertTrue(validator.isInternal("http://255.255.255.255:8080/api"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://8.8.8.8/api",
        "http://1.1.1.1/api",
        "http://93.184.216.34/api",
        "http://151.101.1.5/api",
        "http://203.0.113.1/api"
    })
    void shouldAllowPublicIps(String url) {
        assertFalse(validator.isInternal(url));
    }

    @Test
    void shouldBlockUrlWithNoHost() {
        assertTrue(validator.isInternal("not-a-valid-url"));
    }

    @Test
    void shouldAllowRealPublicHost() {
        assertFalse(validator.isInternal("https://github.com"));
    }
}
