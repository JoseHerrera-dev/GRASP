# Diagrama de clases — Sistema de solicitudes de préstamo

Dos vistas del mismo sistema:

- **A. Modelo de dominio** — entidades, value objects, enums y sus relaciones.
- **B. Capas y puertos** — cómo se conectan casos de uso, puertos y adaptadores (arquitectura hexagonal).

---

## A. Modelo de dominio

```mermaid
%%{init: {'themeVariables': {'fontSize': '18px'}}}%%
classDiagram
    direction TB

    class Applicant {
        -UUID id
        -String fullName
        -String identificationNumber
        -LocalDate birthDate
        -BigDecimal monthlyIncome
        -String email
        -String phoneNumber
        -String address
        -EmploymentStatus employmentStatus
        +calculateAge(LocalDate referenceDate) int
        +meetsMinimumAgeRequirement(int minimumAge, LocalDate referenceDate) boolean
    }

    class EmploymentStatus {
        <<enumeration>>
        EMPLOYED
        SELF_EMPLOYED
        UNEMPLOYED
        RETIRED
    }

    class LoanApplication {
        -int MAX_EVALUATION_ATTEMPTS$
        -Duration ORPHAN_THRESHOLD$
        -UUID id
        -ApplicationNumber applicationNumber
        -Applicant applicant
        -BigDecimal requestedAmount
        -int termMonths
        -Status status
        -int evaluationAttempts
        -Instant createdAt
        -Instant evaluationStartedAt
        -EvaluationResult evaluationResult
        +receive(ApplicationNumber number, Applicant applicant, BigDecimal amount, int termMonths, Instant now)$ LoanApplication
        +startEvaluation(Instant now) void
        +completeEvaluation(CreditReport report, CreditPolicy policy, LocalDate today, Instant now) Optional~Loan~
        +markEvaluationFailed() void
        +isOrphaned(Instant now) boolean
        +isTerminal() boolean
        +hasSufficientIncome(CreditPolicy policy) boolean
    }

    class Status {
        <<enumeration>>
        RECEIVED
        EVALUATING
        APPROVED
        REJECTED
        EVALUATION_FAILED
        EVALUATION_EXHAUSTED
    }

    class ApplicationNumber {
        <<ValueObject>>
        -String value
        +generate(String identificationNumber, BigDecimal amount, int termMonths, LocalDate businessDay, String secret)$ ApplicationNumber
    }

    class EvaluationResult {
        <<ValueObject>>
        -int creditScore
        -boolean hasOverdueDebts
        -boolean eligible
        -String reason
        -Instant evaluatedAt
        +hasAcceptableCreditScore(int minimumScore) boolean
    }

    class CreditReport {
        <<ValueObject>>
        -int creditScore
        -boolean hasOverdueDebts
    }

    class CreditPolicy {
        +int MINIMUM_AGE$
        +int MINIMUM_CREDIT_SCORE$
        +BigDecimal MAX_INSTALLMENT_TO_INCOME_RATIO$
    }

    class Loan {
        -UUID id
        -UUID loanApplicationId
        -BigDecimal approvedAmount
        -int termMonths
        -Instant createdAt
    }

    class InvalidStateTransitionException {
        <<exception>>
    }

    LoanApplication "0..*" --> "1" Applicant : solicitante
    Applicant --> EmploymentStatus
    LoanApplication --> Status
    LoanApplication "1" *-- "1" ApplicationNumber
    LoanApplication "1" *-- "0..1" EvaluationResult
    Loan "0..1" --> "1" LoanApplication : origen
    LoanApplication ..> Loan : crea al aprobarse
    LoanApplication ..> CreditReport : usa
    LoanApplication ..> CreditPolicy : usa
    LoanApplication ..> InvalidStateTransitionException : lanza
```

### Patrones aplicados en el modelo de dominio

| Clase | Patrón | Por qué |
|-------|--------|---------|
| `Applicant` | Experto en Información | Responde sobre su edad: tiene la fecha de nacimiento |
| `LoanApplication` | Experto en Información | Capacidad de pago y transiciones de estado: tiene monto, plazo, estado y contiene al solicitante |
| `LoanApplication` | Creador | Crea el `Loan` al aprobarse: tiene todos sus datos de inicialización |
| `EvaluationResult` | Experto en Información | Score aceptable: tiene el score informado por el buró |
| `EvaluationResult` | Alta Cohesión | Agrupa los datos de la evaluación; es `null` mientras no hay resultado |
| `Loan` | Bajo Acoplamiento | Llega al `Applicant` a través de su solicitud, sin referencia directa |
| `Status`, `EmploymentStatus` | — | Anidados en su entidad; en el diagrama se muestran aparte porque Mermaid no anida clases |

### Ciclo de vida de `LoanApplication`

```mermaid
stateDiagram-v2
    [*] --> RECEIVED : se registra
    RECEIVED --> EVALUATING : arranca la evaluación / reintento de huérfana > 30 s
    EVALUATING --> APPROVED : elegible → crea Loan
    EVALUATING --> REJECTED : no elegible
    EVALUATING --> EVALUATION_FAILED : timeout, error o huérfana > 30 s, quedan intentos
    EVALUATING --> EVALUATION_EXHAUSTED : timeout, error o huérfana > 30 s, 3.er intento
    EVALUATION_FAILED --> EVALUATING : el cliente reintenta
    APPROVED --> [*]
    REJECTED --> [*]
    EVALUATION_EXHAUSTED --> [*]
```

---

## B. Capas y puertos

Todas las dependencias apuntan hacia `domain`; ninguna sale de él.

```mermaid
classDiagram
    direction LR

    namespace infrastructure {
        class LoanController {
            +applyForLoan(LoanApplicationRequest request) ResponseEntity
        }
        class GlobalExceptionHandler
        class CreditBureauAdapter
        class LoanRegistrationAdapter
        class LoanAdapter
    }

    namespace application {
        class RegisterLoanUseCase {
            +execute(Applicant applicant, BigDecimal amount, int termMonths) LoanApplication
        }
        class LoanEvaluationService {
            +evaluate(ApplicationNumber number) LoanApplication
        }
    }

    namespace domain {
        class CreditBureauPort {
            <<interface>>
            +fetchCreditReport(String identificationNumber) CreditReport
        }
        class LoanRegistrationPort {
            <<interface>>
            +save(LoanApplication application) LoanApplication
            +findByApplicationNumber(ApplicationNumber number) Optional~LoanApplication~
            +findById(UUID id) Optional~LoanApplication~
        }
        class LoanPort {
            <<interface>>
            +save(Loan loan) Loan
        }
        class InvalidStateTransitionException {
            <<exception>>
        }
    }

    LoanController ..> RegisterLoanUseCase : delega
    RegisterLoanUseCase ..> LoanRegistrationPort : usa
    LoanEvaluationService ..> CreditBureauPort : usa
    LoanEvaluationService ..> LoanRegistrationPort : usa
    LoanEvaluationService ..> LoanPort : usa
    CreditBureauAdapter ..|> CreditBureauPort : implementa
    LoanRegistrationAdapter ..|> LoanRegistrationPort : implementa
    LoanAdapter ..|> LoanPort : implementa
    GlobalExceptionHandler ..> InvalidStateTransitionException : traduce a HTTP

    note for CreditBureauPort "Indirección + Variaciones Protegidas: aísla el buró inestable; solo consulta datos"
    note for LoanRegistrationPort "Indirección + Variaciones Protegidas: aísla la base de datos"
    note for LoanPort "Alta Cohesión: puerto propio para Loan"
    note for LoanController "Controlador (fachada): traduce HTTP y delega"
```

---

## Fuera de alcance

- Saldo, pagos y mora del préstamo (gestión de cartera).
- Monto aprobado distinto del pedido (contraoferta).
- Tasa de interés: la cuota se aproxima como `monto / plazo`.
- Historial de transiciones de estado.
