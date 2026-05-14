package com.mcpgateway.session;

import com.mcpgateway.session.impl.LocalSessionStore;
import com.mcpgateway.transport.Transport;
import io.vertx.core.http.HttpServerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalSessionStoreTest {

    private LocalSessionStore store;
    private HttpServerResponse mockResponse;

    @BeforeEach
    void setUp() {
        store = new LocalSessionStore();
        mockResponse = mock(HttpServerResponse.class);
    }

    @Test
    void shouldCreateAndGetSession() {
        Transport transport = mock(Transport.class);
        GatewaySession session = new GatewaySession("session-1", "demo", transport, mockResponse);

        store.create("demo", "session-1", session);
        GatewaySession retrieved = store.get("session-1").result();

        assertNotNull(retrieved);
        assertEquals("session-1", retrieved.id());
        assertEquals("demo", retrieved.prefix());
        assertEquals(transport, retrieved.transport());
    }

    @Test
    void shouldFailForMissingSession() {
        var future = store.get("nonexistent");
        assertTrue(future.failed());
        assertTrue(future.cause().getMessage().contains("nonexistent"));
    }

    @Test
    void shouldRemoveSession() {
        Transport transport = mock(Transport.class);
        GatewaySession session = new GatewaySession("session-1", "demo", transport, mockResponse);

        store.create("demo", "session-1", session);
        assertTrue(store.get("session-1").succeeded());

        store.remove("session-1");
        assertTrue(store.get("session-1").failed());
    }

    @Test
    void shouldCountSessions() {
        assertEquals(0, store.count());

        Transport t1 = mock(Transport.class);
        Transport t2 = mock(Transport.class);
        store.create("demo", "s1", new GatewaySession("s1", "demo", t1, mockResponse));
        store.create("demo", "s2", new GatewaySession("s2", "demo", t2, mockResponse));

        assertEquals(2, store.count());
    }

    @Test
    void shouldListByPrefix() {
        Transport t1 = mock(Transport.class);
        Transport t2 = mock(Transport.class);
        Transport t3 = mock(Transport.class);

        store.create("demo", "s1", new GatewaySession("s1", "demo", t1, mockResponse));
        store.create("demo", "s2", new GatewaySession("s2", "demo", t2, mockResponse));
        store.create("other", "s3", new GatewaySession("s3", "other", t3, mockResponse));

        List<GatewaySession> demoSessions = store.listByPrefix("demo").result();
        assertEquals(2, demoSessions.size());

        List<GatewaySession> otherSessions = store.listByPrefix("other").result();
        assertEquals(1, otherSessions.size());
    }
}
