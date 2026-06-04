package com.elipair.church.global.config;

import com.elipair.church.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "SWAGGER_ENABLED=false",
        "DB_PASSWORD=test",
        "REDIS_PASSWORD=test",
        "JWT_SECRET=test-secret-0123456789-0123456789-0123"
})
class SwaggerToggleTest {

    @LocalServerPort
    int port;

    @Test
    void apiDocsDisabledReturns404() {
        RestClient client = RestClient.create("http://localhost:" + port);
        RestClientResponseException ex = catchThrowableOfType(
                RestClientResponseException.class,
                () -> client.get().uri("/v3/api-docs").retrieve().toEntity(String.class)
        );
        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
