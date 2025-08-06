package com.example.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OrderSagaResource REST endpoints.
 * 
 * These tests verify the REST API integration with Temporal workflows.
 * The application connects to the real Temporal server at localhost:7233.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderSagaResourceTest {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderSagaResourceTest.class);
    private static String testWorkflowId;
    
    @Test
    @Order(1)
    @DisplayName("Test starting a sample order via REST API")
    public void testStartSampleOrder() {
        logger.info("Testing POST /order-saga/start-sample endpoint");
        
        Response response = RestAssured.given()
            .contentType("application/json")
            .when()
            .post("/order-saga/start-sample")
            .then()
            .statusCode(200)
            .body("status", equalTo("started"))
            .body("workflowId", notNullValue())
            .body("runId", notNullValue())
            .body("message", containsString("Order processing started"))
            .extract()
            .response();
        
        testWorkflowId = response.jsonPath().getString("workflowId");
        String runId = response.jsonPath().getString("runId");
        
        logger.info("Order started successfully - Workflow ID: {}, Run ID: {}", testWorkflowId, runId);
        logger.info("View in Temporal UI: http://localhost:8088/namespaces/default/workflows/{}", testWorkflowId);
        
        assertNotNull(testWorkflowId);
        assertTrue(testWorkflowId.startsWith("order-saga-"));
    }
    
    @Test
    @Order(2)
    @DisplayName("Test getting order status via REST API")
    public void testGetOrderStatus() throws InterruptedException {
        // Wait a moment for workflow to start processing
        Thread.sleep(1000);
        
        logger.info("Testing GET /order-saga/status/{} endpoint", testWorkflowId);
        
        Response response = RestAssured.given()
            .pathParam("workflowId", testWorkflowId)
            .when()
            .get("/order-saga/status/{workflowId}")
            .then()
            .statusCode(200)
            .body("workflowId", equalTo(testWorkflowId))
            .body("orderStatus", notNullValue())
            .body("completedSteps", notNullValue())
            .extract()
            .response();
        
        String status = response.jsonPath().getString("orderStatus");
        java.util.List<String> steps = response.jsonPath().getList("completedSteps");
        
        logger.info("Order status: {}, Completed steps: {}", status, steps);
        
        assertNotNull(status);
        assertNotNull(steps);
    }
    
    @Test
    @Order(3)
    @DisplayName("Test starting order with custom data")
    public void testStartOrderWithCustomData() {
        logger.info("Testing POST /order-saga/start with custom order data");
        
        String orderJson = """
            {
                "customerId": "CUST-REST-TEST",
                "shippingAddress": "456 REST API Test Ave, Integration City, IC 54321",
                "totalAmount": 499.99,
                "items": [
                    {
                        "productId": "PROD-REST-001",
                        "quantity": 2,
                        "price": 249.99
                    }
                ]
            }
            """;
        
        Response response = RestAssured.given()
            .contentType("application/json")
            .body(orderJson)
            .when()
            .post("/order-saga/start")
            .then()
            .statusCode(200)
            .body("status", equalTo("started"))
            .body("workflowId", notNullValue())
            .extract()
            .response();
        
        String workflowId = response.jsonPath().getString("workflowId");
        logger.info("Custom order started - Workflow ID: {}", workflowId);
        
        assertNotNull(workflowId);
    }
    
    @Test
    @Order(4)
    @DisplayName("Test simulating failure via REST API")
    public void testSimulateFailure() {
        logger.info("Testing failure simulation endpoint");
        
        // First start a new order
        Response startResponse = RestAssured.given()
            .contentType("application/json")
            .when()
            .post("/order-saga/start-sample")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String workflowId = startResponse.jsonPath().getString("workflowId");
        logger.info("Started order for failure test - Workflow ID: {}", workflowId);
        
        // Simulate inventory failure
        Response failureResponse = RestAssured.given()
            .contentType("application/json")
            .pathParam("workflowId", workflowId)
            .queryParam("step", "INVENTORY")
            .when()
            .post("/order-saga/simulate-failure/{workflowId}")
            .then()
            .statusCode(200)
            .body("status", equalTo("failure simulation set"))
            .body("workflowId", equalTo(workflowId))
            .body("failureStep", equalTo("INVENTORY"))
            .extract()
            .response();
        
        logger.info("Failure simulation set for step: {}", 
            failureResponse.jsonPath().getString("failureStep"));
    }
    
    @Test
    @Order(5)
    @DisplayName("Test getting compensation history")
    public void testGetCompensationHistory() throws InterruptedException {
        logger.info("Testing compensation history endpoint");
        
        // Start a new order and simulate failure
        Response startResponse = RestAssured.given()
            .contentType("application/json")
            .when()
            .post("/order-saga/start-sample")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String workflowId = startResponse.jsonPath().getString("workflowId");
        
        // Simulate shipping failure (after payment and inventory succeed)
        RestAssured.given()
            .contentType("application/json")
            .pathParam("workflowId", workflowId)
            .queryParam("step", "SHIPPING")
            .when()
            .post("/order-saga/simulate-failure/{workflowId}")
            .then()
            .statusCode(200);
        
        // Wait for workflow to process and fail
        Thread.sleep(3000);
        
        // Get compensation history
        Response historyResponse = RestAssured.given()
            .pathParam("workflowId", workflowId)
            .when()
            .get("/order-saga/compensation-history/{workflowId}")
            .then()
            .statusCode(200)
            .body("workflowId", equalTo(workflowId))
            .body("compensationRequired", notNullValue())
            .extract()
            .response();
        
        Boolean compensationRequired = historyResponse.jsonPath().getBoolean("compensationRequired");
        java.util.List<String> compensationActions = historyResponse.jsonPath().getList("compensationActions");
        
        logger.info("Compensation required: {}, Actions: {}", compensationRequired, compensationActions);
        
        if (compensationRequired != null && compensationRequired) {
            assertNotNull(compensationActions);
            assertTrue(compensationActions.size() > 0);
        }
    }
    
    @Test
    @Order(6)
    @DisplayName("Test error handling for invalid requests")
    public void testErrorHandling() {
        logger.info("Testing error handling for invalid requests");
        
        // Test with empty request body
        RestAssured.given()
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/order-saga/start")
            .then()
            .statusCode(400)
            .body("error", containsString("Invalid order request"));
        
        // Test status with invalid workflow ID
        RestAssured.given()
            .pathParam("workflowId", "invalid-workflow-id")
            .when()
            .get("/order-saga/status/{workflowId}")
            .then()
            .statusCode(404)
            .body("error", containsString("Workflow not found"));
        
        // Test simulate failure with invalid step
        RestAssured.given()
            .contentType("application/json")
            .pathParam("workflowId", "some-workflow")
            .queryParam("step", "INVALID_STEP")
            .when()
            .post("/order-saga/simulate-failure/{workflowId}")
            .then()
            .statusCode(400)
            .body("error", containsString("Invalid step"));
        
        logger.info("Error handling tests completed successfully");
    }
    
    @Test
    @Order(7)
    @DisplayName("Test getting order result (blocking call)")
    public void testGetOrderResult() {
        logger.info("Testing GET /order-saga/result endpoint");
        
        // Start a small order that should complete quickly
        Response startResponse = RestAssured.given()
            .contentType("application/json")
            .body("""
                {
                    "customerId": "CUST-QUICK",
                    "shippingAddress": "789 Quick St",
                    "totalAmount": 10.00,
                    "items": [
                        {
                            "productId": "PROD-QUICK",
                            "quantity": 1,
                            "price": 10.00
                        }
                    ]
                }
                """)
            .when()
            .post("/order-saga/start")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String workflowId = startResponse.jsonPath().getString("workflowId");
        logger.info("Started quick order - Workflow ID: {}", workflowId);
        
        // This call blocks until workflow completes
        Response resultResponse = RestAssured.given()
            .pathParam("workflowId", workflowId)
            .when()
            .get("/order-saga/result/{workflowId}")
            .then()
            .statusCode(200)
            .body("orderId", notNullValue())
            .body("status", notNullValue())
            .extract()
            .response();
        
        String orderId = resultResponse.jsonPath().getString("orderId");
        String status = resultResponse.jsonPath().getString("status");
        
        logger.info("Order completed - Order ID: {}, Status: {}", orderId, status);
        
        assertNotNull(orderId);
        assertTrue(orderId.startsWith("ORDER-"));
    }
}