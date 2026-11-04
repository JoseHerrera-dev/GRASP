package com.pragma.loanprocessing.infrastructure.adapter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.pragma.loanprocessing.domain.model.Applicant;
import com.pragma.loanprocessing.domain.model.LoanApplication;
import com.pragma.loanprocessing.domain.port.LoanRegistrationPort;
import com.pragma.loanprocessing.infrastructure.exception.DuplicateLoanException;

/** Adaptador que implementa el puerto de registro con almacenamiento en memoria. */
@Repository
public class LoanRegistrationAdapter implements LoanRegistrationPort {

    private static final Logger LOG = LoggerFactory.getLogger(LoanRegistrationAdapter.class);
    
    private final Map<String, LoanApplication> applicationStore = new ConcurrentHashMap<>();
    private final Map<UUID, LoanApplication> idStore = new ConcurrentHashMap<>();
    private final Map<UUID, Applicant> applicantStore = new ConcurrentHashMap<>();
    private long applicationCounter = 0;

    @Override
    @Transactional
    public CompletionStage<LoanApplication> registerLoanApplication(LoanApplication loanApplication) {
        LOG.info("Registrando solicitud de préstamo: {}", loanApplication.getApplicationNumber());

        return CompletableFuture.supplyAsync(() -> {
            if (existsByApplicationNumberSync(loanApplication.getApplicationNumber())) {
                LOG.warn("Solicitud duplicada detectada: {}", loanApplication.getApplicationNumber());
                throw new DuplicateLoanException(
                        "Ya existe una solicitud con el número: " + loanApplication.getApplicationNumber()
                );
            }

            LocalDateTime now = LocalDateTime.now();
            LoanApplication savedApplication = new LoanApplication(
                    UUID.randomUUID(),
                    loanApplication.getApplicant(),
                    loanApplication.getRequestedAmount(),
                    LoanApplication.Status.PENDING,
                    generateApplicationNumber(),
                    loanApplication.getEvaluationResult(),
                    now,
                    now,
                    loanApplication.getEvaluationReason(),
                    loanApplication.getCreditScore(),
                    loanApplication.isHasOverdueDebts()
            );

            applicationStore.put(savedApplication.getApplicationNumber(), savedApplication);
            idStore.put(savedApplication.getId(), savedApplication);
            applicantStore.put(savedApplication.getId(), loanApplication.getApplicant());

            LOG.info("Solicitud registrada exitosamente: {} con ID: {}",
                    savedApplication.getApplicationNumber(), savedApplication.getId());
            
            return savedApplication;
        });
    }

    private boolean existsByApplicationNumberSync(String applicationNumber) {
        return applicationStore.containsKey(applicationNumber);
    }

    @Override
    public CompletionStage<Boolean> existsByApplicationNumber(String applicationNumber) {
        LOG.debug("Verificando existencia de solicitud: {}", applicationNumber);
        return CompletableFuture.completedFuture(applicationStore.containsKey(applicationNumber));
    }

    @Override
    @Transactional
    public CompletionStage<LoanApplication> updateApplicationStatus(UUID applicationId, 
                                                                      LoanApplication.Status newStatus) {
        LOG.info("Actualizando estado de solicitud {} a {}", applicationId, newStatus);
        
        return CompletableFuture.supplyAsync(() -> {
            LoanApplication existing = idStore.get(applicationId);
            if (existing == null) {
                LOG.error("Solicitud no encontrada: {}", applicationId);
                throw new IllegalArgumentException("Solicitud no encontrada: " + applicationId);
            }
            
            LoanApplication updated = existing.withStatus(newStatus);

            applicationStore.put(updated.getApplicationNumber(), updated);
            idStore.put(applicationId, updated);
            
            LOG.info("Estado actualizado exitosamente: {} -> {}", applicationId, newStatus);
            return updated;
        });
    }

    @Override
    public CompletionStage<LoanApplication> getApplicationById(UUID applicationId) {
        LOG.debug("Obteniendo solicitud por ID: {}", applicationId);
        return CompletableFuture.completedFuture(idStore.get(applicationId));
    }

    @Override
    public CompletionStage<Applicant> getApplicantForApplication(UUID applicationId) {
        LOG.debug("Obteniendo solicitante para aplicación: {}", applicationId);
        return CompletableFuture.completedFuture(applicantStore.get(applicationId));
    }

    private synchronized String generateApplicationNumber() {
        applicationCounter++;
        return String.format("LOAN-%d-%04d", 
                System.currentTimeMillis() % 10000, 
                applicationCounter);
    }
}
