package com.pragma.loanprocessing.infrastructure.exception;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/** Traduce las excepciones de la aplicación a respuestas HTTP. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja errores del motor de evaluación.
     * @param ex excepción lanzada
     * @param request petición en curso
     * @return respuesta 503
     */
    @ExceptionHandler(LoanEvaluationException.class)
    public ResponseEntity<ErrorResponse> handleLoanEvaluationException(
            LoanEvaluationException ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "EVALUATION_ERROR",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", ""),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Maneja solicitudes duplicadas.
     * @param ex excepción lanzada
     * @param request petición en curso
     * @return respuesta 409
     */
    @ExceptionHandler(DuplicateLoanException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateLoanException(
            DuplicateLoanException ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_APPLICATION",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", ""),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Maneja errores de validación de los datos de entrada.
     * @param ex excepción lanzada
     * @param request petición en curso
     * @return respuesta 400 con el detalle por campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null 
                                ? error.getDefaultMessage() 
                                : "Invalid value",
                        (existing, replacement) -> existing
                ));

        ValidationErrorResponse errorResponse = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Error de validación en los datos de entrada",
                request.getDescription(false).replace("uri=", ""),
                LocalDateTime.now(),
                errors
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja argumentos inválidos.
     * @param ex excepción lanzada
     * @param request petición en curso
     * @return respuesta 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_ARGUMENT",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", ""),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja cualquier error no contemplado.
     * @param ex excepción lanzada
     * @param request petición en curso
     * @return respuesta 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "Error interno del servidor: " + ex.getMessage(),
                request.getDescription(false).replace("uri=", ""),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Cuerpo de respuesta de error. */
    public record ErrorResponse(
            int status,
            String code,
            String message,
            String path,
            LocalDateTime timestamp
    ) { }

    /** Cuerpo de respuesta de error de validación. */
    public record ValidationErrorResponse(
            int status,
            String code,
            String message,
            String path,
            LocalDateTime timestamp,
            Map<String, String> fieldErrors
    ) { }
}
