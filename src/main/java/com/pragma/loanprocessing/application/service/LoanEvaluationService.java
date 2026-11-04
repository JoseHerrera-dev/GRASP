package com.pragma.loanprocessing.application.service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pragma.loanprocessing.domain.model.Applicant;
import com.pragma.loanprocessing.domain.model.LoanApplication;
import com.pragma.loanprocessing.domain.port.LoanEvaluationPort;
import com.pragma.loanprocessing.infrastructure.exception.LoanEvaluationException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

/** Servicio que evalúa la elegibilidad del solicitante y crea la solicitud resultante. */
@Service
public class LoanEvaluationService {

    private static final Logger LOG = LoggerFactory.getLogger(LoanEvaluationService.class);
    private static final int DEFAULT_MINIMUM_AGE = 18;
    private static final int DEFAULT_MINIMUM_CREDIT_SCORE = 600;
    private static final BigDecimal DEFAULT_INCOME_MULTIPLIER = new BigDecimal("4");
    private static final String CIRCUIT_BREAKER_NAME = "loanEvaluation";
    private static final String RETRY_NAME = "loanEvaluation";
    private static final String TIME_LIMITER_NAME = "loanEvaluation";

    private final LoanEvaluationPort loanEvaluationPort;

    /**
     * Crea el servicio con su puerto de evaluación.
     * @param loanEvaluationPort puerto hacia el motor de evaluación
     */
    public LoanEvaluationService(LoanEvaluationPort loanEvaluationPort) {
        this.loanEvaluationPort = loanEvaluationPort;
    }

    /**
     * Evalúa la elegibilidad del solicitante y crea la solicitud con el resultado.
     * @param applicant solicitante a evaluar
     * @param requestedAmount monto solicitado
     * @param applicationNumber número de la solicitud
     * @return solicitud creada con el resultado de la evaluación
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "evaluateEligibilityFallback")
    @Retry(name = RETRY_NAME)
    @TimeLimiter(name = TIME_LIMITER_NAME)
    public CompletionStage<LoanApplication> evaluateAndCreateApplication(
            Applicant applicant, BigDecimal requestedAmount, String applicationNumber) {
        
        LOG.info("Iniciando evaluación de elegibilidad para solicitante: {} con monto: {}", 
                applicant.identificationNumber(), requestedAmount);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Boolean eligibilityResult = loanEvaluationPort.evaluateEligibility(
                        applicant, 
                        requestedAmount.doubleValue()
                ).toCompletableFuture().get(2, TimeUnit.SECONDS);

                Integer creditScore = loanEvaluationPort.getCreditScore(
                        applicant.identificationNumber()
                ).toCompletableFuture().get(2, TimeUnit.SECONDS);

                Boolean hasOverdue = loanEvaluationPort.hasOverdueDebts(
                        applicant.identificationNumber()
                ).toCompletableFuture().get(2, TimeUnit.SECONDS);

                String reason = buildEvaluationReason(applicant, requestedAmount, creditScore, hasOverdue);

                return LoanApplication.create(
                        applicant,
                        requestedAmount,
                        applicationNumber,
                        eligibilityResult,
                        reason,
                        creditScore,
                        hasOverdue != null && hasOverdue
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LoanEvaluationException("Evaluación interrumpida para solicitante: " 
                        + applicant.identificationNumber(), e);
            } catch (ExecutionException e) {
                throw new LoanEvaluationException("Error en evaluación de solicitante: " 
                        + applicant.identificationNumber(), e);
            } catch (TimeoutException e) {
                throw new LoanEvaluationException("Timeout en evaluación de solicitante: " 
                        + applicant.identificationNumber(), e);
            }
        });
    }

    private String buildEvaluationReason(Applicant applicant, BigDecimal requestedAmount, 
                                          Integer creditScore, Boolean hasOverdue) {
        StringBuilder reason = new StringBuilder();
        
        if (!applicant.meetsMinimumAgeRequirement(DEFAULT_MINIMUM_AGE)) {
            reason.append("Edad mínima no cumplida. ");
        }
        
        if (!applicant.hasSufficientIncome(requestedAmount, DEFAULT_INCOME_MULTIPLIER)) {
            reason.append("Ingresos insuficientes para el monto solicitado. ");
        }
        
        if (!applicant.hasAcceptableCreditScore(DEFAULT_MINIMUM_CREDIT_SCORE)) {
            reason.append("Score crediticio insuficiente. ");
        }
        
        if (hasOverdue != null && hasOverdue) {
            reason.append("Tiene deudas vencidas. ");
        }
        
        if (reason.length() == 0) {
            return "Elegible según criterios establecidos";
        }
        
        return reason.toString().trim();
    }

    private LoanApplication evaluateEligibilityFallback(Applicant applicant, BigDecimal requestedAmount, 
                                                         String applicationNumber, Throwable t) {
        LOG.error("Fallback activado para solicitante {}: {}", applicant.identificationNumber(), t.getMessage());
        
        return LoanApplication.create(
                applicant,
                requestedAmount,
                applicationNumber,
                false,
                "Error en evaluación: servicio no disponible temporalmente. Intente más tarde.",
                null,
                false
        );
    }

    /**
     * Valida los requisitos mínimos del solicitante para el monto pedido.
     * @param applicant solicitante a validar
     * @param requestedAmount monto solicitado
     * @return true si cumple edad, ingreso y score mínimos
     */
    public boolean validateApplicantRequirements(Applicant applicant, BigDecimal requestedAmount) {
        if (applicant == null || requestedAmount == null) {
            return false;
        }
        
        return applicant.meetsMinimumAgeRequirement(DEFAULT_MINIMUM_AGE)
                && applicant.hasSufficientIncome(requestedAmount, DEFAULT_INCOME_MULTIPLIER)
                && applicant.hasAcceptableCreditScore(DEFAULT_MINIMUM_CREDIT_SCORE);
    }
}
