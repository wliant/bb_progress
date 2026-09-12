package com.bb.progress;

import org.springframework.boot.SpringApplication;

public class TestBbProgressAppApplication {

	public static void main(String[] args) {
		SpringApplication.from(BbProgressAppApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
