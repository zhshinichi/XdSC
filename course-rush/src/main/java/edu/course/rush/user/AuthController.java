package edu.course.rush.user;

import edu.course.rush.user.dto.LoginRequest;
import edu.course.rush.user.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResult register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req.username(), req.name(), req.password(), req.role());
    }

    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.username(), req.password());
    }
}
