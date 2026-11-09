# Architectural Conventions — Abstract Rules for Any Domain/Microservice

## 1. Dependency Injection
- Use **constructor injection exclusively** via Lombok `@RequiredArgsConstructor` with `private final` fields.
- **Never** use field injection (`@Autowired` on fields) or setter injection.
- Annotate only concrete implementations: `@Service` for application services, `@Component` for infrastructure adapters, `@Repository` for Spring Data interfaces. Never annotate interfaces.
- Controllers depend on **domain use-case/query interfaces**, never on service implementations or repositories directly.
- When multiple implementations of a port exist, select them via **`@Profile`** on the implementation class — never via `@Primary` or conditional logic in consumers.

## 2. Layered Boundaries & DTO/Entity Separation
- Enforce **hexagonal (ports & adapters) layering**: `domain` → `application` → `infrastructure`. Dependencies point inward only; inner layers never import infrastructure or framework types.
- Maintain **strictly separate model types per boundary** — never reuse one class across layers:
  - Domain model: plain POJO (`@Data @Builder`), zero framework annotations.
  - API DTOs (`*Request`/`*Response`): Jakarta Validation + Swagger `@Schema` annotations.
  - Persistence entity: JPA annotations only.
- Perform **all conversions via MapStruct mapper interfaces**, one per boundary (`*ApiMapper`, `*EntityMapper`). Custom type conversions (enums, value objects) go in `default` methods inside the mapper — never in controllers or services.
- Place **input validation on request DTOs** using Jakarta constraints and custom `@Constraint`/`ConstraintValidator` pairs; enforce with `@Valid @RequestBody`.
- Document every REST endpoint with **OpenAPI annotations** (`@Tag`, `@Operation`, `@ApiResponses`, `@Parameter`) — treat them as mandatory, not optional. Error responses use `ProblemDetail` as the schema.


## 3. Application Layer — Use Cases & Queries (CQRS-lite)
- The domain layer exposes **one interface per operation**, split by intent:
  - Commands (writes): named `*UseCase`.
  - Queries (reads): named `*Query`.
- Implementations live in `application/service` as `@Service` classes; a single service may implement multiple related query interfaces.
- Apply **`@Transactional`** on write services and **`@Transactional(readOnly = true)`** on query services.
- Use-case interfaces return domain models or `Optional`/collections thereof — never DTOs or entities.

## 4. Data Access
- The domain layer defines a **plain repository interface** returning domain models — no Spring Data, JPA, or vendor types may appear in its signature.
- Each persistence technology is hidden behind an **adapter** (`*RepositoryAdapter`) implementing that interface, delegating to a tech-specific repository and mapping results to domain objects.
- For JPA: use **Spring Data `JpaRepository` interfaces with derived query methods**; avoid `@Query`/JPQL/`EntityManager` unless unavoidable.
- Alternative persistence mechanisms (CSV, in-memory, etc.) must implement the same domain repository interface behind a `@Profile`, proving the interface is the only swap point.

## 5. Exception Handling
- Use a **single `@RestControllerAdvice`** extending `ResponseEntityExceptionHandler`.
- All error responses must be **`ProblemDetail`** (RFC 7807) with `application/problem+json` media type; always set `title`, `detail`, and a `timestamp` property.
- Map domain/infrastructure exceptions (e.g., `IllegalArgumentException`) to HTTP statuses via `@ExceptionHandler` methods — never catch-and-rethrow in controllers.
- **Bean-validation failures** (`@Valid` on request bodies) are handled by overriding [handleMethodArgumentNotValid]
- Services, use cases, and repositories must not know about HTTP status codes.

## 6. Testing
- Use **JUnit 5 + AssertJ** for assertions and **Mockito** for mocking; Spring Boot test slices where appropriate.
- Mirror the `src/main` package structure under `src/test`.
- **Controllers**: test with `@WebMvcTest(<Controller>.class)` + `MockMvc`; mock all use-case/query interfaces and mappers with `@MockitoBean`; verify status codes and `jsonPath` — including `ProblemDetail` fields (`title`, `status`, `detail`, `timestamp`) for error cases.
- **Application services**: test through the domain interface (`@Autowired` the `*UseCase`/`*Query` port) with `@SpringBootTest` + `@ActiveProfiles` to exercise the real persistence adapter.
- **Integration tests** (full stack + `MockMvc`): name them `*IntegrationTest`, use `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles` + `@TestPropertySource` for fixture data.
- Name test classes `*Test` (unit/slice) or `*IntegrationTest` (full context); methods follow `methodName_shouldExpectedBehavior_whenCondition`.
- Annotate every test method with `@DisplayName` describing the scenario in plain language.
- Cover validation failures (400) and not-found cases (404) for every endpoint.


## Package Layout (mandatory for every feature)
```
<feature>/
├── domain/
│   ├── model/                              # POJOs only
│   ├── repository/                         # port interfaces
│   └── usecase/                            # *UseCase (commands) + *Query (queries)
├── application/service/                    # @Service implementations
└── infrastructure/
    ├── api/{model,mapper,validation}       # controllers, DTOs, mappers, validators
    │   └── GlobalExceptionHandler          # @RestControllerAdvice returning ProblemDetail
    └── persistence/<tech>/{entity,repository,mapper,adapter}
```
