package com.pragma.loanprocessing.domain.port;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

import com.pragma.loanprocessing.domain.model.Applicant;
import com.pragma.loanprocessing.domain.model.LoanApplication;

/**
 * Interfaz que define el puerto para registrar solicitudes de préstamo.
 * Aplica el patrón Controlador: esta interfaz actúa como el punto de entrada
 * para operaciones de registro de solicitudes.
 */
public interface LoanRegistrationPort {
    /**
     * Registra una nueva solicitud de préstamo en el sistema.
     * @param loanApplication solicitud de préstamo a registrar
     * @return CompletionStage<LoanApplication> que resuelve a la solicitud registrada con su ID generado
     */
    CompletionStage<LoanApplication> registerLoanApplication(LoanApplication loanApplication);

    /**
     * Verifica si ya existe una solicitud con el mismo número de solicitud.
     * @param applicationNumber número de solicitud a verificar
     * @return CompletionStage<Boolean> que resuelve a true si existe, false en caso contrario
     */
    CompletionStage<Boolean> existsByApplicationNumber(String applicationNumber);

    /**
     * Actualiza el estado de una solicitud existente.
     * @param applicationId ID de la solicitud
     * @param newStatus nuevo estado de la solicitud
     * @return CompletionStage<LoanApplication> que resuelve a la solicitud actualizada
     */
    CompletionStage<LoanApplication> updateApplicationStatus(UUID applicationId, LoanApplication.Status newStatus);

    /**
     * Obtiene una solicitud por su ID.
     * @param applicationId ID de la solicitud
     * @return CompletionStage<LoanApplication> que resuelve a la solicitud encontrada
     */
    CompletionStage<LoanApplication> getApplicationById(UUID applicationId);

    /**
     * Obtiene el solicitante asociado a una solicitud.
     * @param applicationId ID de la solicitud
     * @return CompletionStage<Applicant> que resuelve al solicitante asociado
     */
    CompletionStage<Applicant> getApplicantForApplication(UUID applicationId);
}
