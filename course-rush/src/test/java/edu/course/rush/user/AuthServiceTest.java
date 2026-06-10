package edu.course.rush.user;

import edu.course.rush.common.security.JwtPrincipal;
import edu.course.rush.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AuthServiceTest {

    private static final String SECRET =
            "test-secret-key-which-is-long-enough-for-hs256-algorithm-256bits!!";

    @Autowired
    private UserRepository userRepository;

    private AuthService authService;
    private final JwtService jwtService = new JwtService(SECRET, 120);

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, new BCryptPasswordEncoder(), jwtService);
    }

    @Test
    void registerThenLoginReturnsValidToken() {
        authService.register("alice", "爱丽丝", "pass123", Role.STUDENT);

        AuthResult result = authService.login("alice", "pass123");

        assertThat(result.token()).isNotBlank();
        assertThat(result.role()).isEqualTo("STUDENT");
        JwtPrincipal principal = jwtService.parse(result.token());
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.userId()).isEqualTo(result.userId());
    }

    @Test
    void passwordIsStoredHashedNotPlaintext() {
        AuthResult result = authService.register("bob", "鲍勃", "secret", Role.STUDENT);

        User saved = userRepository.findById(result.userId()).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret");
        assertThat(saved.getPasswordHash()).startsWith("$2"); // BCrypt 前缀
    }

    @Test
    void registerDuplicateUsernameThrows() {
        authService.register("carol", "卡罗尔", "pw", Role.STUDENT);

        assertThatThrownBy(() -> authService.register("carol", "另一个", "pw2", Role.ADMIN))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void loginWithWrongPasswordThrows() {
        authService.register("dave", "戴夫", "correct", Role.STUDENT);

        assertThatThrownBy(() -> authService.login("dave", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginUnknownUserThrows() {
        assertThatThrownBy(() -> authService.login("ghost", "whatever"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
