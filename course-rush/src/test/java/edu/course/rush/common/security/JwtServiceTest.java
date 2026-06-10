package edu.course.rush.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "test-secret-key-which-is-long-enough-for-hs256-algorithm-256bits!!";

    @Test
    void generatesTokenThatParsesBackToSamePrincipal() {
        JwtService jwt = new JwtService(SECRET, 120);

        String token = jwt.generateToken(42L, "alice", "STUDENT");
        JwtPrincipal principal = jwt.parse(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.role()).isEqualTo("STUDENT");
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwt = new JwtService(SECRET, 120);
        String token = jwt.generateToken(1L, "bob", "ADMIN");

        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertThatThrownBy(() -> jwt.parse(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = new JwtService(SECRET, -1); // 已过期
        String token = jwt.generateToken(1L, "carol", "STUDENT");

        assertThatThrownBy(() -> jwt.parse(token))
                .isInstanceOf(InvalidTokenException.class);
    }
}
