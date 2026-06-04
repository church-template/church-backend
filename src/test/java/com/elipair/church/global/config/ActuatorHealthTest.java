package com.elipair.church.global.config;

import com.elipair.church.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_PASSWORD=test",
        "REDIS_PASSWORD=test",
        "JWT_SECRET=test-secret-0123456789-0123456789-0123"
})
class ActuatorHealthTest {

    @LocalServerPort
    int port;

    @Test
    void actuatorHealthIsPubliclyAccessible() {
        RestClient client = RestClient.create("http://localhost:" + port);
        ResponseEntity<String> resp = client.get()
                .uri("/actuator/health")
                .retrieve()
                .toEntity(String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("UP");
    }
}
