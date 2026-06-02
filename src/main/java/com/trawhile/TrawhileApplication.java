package com.trawhile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point. The hexagonal architecture committed in ADR 0001 keeps
 * the runtime composition root here and pushes feature code into the cluster
 * subpackages of {@code adapter}, {@code port}, and {@code service}.
 *
 * @see <a href="../../../../docs/architecture.md">docs/architecture.md</a>
 */
@SpringBootApplication
@EnableScheduling
public class TrawhileApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrawhileApplication.class, args);
    }
}
