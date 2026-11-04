package com.pragma.loanprocessing.infrastructure.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pragma.loanprocessing.application.usecase.RegisterLoanUseCase;
import com.pragma.loanprocessing.domain.model.Applicant;
import com.pragma.loanprocessing.domain.model.LoanApplication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Controlador REST para las solicitudes de préstamo. */
@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

    private static final Logger LOG = LoggerFactory.getLogger(LoanController.class);
    
    private final RegisterLoanUseCase registerLoanUseCase;

    /**
     * Crea el controlador con el caso de uso de registro.
     * @param registerLoanUseCase caso de uso de registro
     */
    public LoanController(RegisterLoanUseCase registerLoanUseCase) {
        this.registerLoanUseCase = registerLoanUseCase;
        LOG.info("LoanController inicializado");
    }

    /**
     * Recibe y registra una solicitud de préstamo.
     * @param request datos de la solicitud
     * @return solicitud registrada o error
     */
    @PostMapping("/apply")
    public ResponseEntity<LoanApplicationResponse> applyForLoan(
            @Valid @RequestBody LoanApplicationRequest request) {
        LOG.info("Recibida solicitud de préstamo para: {}", request.identificationNumber());
        
        try {
            Applicant applicant = new Applicant(
                    UUID.randomUUID(),
                    request.fullName(),
                    request.identificationNumber(),
                    request.dateOfBirth(),
                    request.monthlyIncome(),
                    request.email(),
                    request.phoneNumber(),
                    request.address(),
                    Applicant.EmploymentStatus.valueOf(request.employmentStatus()),
                    0,
                    LocalDate.now()
            );

            LoanApplication registered = registerLoanUseCase.execute(applicant, request.requestedAmount())
                    .toCompletableFuture().get();

            LoanApplicationResponse response = toResponse(registered, request.loanTermMonths());
            LOG.info("Solicitud registrada exitosamente: {}", registered.getApplicationNumber());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            LOG.error("Error al procesar solicitud de préstamo: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoanApplicationResponse(null, null, null, null, null, null, null, e.getMessage()));
        }
    }

    /**
     * Consulta una solicitud por su ID.
     * @param applicationId ID de la solicitud
     * @return solicitud encontrada
     */
    @GetMapping("/{applicationId}")
    public ResponseEntity<LoanApplicationResponse> getApplication(
            @PathVariable UUID applicationId) {
        LOG.debug("Consultando solicitud: {}", applicationId);
        return ResponseEntity.notFound().build();
    }

    /**
     * Consulta el estado de una solicitud por su número.
     * @param applicationNumber número de la solicitud
     * @return estado de la solicitud
     */
    @GetMapping("/status/{applicationNumber}")
    public ResponseEntity<Map<String, String>> getApplicationStatus(
            @PathVariable String applicationNumber) {
        LOG.debug("Consultando estado de solicitud: {}", applicationNumber);
        return ResponseEntity.ok(Map.of("status", "PENDING"));
    }

    private LoanApplicationResponse toResponse(LoanApplication application, Integer loanTermMonths) {
        return new LoanApplicationResponse(
                application.getId().toString(),
                application.getApplicationNumber(),
                application.getApplicant().identificationNumber(),
                application.getRequestedAmount(),
                application.getStatus().name(),
                application.getCreatedAt().toString(),
                loanTermMonths,
                null
        );
    }

    /** Datos de entrada de una solicitud de préstamo. */
    public record LoanApplicationRequest(
            @NotBlank String identificationNumber,
            @NotBlank String fullName,
            @NotBlank @Email String email,
            @NotBlank String phoneNumber,
            @NotBlank String address,
            @NotNull LocalDate dateOfBirth,
            @NotBlank String employmentStatus,
            @NotNull @Positive BigDecimal monthlyIncome,
            @NotNull @Positive BigDecimal requestedAmount,
            @NotNull @Positive Integer loanTermMonths,
            @NotBlank String loanPurpose
    ) { }

    /** Datos de salida de una solicitud de préstamo. */
    public record LoanApplicationResponse(
            String id,
            String applicationNumber,
            String identificationNumber,
            BigDecimal requestedAmount,
            String status,
            String applicationDate,
            Integer loanTermMonths,
            String errorMessage
    ) { }
}
