# Java Spring Boot Code Style Guide

This document outlines the standard coding conventions and best practices for Java Spring Boot applications.

## 1. Naming Conventions

### 1.1 Classes and Interfaces
- Use **PascalCase** for class and interface names (e.g., `UserService`, `UserRepository`).
- Interface names should **not** begin with `I` (e.g., `UserService` instead of `IUserService`).
- Implementation classes should use the `Impl` suffix only if they are the sole implementation of an interface (e.g., `UserServiceImpl`).

### 1.2 Methods and Variables
- Use **camelCase** for method and variable names (e.g., `findUserById`, `firstName`).
- Method names should be verbs or verb phrases (e.g., `calculateTotal`, `saveUser`).
- Boolean variables should often start with `is`, `has`, or `can` (e.g., `isActive`, `hasPermission`).

### 1.3 Constants
- Use **SCREAMING_SNAKE_CASE** for constants (static final variables) (e.g., `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE`).

### 1.4 Packages
- Use **lowercase** for package names (e.g., `com.orasa.backend.domain`).
- Use singular nouns for domain packages (e.g., `user`, `order` instead of `users`, `orders`).

## 2. Project Structure

### 2.1 Layered Architecture
Follow a standard layered architecture:
- **Controller/Web Layer**: Handles HTTP requests/responses (`.controller` or `.web` package).
- **Service Layer**: Contains business logic (`.service` package).
- **Repository/Data Layer**: Data access logic (`.repository` or `.dao` package).
- **Domain/Model Layer**: Entities and DTOs (`.domain`, `.model`, or `.dto` package).
- **Config Layer**: Configuration classes (`.config` package).

## 3. Formatting

### 3.1 Indentation
- Use **4 spaces** for indentation. Do not use tabs.

### 3.2 Braces
- Use **K&R style** (Egyptian brackets) - opening brace on the same line as the declaration.
```java
if (condition) {
    // code
} else {
    // code
}
```

### 3.3 Line Length
- Aim for a soft limit of **100-120 characters** per line.

### 3.4 Imports
- Avoid wildcard imports (`import java.util.*;`). Import classes explicitly.
- Group imports: standard Java imports, third-party libraries, correct project imports.

## 4. Spring Boot Best Practices

### 4.1 Dependency Injection
- Prefer **Constructor Injection** over Field Injection (`@Autowired` on fields).
- Use `final` for injected dependencies to ensure immutability.
```java
@Service
@RequiredArgsConstructor // Lombok
public class UserService {
    private final UserRepository userRepository;
}
```

### 4.2 Rest Controllers
- Use plural nouns for resource paths (e.g., `/api/users`).
- Use proper HTTP methods (`GET`, `POST`, `PUT`, `DELETE`).
- Return `ResponseEntity<T>` for flexibility in status codes and headers.
- Use `@RestController` instead of `@Controller` + `@ResponseBody`.

### 4.3 Exception Handling
- Use `@RestControllerAdvice` and `@ExceptionHandler` for global exception handling.
- Return meaningful error responses (e.g., specific error codes and messages) rather than stack traces.

### 4.4 Validation
- Use Bean Validation (`@Valid`, `@NotNull`, `@Size`) on DTOs.
- Validate inputs at the Controller layer.

### 4.5 Configuration
- Move configuration values to `application.yml` or `application.properties`.
- Use `@ConfigurationProperties` for grouping related properties.

## 5. Lombok Usage

- **@Data**: Use with caution on Entities (can cause performance issues with `hashCode` and `equals` if circular references exist). Prefer `@Getter`, `@Setter`, `@ToString`.
- **@Builder**: Useful for constructing complex objects.
- **@RequiredArgsConstructor**: ideal for constructor injection.
- **@Slf4j**: For logging.

## 6. Documentation

- Use **Javadoc** for public methods and classes, especially in the Service and Utility layers.
- Explain *why* something is done, not just *what*.
- Keep comments up-to-date with code changes.

## 7. Testing

- Write **Unit Tests** for Service layer logic (use JUnit 5 and Mockito).
- Write **Integration Tests** for Controllers and Repositories (`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`).
- Use descriptive test method names (e.g., `shouldCreateUserWhenValidDataProvided`).
