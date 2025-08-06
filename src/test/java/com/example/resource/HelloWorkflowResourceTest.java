package com.example.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class HelloWorkflowResourceTest {
    
    @Test
    void testStartHelloWorkflow() {
        RestAssured.given()
            .queryParam("name", "TestUser")
            .when()
            .post("/hello-workflow/start")
            .then()
            .statusCode(200)
            .body(containsString("started"))
            .body(containsString("workflowId"));
    }
    
    @Test
    void testStartHelloWorkflowWithoutName() {
        RestAssured.given()
            .when()
            .post("/hello-workflow/start")
            .then()
            .statusCode(200)
            .body(containsString("started"));
    }
    
    @Test
    void testGetWorkflowResultWithoutId() {
        RestAssured.given()
            .when()
            .get("/hello-workflow/result")
            .then()
            .statusCode(400)
            .body(containsString("workflowId is required"));
    }
}