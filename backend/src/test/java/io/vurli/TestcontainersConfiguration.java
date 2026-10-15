package io.vurli;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Real PostgreSQL and a real SMTP server (Mailpit) for integration tests. Emails are
 * read back through Mailpit's API, so tests see exactly what a user would receive.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final int SMTP_PORT = 1025;

	private static final int API_PORT = 8025;

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

	@Bean
	GenericContainer<?> mailpitContainer() {
		return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.5"))
			.withExposedPorts(SMTP_PORT, API_PORT)
			.waitingFor(Wait.forHttp("/livez").forPort(API_PORT));
	}

	@Bean
	DynamicPropertyRegistrar mailProperties(GenericContainer<?> mailpitContainer) {
		return (registry) -> {
			registry.add("spring.mail.host", mailpitContainer::getHost);
			registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(SMTP_PORT));
			registry.add("vurli.app-base-url", () -> "http://app.test");
		};
	}

	/**
	 * Every MockMvc request comes from its own random address, as different visitors would.
	 * Rate limits are per address and shared by all tests in a context, so without this
	 * unrelated tests would use up each other's limits. Tests about limits set the address
	 * explicitly.
	 */
	@Bean
	MockMvcBuilderCustomizer randomClientAddress() {
		return (builder) -> builder.defaultRequest(get("/").with((request) -> {
			request.setRemoteAddr(TestAccounts.randomAddress());
			return request;
		}));
	}

	@Bean
	Mailpit mailpit(GenericContainer<?> mailpitContainer) {
		return new Mailpit(() -> "http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(API_PORT));
	}

}
