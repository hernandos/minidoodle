package de.com.saenz.minidoodle.infrastructure.api;

import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.usecase.CreateUserUseCase;
import de.com.saenz.minidoodle.infrastructure.api.mapper.ApiDtoMapper;
import de.com.saenz.minidoodle.infrastructure.api.model.CreateUserRequest;
import de.com.saenz.minidoodle.infrastructure.api.model.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller managing User resources.
 */
@Validated
@RestController
@RequestMapping(value = "/api/v1/users",
                produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
@RequiredArgsConstructor
@Tag(name = "Users", description = "Endpoints for registering and managing user profiles")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final ApiDtoMapper apiDtoMapper;

    @Operation(summary = "Register a new user",
               description = "Creates a new user profile with a unique username and email.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User successfully created",
                         content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed - invalid input",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - username or email already exists",
                         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        User created = createUserUseCase.createUser(request.username(), request.name(), request.email());
        return apiDtoMapper.toUserResponse(created);
    }
}
