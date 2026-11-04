package com.pragma.loanprocessing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Entidad de dominio que representa al solicitante de un préstamo.
 * Aplica el patrón Experto en Información: esta clase contiene toda la lógica
 * relacionada con la validación de los datos del solicitante.
 */
public record Applicant(
    @NotNull(message = "El ID del solicitante es obligatorio")
    UUID id,

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    String fullName,

    @NotBlank(message = "El número de identificación es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9]{5,20}$",
        message = "El número de identificación debe ser alfanumérico entre 5 y 20 caracteres")
    String identificationNumber,

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser en el pasado")
    LocalDate birthDate,

    @NotNull(message = "El ingreso mensual es obligatorio")
    @Positive(message = "El ingreso mensual debe ser positivo")
    BigDecimal monthlyIncome,

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    String email,

    @NotBlank(message = "El número de teléfono es obligatorio")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "El número de teléfono debe contener entre 10 y 15 dígitos")
    String phoneNumber,

    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
    String address,

    @NotNull(message = "El estado de empleo es obligatorio")
    EmploymentStatus employmentStatus,

    @NotNull(message = "El puntaje crediticio es obligatorio")
    @Min(value = 300, message = "El puntaje crediticio mínimo es 300")
    @Max(value = 850, message = "El puntaje crediticio máximo es 850")
    int creditScore,

    @NotNull(message = "La fecha de solicitud es obligatoria")
    LocalDate applicationDate
) {
    /**
     * Calcula la edad del solicitante en años.
     * @return edad en años
     */
    public int calculateAge() {
        return LocalDate.now().getYear() - birthDate.getYear();
    }

    /**
     * Verifica si el solicitante cumple con la edad mínima para solicitar un préstamo.
     * @param minimumAge edad mínima requerida
     * @return true si cumple con la edad mínima, false en caso contrario
     */
    public boolean meetsMinimumAgeRequirement(int minimumAge) {
        return calculateAge() >= minimumAge;
    }

    /**
     * Verifica si el solicitante tiene un ingreso suficiente basado en el monto del préstamo solicitado.
     * @param loanAmount monto del préstamo solicitado
     * @param incomeMultiplier multiplicador de ingreso (ej: 0.3 para 30% del ingreso)
     * @return true si el ingreso es suficiente, false en caso contrario
     */
    public boolean hasSufficientIncome(BigDecimal loanAmount, BigDecimal incomeMultiplier) {
        BigDecimal requiredIncome = loanAmount.multiply(incomeMultiplier);
        return monthlyIncome.compareTo(requiredIncome) >= 0;
    }

    /**
     * Verifica si el solicitante tiene un puntaje crediticio aceptable.
     * @param minimumScore puntaje mínimo requerido
     * @return true si el puntaje es aceptable, false en caso contrario
     */
    public boolean hasAcceptableCreditScore(int minimumScore) {
        return creditScore >= minimumScore;
    }

    /**
     * Enum que representa el estado de empleo del solicitante.
     */
    public enum EmploymentStatus {
        EMPLOYED,
        SELF_EMPLOYED,
        UNEMPLOYED,
        RETIRED
    }
}
