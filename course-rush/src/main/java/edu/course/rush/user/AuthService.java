package edu.course.rush.user;

import edu.course.rush.common.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 注册与登录用例编排。 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResult register(String username, String name, String rawPassword, Role role) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
        User user = new User();
        user.setUsername(username);
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        User saved = userRepository.save(user);
        return toResult(saved);
    }

    @Transactional(readOnly = true)
    public AuthResult login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        return toResult(user);
    }

    private AuthResult toResult(User user) {
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return new AuthResult(token, user.getId(), user.getUsername(), user.getName(), user.getRole().name());
    }
}
