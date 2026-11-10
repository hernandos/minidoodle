package de.com.saenz.minidoodle.infrastructure.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.usecase.CreateUserUseCase;
import de.com.saenz.minidoodle.infrastructure.api.mapper.ApiDtoMapper;
import de.com.saenz.minidoodle.infrastructure.api.model.CreateUserRequest;
import de.com.saenz.minidoodle.infrastructure.api.model.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateUserUseCase createUserUseCase;

    @MockitoBean
    private ApiDtoMapper apiDtoMapper;

    @Test
    @DisplayName("POST /api/v1/users should create user and return 201 Created")
    void createUser_shouldReturnCreated_whenValid() throws Exception {
        CreateUserRequest request = new CreateUserRequest("charlie", "Charlie Brown", "charlie@example.com");
        User domainUser = User.builder()
                              .id(3L)
                              .username("charlie")
                              .name("Charlie Brown")
                              .email("charlie@example.com")
                              .build();
        UserResponse responseDto = new UserResponse(3L, "charlie", "Charlie Brown", "charlie@example.com");

        when(createUserUseCase.createUser("charlie", "Charlie Brown", "charlie@example.com")).thenReturn(domainUser);
        when(apiDtoMapper.toUserResponse(domainUser)).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(3))
               .andExpect(jsonPath("$.username").value("charlie"))
               .andExpect(jsonPath("$.name").value("Charlie Brown"))
               .andExpect(jsonPath("$.email").value("charlie@example.com"));

        verify(createUserUseCase).createUser("charlie", "Charlie Brown", "charlie@example.com");
    }

    @Test
    @DisplayName("POST /api/v1/users should return 400 Bad Request when validation fails")
    void createUser_shouldReturn400_whenFieldsAreInvalid() throws Exception {
        CreateUserRequest invalidRequest = new CreateUserRequest("ab", "", "not-an-email");

        mockMvc.perform(post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_PROBLEM_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"))
               .andExpect(jsonPath("$.status").value(400));

        verify(createUserUseCase, never()).createUser(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/users should return 409 Conflict when username/email already taken")
    void createUser_shouldReturn409_whenDuplicate() throws Exception {
        CreateUserRequest request = new CreateUserRequest("alice", "Alice Smith", "alice@example.com");

        when(createUserUseCase.createUser(any(), any(), any()))
                .thenThrow(new IllegalStateException("Username 'alice' is already taken"));

        mockMvc.perform(post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_PROBLEM_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.title").value("Business State Conflict"))
               .andExpect(jsonPath("$.status").value(409));
    }
}
