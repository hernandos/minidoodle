package de.com.saenz.minidoodle.infrastructure.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User summary representation")
public record UserResponse(
        @Schema(description = "Unique identifier of the user", example = "1")
        Long id,

        @Schema(description = "Username of the user", example = "alice")
        String username,

        @Schema(description = "Full name of the user", example = "Alice Smith")
        String name,

        @Schema(description = "Email address of the user", example = "alice@example.com")
        String email
) {
}
