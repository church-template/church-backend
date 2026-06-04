package com.elipair.church.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.redis.TokenBlacklist;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SecuredTestController.class})
class SecurityBlacklistE2eTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider provider;

    @Autowired
    TokenBlacklist blacklist;

    @Test
    void blacklisted_access_token_is_rejected_on_protected_path() throws Exception {
        String token = provider.issueAccess(new MemberPrincipal(1L, "u", "n", 100), null, List.of("SERMON_WRITE"));
        Claims claims = provider.parse(token);
        blacklist.blacklist(claims.getId(), claims.getExpiration().toInstant());

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void non_blacklisted_token_still_works() throws Exception {
        String token = provider.issueAccess(new MemberPrincipal(1L, "u", "n", 100), null, List.of("SERMON_WRITE"));

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
