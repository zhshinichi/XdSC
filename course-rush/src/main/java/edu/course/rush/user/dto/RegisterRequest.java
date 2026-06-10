package edu.course.rush.user.dto;

import edu.course.rush.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String name,
        @NotBlank String password,
        @NotNull Role role) {
}
