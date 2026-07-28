package com.giri.oms.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Same shape and same "start once in a static initializer, not @Container"
 * reasoning as oms-main's AbstractIntegrationTest — see there for the full
 * explanation of why @Container/@Testcontainers breaks test-context caching
 * across more than one test class.
 * <p>
 * One real difference: oms-main's version needs REDIS up before ANY
 * @SpringBootTest context can even start, because Redisson connects eagerly
 * at application startup. This service has no Redisson (see
 * common.config.CacheConfig — plain Spring Cache + Lettuce only), and
 * Lettuce's connection factory is lazy, so in principle Redis is only
 * strictly required for tests that actually exercise caching. It's still
 * started here for every test class extending this one, for simplicity —
 * revisit if container startup time becomes a real cost.
 * <p>
 * Requires Docker to be running wherever these tests execute.
 */
@Tag("integration")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES;
    static final GenericContainer<?> REDIS;
    public static final KafkaContainer KAFKA;

    static {
        POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        POSTGRES.start();

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withCommand("redis-server --requirepass my_secret_test_password")
                .withExposedPorts(6379);
        REDIS.start();

        KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "my_secret_test_password");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.outbox.poll-interval-ms", () -> 100000L);
    }
}
