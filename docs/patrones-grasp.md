# Patrones GRASP — Guía aplicada a este reto

> **GRASP** = *General Responsibility Assignment Software Patterns* (Craig Larman, *Applying UML and Patterns*).
> No son patrones de código como los del GoF (Strategy, Factory...). Son **criterios para decidir QUIÉN hace QUÉ**.
> La pregunta que responden siempre es la misma: *"¿a qué clase le asigno esta responsabilidad?"*

Analogía de obra: GRASP no te dice cómo hacer una pared, te dice **qué oficio se encarga de cada cosa**.
El electricista no hace la plomería, y el que tiene los planos de la instalación es el que decide dónde va el enchufe.

Son 9 patrones. El reto pide **mínimo 2 bien justificados**: es mejor 3 bien aplicados que 9 forzados.

---

## 1. Experto en Información (Information Expert)

**Qué dice:** asigná la responsabilidad a la clase que **tiene la información** necesaria para cumplirla.

**Por qué:** si la lógica vive lejos de los datos, otra clase tiene que pedirle todo con getters
(`applicant.getX()`, `applicant.getY()`...) y decidir por ella. Eso se llama **Feature Envy**: acopla y duplica.

**Dónde aplica en este reto:**
- `Applicant` tiene edad, ingreso y score → **él** responde `meetsMinimumAgeRequirement()`,
  `hasSufficientIncome()` y `hasAcceptableCreditScore()`. Ya está parcialmente aplicado.
- `LoanApplication` tiene su estado → **ella** responde `isApproved()` y `isPending()`, y debería
  validar sus propias transiciones de estado (¿se puede pasar de REJECTED a APPROVED?).

**Error común:** creer que Experto = "meter todo en la entidad". Si la regla necesita datos externos
(deudas vencidas desde un buró), el experto **no** es `Applicant`, porque él no tiene esa información.

**Pregunta para tu diseño:** las constantes `18`, `600` y `4` están duplicadas en el service y en el adapter.
¿Quién es el experto de *la política de elegibilidad*?

---

## 2. Creador (Creator)

**Qué dice:** la clase B debe crear instancias de A si B **contiene** A, **agrega** A, **registra** A,
**usa estrechamente** A o **tiene los datos de inicialización** de A.

**Por qué:** centraliza la construcción. Si cinco lugares hacen `new LoanApplication(...)` con 11 argumentos,
cada cambio del constructor rompe en cinco lugares, y cada uno puede dejar el objeto en un estado inválido.

**Dónde aplica en este reto:**
- ¿Quién tiene los datos para crear una `LoanApplication`? El que tiene el `Applicant`, el monto **y** el
  resultado de la evaluación. Esa es tu candidata a Creadora.
- El factory method `LoanApplication.create(...)` es una forma válida, porque la clase se crea a sí misma
  con invariantes garantizadas.

**Error común:** que el **controller** o el **adapter de persistencia** creen entidades de dominio.
Hoy el adapter arma una `LoanApplication` nueva **y le cambia el número y el estado**. ¿Es el registro
el que tiene que decidir eso?

---

## 3. Controlador (Controller)

**Qué dice:** el primer objeto **detrás de la UI/API** que recibe y coordina un evento del sistema.
**No** hace la lógica: la **delega**.

**Por qué:** desacopla la capa de presentación (HTTP, JSON) de la de negocio. Si mañana entra una
solicitud por Kafka en vez de REST, el caso de uso no cambia.

**Dónde aplica en este reto:**
- `LoanController` (Controlador de fachada): recibe el request, lo traduce al dominio y delega.
- `RegisterLoanUseCase` (Controlador de caso de uso): coordina "evaluar → crear → registrar".

**Error común:** el **Controlador inflado**, que valida, calcula, persiste y además arma la respuesta.
Mirá también el `.get()` bloqueante que hace hoy el controller: ¿qué le pasa al thread HTTP con 1 500 req/s?

---

## 4. Bajo Acoplamiento (Low Coupling)

**Qué dice:** asigná responsabilidades de forma que las clases **dependan lo menos posible** entre sí.

**Por qué:** cuanto menos sabe A de B, menos se rompe A cuando B cambia. Además es más fácil de testear,
porque mockeás una interfaz y no medio sistema.

**Dónde aplica en este reto:**
- Los **puertos** (`LoanEvaluationPort`, `LoanRegistrationPort`): el dominio depende de interfaces, no de
  Postgres ni de un cliente HTTP.
- **Ojo:** `RegisterLoanUseCase` (capa application) importa excepciones de `infrastructure.exception`.
  ¿Hacia dónde apunta esa dependencia? ¿Respeta la regla de hexagonal?

**Error común:** creer que "tengo interfaces, entonces tengo bajo acoplamiento". Si la interfaz expone
tipos de infraestructura, el acoplamiento sigue ahí, solo que disfrazado.

---

## 5. Alta Cohesión (High Cohesion)

**Qué dice:** cada clase hace **una cosa** y todo lo que tiene está relacionado con esa cosa.

**Por qué:** una clase cohesiva se entiende, se testea y se cambia por **una sola razón** (es primo
hermano del SRP de SOLID).

**Dónde aplica en este reto:**
- `LoanEvaluationService` hoy evalúa, arma el motivo del rechazo, maneja timeouts y tiene fallback.
  ¿Son una sola responsabilidad o tres?
- `LoanEvaluationAdapter` simula el buró **y además** vuelve a calcular la elegibilidad con las mismas reglas.

**Error común:** partir en tantas clases que nadie entiende el flujo. Cohesión no es "una clase por método".

> Bajo Acoplamiento y Alta Cohesión casi siempre se evalúan **juntos**: son el par "evaluativo" de GRASP.
> Los otros patrones son formas de lograrlos.

---

## 6. Polimorfismo (Polymorphism)

**Qué dice:** cuando el comportamiento **varía según el tipo**, asignalo a los tipos con una interfaz común,
en vez de usar `if/switch` sobre el tipo.

**Por qué:** agregar un caso nuevo = agregar una clase, sin tocar las existentes (es el OCP de SOLID).

**Dónde aplica en este reto:**
- **Reglas de elegibilidad**: `AgeRule`, `IncomeRule`, `CreditScoreRule`, `OverdueDebtRule`, todas
  implementando una interfaz `EligibilityRule`. El motor recorre la lista.
- Reglas distintas según `EmploymentStatus` (un independiente podría necesitar otro multiplicador de ingreso).

**Error común:** aplicar polimorfismo "por si acaso" cuando hay una sola variante. Eso es sobreingeniería.
Justificalo con una variación **real** del negocio.

---

## 7. Fabricación Pura (Pure Fabrication)

**Qué dice:** cuando ninguna clase del dominio es buena candidata (porque se rompería la cohesión),
**inventá** una clase que no representa nada del negocio pero que sí agrupa la responsabilidad.

**Por qué:** si metés la persistencia en `LoanApplication`, la entidad pasa a saber de SQL. Mejor una clase
artificial que se encargue de eso.

**Dónde aplica en este reto:**
- `LoanRegistrationAdapter` / repositorio: no existe un "repositorio" en el negocio bancario, es una fabricación.
- Un `ApplicationNumberGenerator` (generar la clave de idempotencia **no** es responsabilidad de `Applicant`).
- Un `EvaluationReasonBuilder` si el armado del motivo crece.

**Error común:** abusar del patrón y terminar con un dominio anémico, donde todo son `XxxService`
y las entidades no tienen comportamiento.

---

## 8. Indirección (Indirection)

**Qué dice:** poné un **intermediario** entre dos componentes para que no se acoplen directamente.

**Por qué:** el intermediario absorbe los cambios y permite meter comportamiento en el medio
(reintentos, colas, caché) sin tocar a ninguno de los dos extremos.

**Dónde aplica en este reto:**
- Los **puertos** son indirección: el caso de uso no conoce el adapter.
- Una **cola o buffer** entre la evaluación y el registro para absorber picos de 1 500 req/s
  (este es el pedido de la Fase 4).
- El circuit breaker / retry es indirección en la llamada al motor externo.

**Error común:** cada capa de indirección suma latencia y complejidad. Con una cola **perdés la respuesta
síncrona**: ¿qué le contestás al cliente? Esa decisión tiene que estar justificada.

---

## 9. Variaciones Protegidas (Protected Variations)

**Qué dice:** identificá los **puntos de cambio o inestabilidad** y envolvelos con una interfaz estable.

**Por qué:** el cambio va a llegar, la cuestión es que no se propague por todo el sistema.

**Dónde aplica en este reto:**
- El **motor de evaluación externo** es inestable (timeout >2 s, caídas) → `LoanEvaluationPort` + resiliencia.
- La **base de datos** puede cambiar → `LoanRegistrationPort`.
- Las **reglas de crédito** cambian con la política del banco → reglas polimórficas o configuración.

**Error común:** proteger cosas que nunca van a variar, lo que es YAGNI. Protegé lo que tiene
evidencia de cambio.

> Variaciones Protegidas es el patrón "paraguas": Polimorfismo, Indirección y Fabricación Pura
> son formas concretas de lograrlo.

---

## Mapa rápido: patrón → fase del reto

| Fase | Patrones que la propia consigna sugiere | Candidatos adicionales |
|------|-----------------------------------------|------------------------|
| 1. Diseño | Experto en Información, Alta Cohesión / Bajo Acoplamiento | Variaciones Protegidas |
| 2. Motor de evaluación | Creador | Polimorfismo (reglas), Fabricación Pura |
| 3. Registro | Controlador | Fabricación Pura (repositorio, generador de número) |
| 4. Integración y carga | Polimorfismo, Indirección | Variaciones Protegidas |

## Cómo justificar un patrón en la entrega

Para cada patrón que declares, respondé estas 4 cosas (son las "dimensiones evaluadas" del README):

1. **Qué problema concreto** de ESTE sistema resolvía.
2. **Dónde** lo aplicaste (clase/método).
3. **Qué alternativa** descartaste y por qué.
4. **Qué costo** tiene (todo patrón tiene un costo: más clases, más indirección, más latencia).

Si no podés responder el punto 4, todavía no entendiste el patrón. Te lo digo en serio.

## Recursos

- Craig Larman — *Applying UML and Patterns*, caps. 17 y 25 (la fuente original de GRASP).
- Martin Fowler — *Anemic Domain Model* (el error clásico al malinterpretar Fabricación Pura).
- Alistair Cockburn — *Hexagonal Architecture* (cómo los puertos son Indirección + Variaciones Protegidas).
