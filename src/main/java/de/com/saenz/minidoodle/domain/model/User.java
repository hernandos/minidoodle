package de.com.saenz.minidoodle.domain.model;

import lombok.*;

/**
 * Pure domain entity representing a registered platform user.
 * Zero framework or persistence annotations.
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class User {
    private Long id;
    private String username;
    private String name;
    private String email;
}
