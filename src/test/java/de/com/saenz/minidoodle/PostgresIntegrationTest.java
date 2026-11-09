package de.com.saenz.minidoodle;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Meta-annotation for Spring Boot integration tests running against a real PostgreSQL
 * container managed via Testcontainers and {@link org.springframework.boot.testcontainers.service.connection.ServiceConnection}.
 * <p>
 * Favors composition over inheritance, replacing abstract test base classes with a declarative annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public @interface PostgresIntegrationTest {

    @AliasFor(annotation = SpringBootTest.class, attribute = "properties")
    String[] properties() default {};
}
