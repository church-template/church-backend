package com.elipair.church;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        properties = {"DB_PASSWORD=test", "REDIS_PASSWORD=test", "JWT_SECRET=test-secret-0123456789-0123456789-0123"})
class ChurchBackendApplicationTests {

    @Test
    void contextLoads() {}
}
