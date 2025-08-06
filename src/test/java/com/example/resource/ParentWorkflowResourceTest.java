package com.example.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class ParentWorkflowResourceTest {
    
    @Test
    public void testStartParentWorkflow() {
        given()
            .queryParam("input", "test-data")
            .when()
            .post("/parent-workflow/start")
            .then()
            .statusCode(200)
            .body(containsString("started"))
            .body(containsString("workflowId"))
            .body(containsString("parent-workflow-"));
    }
    
    @Test
    public void testStartParentWorkflowWithoutInput() {
        given()
            .when()
            .post("/parent-workflow/start")
            .then()
            .statusCode(200)
            .body(containsString("started"))
            .body(containsString("workflowId"));
    }
    
    @Test
    public void testGetWorkflowResultWithoutWorkflowId() {
        given()
            .when()
            .get("/parent-workflow/result")
            .then()
            .statusCode(400)
            .body(containsString("workflowId is required"));
    }
    
    @Test
    public void testGetWorkflowResultWithInvalidWorkflowId() {
        given()
            .queryParam("workflowId", "invalid-workflow-id")
            .when()
            .get("/parent-workflow/result")
            .then()
            .statusCode(500);
    }
}