package com.pragma.loanprocessing.infrastructure.adapter;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.pragma.loanprocessing.domain.model.Applicant;
import com.pragma.loanprocessing.domain.port.LoanEvaluationPort;
import com.pragma.loanprocessing.infrastructure.exception.LoanEvaluationException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/** Adaptador que implementa el puerto de evaluación simulando el motor externo. */
@Component
public class LoanEvaluationAdapter implements LoanEvaluationPort {

    private static final Logger LOG = LoggerFactory.getLogger(LoanEvaluationAdapter.class);
    private static final int MINIMUM_AGE = 18;
    private static final int MINIMUM_CREDIT_SCORE = 600;
    private static final BigDecimal INCOME_MULTIPLIER = new BigDecimal("4");
    private static final int TIMEOUT_SECONDS = 2;

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Crea el adaptador con los registros de resiliencia.
     * @param circuitBreakerRegistry registro de circuit breakers
     * @param retryRegistry registro de reintentos
     */
    public LoanEvaluationAdapter(CircuitBreakerRegistry circuitBreakerRegistry, 
                                  RetryRegistry retryRegistry) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("loanEvaluation");
        this.retry = retryRegistry.retry("loanEvaluation");
        LOG.info("LoanEvaluationAdapter inicializado con CircuitBreaker y Retry configurados");
    }

    @Override
    public CompletionStage<Boolean> evaluateEligibility(Applicant applicant, double requestedAmount) {
        LOG.info("Iniciando evaluación de elegibilidad para solicitante: {}", applicant.identificationNumber());
        
        Supplier<CompletionStage<Boolean>> decoratedSupplier = CircuitBreaker.decorateCompletionStage(
                circuitBreaker,
                Retry.decorateCompletionStage(retry, retryScheduler,
                        () -> performEvaluation(applicant, requestedAmount)
                )
        );
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            LOG.error("Error en evaluación de elegibilidad: {}", e.getMessage());
            return CompletableFuture.failedFuture(
                    new LoanEvaluationException("Error al evaluar elegibilidad: " + e.getMessage())
            );
        }
    }

    private CompletionStage<Boolean> performEvaluation(Applicant applicant, double requestedAmount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
                
                boolean meetsAge = applicant.meetsMinimumAgeRequirement(MINIMUM_AGE);
                boolean hasIncome = applicant.hasSufficientIncome(
                        BigDecimal.valueOf(requestedAmount), 
                        INCOME_MULTIPLIER
                );
                boolean hasCreditScore = applicant.hasAcceptableCreditScore(MINIMUM_CREDIT_SCORE);
                
                boolean eligible = meetsAge && hasIncome && hasCreditScore;
                
                LOG.info("Evaluación completada para {}: elegible={}", 
                        applicant.identificationNumber(), eligible);
                
                return eligible;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LoanEvaluationException("Evaluación interrumpida");
            }
        });
    }

    @Override
    public CompletionStage<Integer> getCreditScore(String identificationNumber) {
        LOG.debug("Obteniendo score crediticio para: {}", identificationNumber);
        
        Supplier<CompletionStage<Integer>> decoratedSupplier = CircuitBreaker.decorateCompletionStage(
                circuitBreaker,
                Retry.decorateCompletionStage(retry, retryScheduler,
                        () -> fetchCreditScore(identificationNumber)
                )
        );
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            LOG.error("Error al obtener credit score: {}", e.getMessage());
            return CompletableFuture.failedFuture(
                    new LoanEvaluationException("Error al obtener credit score: " + e.getMessage())
            );
        }
    }

    private CompletionStage<Integer> fetchCreditScore(String identificationNumber) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50);
                int score = 650 + (identificationNumber.hashCode() % 150);
                LOG.debug("Credit score obtenido: {}", score);
                return score;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LoanEvaluationException("Error al obtener credit score");
            }
        });
    }

    @Override
    public CompletionStage<Boolean> hasOverdueDebts(String identificationNumber) {
        LOG.debug("Verificando deudas vencidas para: {}", identificationNumber);
        
        Supplier<CompletionStage<Boolean>> decoratedSupplier = CircuitBreaker.decorateCompletionStage(
                circuitBreaker,
                Retry.decorateCompletionStage(retry, retryScheduler,
                        () -> checkOverdueDebts(identificationNumber)
                )
        );
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            LOG.error("Error al verificar deudas vencidas: {}", e.getMessage());
            return CompletableFuture.failedFuture(
                    new LoanEvaluationException("Error al verificar deudas: " + e.getMessage())
            );
        }
    }

    private CompletionStage<Boolean> checkOverdueDebts(String identificationNumber) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50);
                boolean hasDebts = identificationNumber.hashCode() % 3 == 0;
                LOG.debug("Deudas vencidas verificadas: {}", hasDebts);
                return hasDebts;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LoanEvaluationException("Error al verificar deudas vencidas");
            }
        });
    }
}
