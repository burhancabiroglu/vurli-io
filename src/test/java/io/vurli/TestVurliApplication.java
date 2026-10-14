package io.vurli;

import org.springframework.boot.SpringApplication;

public class TestVurliApplication {

	public static void main(String[] args) {
		SpringApplication.from(VurliApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
