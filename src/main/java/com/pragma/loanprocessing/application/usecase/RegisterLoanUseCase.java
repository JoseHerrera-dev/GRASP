package com.pragma.loanprocessing.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.pragma.loanprocessing.domain.model.Applicant;
import com.pragma.loanprocessing.domain.model.LoanApplication;
import com.pragma.loanprocessing.domain.port.LoanRegistrationPort;
import com.pragma.loanprocessing.infrastructure.exception.DuplicateLoanException;
import com.pragma.loanprocessing.infrastructure.exception.LoanEvaluationException;

/** Caso de uso que registra solicitudes de préstamo. */
@Component
public class RegisterLoanUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RegisterLoanUseCase.class);
    private static final int REGISTRATION_TIMEOUT_SECONDS = 3;

    private final LoanRegistrationPort loanRegistrationPort;

    /**
     * Crea el caso de uso con su puerto de registro.
     * @param loanRegistrationPort puerto hacia el registro de solicitudes
     */
    public RegisterLoanUseCase(LoanRegistrationPort loanRegistrationPort) {
        this.loanRegistrationPort = loanRegistrationPort;
    }

    /**
     * Registra una nueva solicitud de préstamo.
     * @param applicant solicitante
     * @param requestedAmount monto solicitado
     * @return solicitud registrada
     */
    public CompletionStage<LoanApplication> execute(Applicant applicant, BigDecimal requestedAmount) {
        String applicationNumber = generateApplicationNumber(applicant.identificationNumber());
        
        LOG.info("Iniciando registro de solicitud con número: {}", applicationNumber);

        return checkIdempotency(applicationNumber)
                .thenCompose(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        LOG.warn("Solicitud duplicada detectada para número: {}", applicationNumber);
                        CompletableFuture<LoanApplication> failedFuture = new CompletableFuture<>();
                        failedFuture.completeExceptionally(
                                new DuplicateLoanException("Ya existe una solicitud con número: " + applicationNumber)
                        );
                        return failedFuture;
                    }
                    
                    return retrieveApplicantData(applicant)
                            .thenCompose(retrievedApplicant -> {
                                if (retrievedApplicant == null) {
                                    LOG.info("Datos del solicitante no encontrados, usando datos originales");
                                    retrievedApplicant = applicant;
                                }
                                
                                LoanApplication loanApplication = LoanApplication.create(
                                        retrievedApplicant,
                                        requestedAmount,
                                        applicationNumber,
                                        true,
                                        "Solicitud registrada exitosamente",
                                        null,
                                        false
                                );
                                
                                return loanRegistrationPort.registerLoanApplication(loanApplication)
                                        .thenApply(registered -> {
                                            LOG.info("Solicitud {} registrada exitosamente con ID: {}", 
                                                    applicationNumber, registered.getId());
                                            return registered;
                                        });
                            });
                })
                .exceptionally(ex -> {
                    LOG.error("Error en registro de solicitud {}: {}", applicationNumber, ex.getMessage());
                    if (ex.getCause() instanceof DuplicateLoanException) {
                        throw (DuplicateLoanException) ex.getCause();
                    }
                    throw new LoanEvaluationException("Error al registrar solicitud: " + ex.getMessage(), ex);
                });
    }

    private CompletionStage<Boolean> checkIdempotency(String applicationNumber) {
        LOG.debug("Verificando idempotencia para número de solicitud: {}", applicationNumber);
        return loanRegistrationPort.existsByApplicationNumber(applicationNumber);
    }

    private CompletionStage<Applicant> retrieveApplicantData(Applicant applicant) {
        LOG.debug("Recuperando datos actualizados del solicitante: {}", applicant.identificationNumber());
        return CompletableFuture.completedFuture(applicant);
    }

    private String generateApplicationNumber(String identificationNumber) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuidSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("LN-%s-%s", identificationNumber, uuidSuffix);
    }

    /**
     * Consulta una solicitud por su ID.
     * @param applicationId ID de la solicitud
     * @return solicitud encontrada
     */
    public CompletionStage<LoanApplication> getApplicationById(UUID applicationId) {
        LOG.debug("Consultando solicitud por ID: {}", applicationId);
        return loanRegistrationPort.getApplicationById(applicationId);
    }

    /**
     * Actualiza el estado de una solicitud.
     * @param applicationId ID de la solicitud
     * @param newStatus nuevo estado
     * @return solicitud actualizada
     */
    public CompletionStage<LoanApplication> updateApplicationStatus(UUID applicationId, 
                                                                      LoanApplication.Status newStatus) {
        LOG.info("Actualizando estado de solicitud {} a {}", applicationId, newStatus);
        
        if (applicationId == null || newStatus == null) {
            CompletableFuture<LoanApplication> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(
                    new IllegalArgumentException("El ID de aplicación y el nuevo estado son obligatorios")
            );
            return failedFuture;
        }
        
        return loanRegistrationPort.updateApplicationStatus(applicationId, newStatus)
                .thenApply(updated -> {
                    LOG.info("Estado de solicitud {} actualizado exitosamente a {}", 
                            applicationId, newStatus);
                    return updated;
                });
    }

    /**
     * Reintenta el registro de una solicitud a partir de su número original.
     * @param applicant solicitante
     * @param requestedAmount monto solicitado
     * @param originalApplicationNumber número de la solicitud original
     * @return solicitud existente o recién registrada
     */
    public CompletionStage<LoanApplication> retryRegistration(Applicant applicant, 
                                                               BigDecimal requestedAmount, 
                                                               String originalApplicationNumber) {
        LOG.info("Reintentando registro de solicitud con número original: {}", originalApplicationNumber);
        return loanRegistrationPort.existsByApplicationNumber(originalApplicationNumber)
                .thenCompose(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        LOG.info("Recuperando solicitud existente: {}", originalApplicationNumber);
                        return loanRegistrationPort.getApplicationById(
                                UUID.nameUUIDFromBytes(originalApplicationNumber.getBytes())
                        );
                    }
                    
                    return execute(applicant, requestedAmount);
                });
    }
}
