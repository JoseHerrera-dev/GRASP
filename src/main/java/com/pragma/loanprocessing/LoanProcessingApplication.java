package com.pragma.loanprocessing;

import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/** Punto de entrada de la aplicación de procesamiento de préstamos. */
@SpringBootApplication
@EnableConfigurationProperties
public class LoanProcessingApplication {
    
    /**
     * Arranca el contexto de Spring Boot.
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(LoanProcessingApplication.class, args);
    }
    
    /**
     * Circuit breaker para las llamadas al motor de evaluación.
     * @param registry registro de circuit breakers
     * @return circuit breaker configurado
     */
    @Bean
    public CircuitBreaker loanEvaluationCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(2)
                .recordExceptions(java.util.concurrent.TimeoutException.class,
                                 org.springframework.web.client.ResourceAccessException.class)
                .build();
        return registry.circuitBreaker("loanEvaluation", config);
    }
    
    /**
     * Política de reintentos para las llamadas al motor de evaluación.
     * @param registry registro de reintentos
     * @return retry configurado
     */
    @Bean
    public Retry loanEvaluationRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(java.util.concurrent.TimeoutException.class,
                                org.springframework.web.client.ResourceAccessException.class)
                .build();
        return registry.retry("loanEvaluation", config);
    }
    
    /**
     * Límite de tiempo para las llamadas al motor de evaluación.
     * @param registry registro de time limiters
     * @return time limiter configurado
     */
    @Bean
    public TimeLimiter loanEvaluationTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .cancelRunningFuture(true)
                .build();
        return registry.timeLimiter("loanEvaluation", config);
    }
}
