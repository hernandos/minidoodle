package de.com.saenz.minidoodle.infrastructure.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight user reference embedded in TimeSlot responses.
 * Avoids repeating the full user payload for every slot in aggregated views.
 */
@Schema(description = "Lightweight user reference")
public record UserSummaryResponse(
        @Schema(description = "User unique identifier", example = "1")
        Long id,

        @Schema(description = "Username", example = "alice")
        String username
) {
}
