package com.example;

import com.example.test.MySQLTestResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@WithTestResource(value = MySQLTestResource.class, restrictToAnnotatedClass = false)
public class ApplicationTest {

    @Test
    public void testHealthEndpoint() {
        given()
          .when().get("/api/health")
          .then()
             .statusCode(200)
             .body("status", is("UP"))
             .body("application", is("quarkus-temporal-example"))
             .body("version", is("1.0.0-SNAPSHOT"));
    }

    @Test
    public void testQuarkusHealthEndpoint() {
        given()
          .when().get("/q/health")
          .then()
             .statusCode(200)
             .body("status", is("UP"));
    }

    @Test
    public void testLivenessProbe() {
        given()
          .when().get("/q/health/live")
          .then()
             .statusCode(200)
             .body("status", is("UP"));
    }

    @Test
    public void testReadinessProbe() {
        given()
          .when().get("/q/health/ready")
          .then()
             .statusCode(200)
             .body("status", is("UP"));
    }
}