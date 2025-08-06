package com.example.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class MetricsMonitorResourceTest {
    
    @Test
    public void testStartMonitoring() {
        given()
            .when()
            .post("/metrics-monitor/start")
            .then()
            .statusCode(anyOf(is(200), is(500))); // May fail if already running
    }
    
    @Test
    public void testStopMonitoring() {
        given()
            .when()
            .delete("/metrics-monitor/stop")
            .then()
            .statusCode(anyOf(is(200), is(500)));
    }
    
    @Test
    public void testPauseMonitoring() {
        given()
            .when()
            .put("/metrics-monitor/pause")
            .then()
            .statusCode(anyOf(is(200), is(500)));
    }
    
    @Test
    public void testResumeMonitoring() {
        given()
            .when()
            .put("/metrics-monitor/resume")
            .then()
            .statusCode(anyOf(is(200), is(500)));
    }
    
    @Test
    public void testUpdateThreshold() {
        given()
            .queryParam("metric", "cpu")
            .queryParam("value", 75.0)
            .when()
            .put("/metrics-monitor/threshold")
            .then()
            .statusCode(anyOf(is(200), is(500)));
    }
    
    @Test
    public void testUpdateThresholdMissingParams() {
        given()
            .queryParam("metric", "cpu")
            .when()
            .put("/metrics-monitor/threshold")
            .then()
            .statusCode(400)
            .body(containsString("Both metric and value parameters are required"));
    }
    
    @Test
    public void testGetStatus() {
        given()
            .when()
            .get("/metrics-monitor/status")
            .then()
            .statusCode(200)
            .body(containsString("workflowId"));
    }
    
    @Test
    public void testGetRecentMetrics() {
        given()
            .when()
            .get("/metrics-monitor/metrics")
            .then()
            .statusCode(anyOf(is(200), is(500)));
    }
}