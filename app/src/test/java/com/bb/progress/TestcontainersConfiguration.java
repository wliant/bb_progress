package com.bb.progress;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final String S3_ACCESS_KEY = "testaccess";
	public static final String S3_SECRET_KEY = "testsecret";
	public static final String S3_BUCKET = "bb-progress-test";

	/**
	 * Photos are stored through the S3 API, so the integration tests exercise a real
	 * implementation of it rather than a mock. Held as a singleton rather than a bean because
	 * {@link PostgreSQLContainer} is also a {@link GenericContainer}, which makes injecting one
	 * by type ambiguous; the Testcontainers MinIO module lags the core version resolved here.
	 */
	private static final GenericContainer<?> MINIO =
			new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
					.withEnv("MINIO_ROOT_USER", S3_ACCESS_KEY)
					.withEnv("MINIO_ROOT_PASSWORD", S3_SECRET_KEY)
					.withCommand("server", "/data")
					.withExposedPorts(9000)
					.waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).forStatusCode(200));

	static {
		MINIO.start();
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

	@Bean
	DynamicPropertyRegistrar objectStorageProperties() {
		return registry -> {
			registry.add("app.s3.endpoint",
					() -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
			registry.add("app.s3.access-key", () -> S3_ACCESS_KEY);
			registry.add("app.s3.secret-key", () -> S3_SECRET_KEY);
			registry.add("app.s3.bucket", () -> S3_BUCKET);
		};
	}
}
