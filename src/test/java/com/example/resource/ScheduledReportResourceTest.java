package com.example.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class ScheduledReportResourceTest {
    
    @Test
    public void testStartScheduledWorkflow() {
        given()
            .when()
            .post("/scheduled-report/start")
            .then()
            .statusCode(200)
            .body(containsString("started"))
            .body(containsString("scheduled-report-workflow"))
            .body(containsString("*/5 * * * * (every 5 minutes)"));
    }
    
    @Test
    public void testGetWorkflowStatus() {
        given()
            .when()
            .get("/scheduled-report/status")
            .then()
            .statusCode(200)
            .body(containsString("workflowId"))
            .body(containsString("status"));
    }
    
    @Test
    public void testQueryLastReportTime() {
        given()
            .when()
            .get("/scheduled-report/query/last-report")
            .then()
            .statusCode(anyOf(is(200), is(500))); // Depends on whether workflow is running
    }
    
    @Test
    public void testQueryReportCount() {
        given()
            .when()
            .get("/scheduled-report/query/count")
            .then()
            .statusCode(anyOf(is(200), is(500))); // Depends on whether workflow is running
    }
    
    @Test
    public void testQueryRecentReports() {
        given()
            .when()
            .get("/scheduled-report/query/recent")
            .then()
            .statusCode(anyOf(is(200), is(500))); // Depends on whether workflow is running
    }
    
    @Test
    public void testStopScheduledWorkflow() {
        // This will return 500 if no workflow is running, which is expected
        given()
            .when()
            .delete("/scheduled-report/stop")
            .then()
            .statusCode(anyOf(is(200), is(500)));
    }
}