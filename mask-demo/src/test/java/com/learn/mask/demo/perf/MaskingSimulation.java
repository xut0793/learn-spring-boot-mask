package com.learn.mask.demo.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * Compare masking on/off by starting the demo with different {@code masking.enabled} values.
 * Target 15000 QPS is hardware-dependent: raise {@code usersPerSec} on a dedicated box and record QPS/RT/CPU/GC.
 * Run: start mask-demo, then {@code mvn -pl mask-demo -Pperf gatling:test}.
 */
public class MaskingSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json")
            .basicAuth("user", "user123");

    ScenarioBuilder jackson = scenario("jackson-user")
            .exec(http("jackson user").get("/api/jackson/users/1"));

    {
        setUp(jackson.injectOpen(constantUsersPerSec(50).during(20)))
                .protocols(httpProtocol);
    }
}
