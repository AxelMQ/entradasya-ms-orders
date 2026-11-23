package com.entradasya.orders.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @InjectMocks
    private HealthController healthController;

    @Test
    @DisplayName("GET /health/live responde 200 OK y status UP")
    void testLivenessCheck() {
        ResponseEntity<Map<String, Object>> response = healthController.liveness();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("ms-orders", response.getBody().get("service"));
    }

    @Test
    @DisplayName("GET /health/ready responde 200 OK cuando la base de datos está disponible")
    void testReadinessCheckSuccess() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = healthController.readiness();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("orders_db", response.getBody().get("database"));
    }

    @Test
    @DisplayName("GET /health/ready responde 503 SERVICE_UNAVAILABLE cuando la base de datos no está disponible")
    void testReadinessCheckDatabaseDown() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused to orders_db:5432"));

        ResponseEntity<Map<String, Object>> response = healthController.readiness();

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
    }
}
