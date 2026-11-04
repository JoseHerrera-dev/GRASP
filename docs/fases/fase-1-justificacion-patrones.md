# Fase 1 — Justificación de patrones GRASP

Cada patrón responde las 4 preguntas de `docs/patrones-grasp.md`: problema · dónde · alternativa descartada · costo.

Se eligen **4 patrones** con justificación completa (el reto pide mínimo 2). Es preferible defender
pocos a fondo que nombrar los 9 a medias.

---

## 1. Experto en Información

**Problema.** En el código base, la lógica de elegibilidad estaba lejos de los datos que usa:
las reglas se aplicaban en `LoanEvaluationService` y se repetían en `LoanEvaluationAdapter`,
que le pedían todo a `Applicant` por getters. `Applicant` además tenía un método
(`hasSufficientIncome`) que necesitaba datos que no eran suyos (el monto del préstamo).

**Dónde se aplica.**

- `Applicant` responde sobre su edad (`calculateAge`, `meetsMinimumAgeRequirement`): tiene la fecha de nacimiento y la fecha actual la recibe como parametro (`referenceDate`), no la saca del sistema, asi el calculo siempre da lo mismo para la misma entrada y se puede testear sin mockear el reloj.

- `LoanApplication` responde la capacidad de pago (`hasSufficientIncome`): tiene el monto, el plazo y contiene al `Applicant` que maneja los datos ingresados por el solicitante, el ingreso no es suyo directamente pero lo alcanza por composicion.

- `LoanApplication` valida sus propias transiciones de estado: es quien conoce su estado actual y es el que se modifica al cambiar el estado, por eso es quien decide si una transicion es valida o no, pero no ejecuta las acciones alrededor (llamar al buró, guardar), eso lo orquesta el caso de uso.

- `EvaluationResult` responde si el score es aceptable: tiene el score informado por el buró, eso es lo que lo hace el experto.
  Aparte, por seguridad el score no lo puede mandar el solicitante, si lo manejara el applicant cualquiera podria definir su score, ademas era redundante como se evidencio en el codigo, habia dos fuentes de verdad (`Applicant.creditScore` y el `getCreditScore()` del puerto).

**Alternativas descartadas.**

- *Sacar los métodos de cálculo de `Applicant` a clases auxiliares (`ApplicantValidator`/`Utils`)*:
  la clase auxiliar tendría que pedirle todos los datos a `Applicant` (Feature Envy) generando un alto acoplamiento y `Applicant`
  quedaría sin comportamiento (modelo de dominio anémico) dejando sin sentido el modelo.

- *Dejar `hasSufficientIncome` en `Applicant`*: dos de sus tres datos (monto, plazo) no le pertenecen, `Applicant` no conocia a `LoanApplication`, el monto le llegaba por parametro y el plazo no lo tenia, entonces no es feature envy sino una responsabilidad mal asignada, estaba en una clase que no tenia la informacion para cumplirla bien.
  Al moverlo se vio que la formula estaba mal, `ingreso >= monto x 4` exigia ganar por mes cuatro veces el prestamo, se corrigio a cuota (monto / plazo) <= 30% del ingreso, que solo se puede calcular donde esta el plazo.

- *Dejar `creditScore` en `Applicant`*: el dato lo emite un tercero en un momento dado; en `Applicant`
  nacía vacío (`0`) y no quedaba la foto del score que se uso al momento de la evaluación, el score cambia con el tiempo y hay que guardar el que se uso para decidir.

**Costo.** La lógica de elegibilidad queda **repartida** entre tres clases (`Applicant`, `LoanApplication`,
`EvaluationResult`): para ver la regla completa hay que mirar varios lugares. Además, `LoanApplication`
concentra varias responsabilidades de su ciclo de vida y hay que vigilar que no se convierta en una clase Dios.

**Preguntas que te pueden hacer.**
- ¿Por qué `hasSufficientIncome` en `LoanApplication` y no en una regla separada?
 porque hoy es una sola regla y `LoanApplication` ya tiene el monto, el plazo y al applicant, sacarla a una clase aparte seria crear una clase que le pide todo a la solicitud, si aparecen mas reglas de credito ahi si se extraen a reglas de elegibilidad polimorficas que es lo que plantea la fase 4

- Si `LoanApplication` tiene 6 de las 10 reglas, ¿no rompe Alta Cohesión?
 hay una tension real y la tengo vigilada, casi todo lo que hace es su propio ciclo de vida, transiciones, intentos y huerfanas, eso es cohesivo, la excepcion es la capacidad de pago que es regla de credito y quedo ahi porque tiene los datos, ademas decide pero no orquesta, no llama al buró ni guarda nada, la cohesion no se mide contando metodos sino viendo si responden a un mismo proposito

- ¿Por qué el score no es de `Applicant` si habla de la persona?
 porque no lo genera la persona sino el buró en un momento dado, si fuera del applicant lo podria mandar el cliente y cualquiera pondria el score que quiera, y se perderia el valor que se uso para decidir, por eso vive en `EvaluationResult` como foto de la evaluacion

---

## 2. Creador

**Problema.** En el código base, `LoanApplication` se construía en varios lugares (el service, el use case,
el adapter de persistencia) y el adapter incluso le cambiaba el número y el estado al guardar.
Varios creadores significan varios lugares donde se puede romper una invariante o generar una incongruencia en funcion del dominio.

**Dónde se aplica.**
- La `LoanApplication` aprobada **crea** el `Loan`: tiene todos sus datos de inicialización
  (monto, plazo, referencia a sí misma) y la creación ocurre justo en la transición a `APPROVED`.

**Alternativas descartadas.**
- *Que el caso de uso o el adapter cree el `Loan`*: se rompe la invariante, solo una solicitud aprobada puede generar un prestamo, si lo crea el caso de uso cualquier codigo puede hacer `new Loan(...)` para una solicitud en cualquier estado y la regla depende de que nadie se equivoque, creandolo dentro de la transicion a `APPROVED` es imposible crear un `Loan` para una solicitud rechazada o en evaluacion.
- *`Loan` solo en el diagrama, sin código*: el diagrama y la implementación no coincidirían, se descarta para que el diagrama concuerde y para entender correctamente el ciclo de vida y la causalidad de las acciones del usuario, primero el aplicante solicita el prestamo, nace loanApplication, si cumple las condiciones genera el `Loan` que referencia a la solicitud de la que nacio, al applicant se llega a traves de la solicitud, el `Loan` no lo conoce directo.

**Costo.** `LoanApplication` conoce a `Loan` (un acoplamiento más). Y como `Loan` tiene su propio puerto, aprobar la solicitud y guardar el préstamo son dos escrituras que tienen que ir en la **misma
transacción**.

**Preguntas que te pueden hacer.**
- ¿Cuál de los criterios de Creador aplica (contiene, agrega, registra, usa estrechamente, tiene los datos de inicialización)?
 tiene los datos de inicializacion, LA (LoanApplication) tiene el monto, el plazo y su propio id que es la referencia que guarda el `Loan`, no aplica contiene porque LA no guarda el `Loan`, lo devuelve (`Optional<Loan>`) desde `completeEvaluation` y es el `Loan` el que referencia a LA, ademas la creacion es parte de su propia transicion a `APPROVED` y eso protege la invariante

- ¿Qué pasa si se guarda la solicitud aprobada pero falla el guardado del `Loan`?
 son parte de una misma transaccion si falla una todas fallan y se revierten, la solicitud no queda aprobada sin prestamo, queda como estaba antes y cuando el cliente reintenta se trata como cualquier reintento, ojo que el codigo base usa `CompletableFuture.supplyAsync` y `@Transactional` no cubre el codigo que corre en otro hilo, eso se resuelve en la fase 3

---

## 3. Alta Cohesión y Bajo Acoplamiento

**Problema.** En el código base, `LoanApplication` tenía campos sueltos de la evaluación (`evaluationResult`,
`evaluationReason`, `creditScore`, `hasOverdueDebts`); la capa `application` importaba excepciones de
`infrastructure` (dependencia invertida y vulneracion de la arquitectura Hexagonal); y el puerto de registro mezclaba persistencia de solicitudes con
consultas del solicitante.

**Dónde se aplica.**
- **Cohesión:** `EvaluationResult` agrupa en un value object los datos que cambian juntos.
  `Loan` tiene su propio puerto, separado del de solicitudes.
- **Acoplamiento:** `Loan` conoce solo a su solicitud, no al `Applicant`. Las excepciones de negocio
  pasan al dominio y las dependencias apuntan hacia adentro.

**Alternativas descartadas.**
- *Un paquete `enums/` para centralizar los enums*: agrupa por tipo técnico y no por concepto; el paquete
  tendría cohesión nula.
- *`Loan` con referencia directa a `Applicant`*: más cómodo para consultar, pero suma una dependencia.
- *Mantener las excepciones en `infrastructure`*: la capa `application` no compilaría sin la infraestructura.

**Costo.** Más clases y archivos (`EvaluationResult`, un puerto más, excepciones de dominio). Para saber
de quién es un préstamo hay un salto extra (`Loan → LoanApplication → Applicant`).

**Preguntas que te pueden hacer.**
- ¿Cómo medís que una clase es cohesiva?
 se mide en funcion de la relaccion entre sus metodos y sus datos contenidos, si los metodos afectan a sus datos, si los datos son utilizables con sus metodos mayoritariamente se dice que esa clase es cohesiva, todo va hacia el mismo objetivo, no se mide contando metodos, una clase con muchos metodos puede ser cohesiva si todos responden al mismo proposito

- ¿Bajo Acoplamiento significa "tener interfaces"?
 no, significa tener pocas dependencias, debiles y estables, se puede tener interfaces en todos lados y seguir muy acoplado si una clase depende de muchas, el ejemplo es `Loan`, referencia a `LoanApplication` y no a `Applicant`, se bajo el acoplamiento quitando una dependencia y no hay ninguna interfaz de por medio, que el dominio no dependa de la implementacion concreta (mañana cambia la bd y el dominio sigue funcionando) es inversion de dependencias y variaciones protegidas, que se logran con interfaces, ayudan al bajo acoplamiento pero no son lo mismo

- ¿Por qué no un paquete `enums/`?
  porque se estaba primando una comodidad tecnica sobre el domino, al dia de hoy solo la clase que tiene el enum lo utiliza entonces no hay razon para sacarlo de ahi, si en un futuro otro concepto depende de ese enum se contempla sacarlo pero al paquete del concepto al que pertenece, nunca a uno por tipo tecnico, lo que cambia junto vive junto (principio de cierre comun)

---

## 4. Indirección y Variaciones Protegidas

**Problema.** El motor de evaluación externo es **inestable** (timeouts > 2 s, caídas), la base de datos
puede cambiar y la política de crédito la ajusta el banco. Si el dominio dependiera directamente de
esas tecnologías, cada cambio se propagaría a las reglas de negocio cosa que no es coherente con la arquitectuara usada ademas que involucraria coste tecnico a futuro.

**Dónde se aplica.**
- **Puertos** (`CreditBureauPort`, `LoanRegistrationPort`, puerto de `Loan`): el caso de uso habla con
  una interfaz del dominio; la tecnología concreta vive en un adapter de infraestructura, al dominio solo le interesa que se cumpla el contrato, es independiente de la tecnologia que lo resuelve.
- El puerto del buró solo **consulta datos**; las reglas no salen del dominio.
- La política de crédito vive en un solo lugar (`CreditPolicy`), lista para construirse desde
  configuración si hace falta.

**Alternativas descartadas.**
- *Que el puerto evalúe la elegibilidad* (`evaluateEligibility`, como en el código base): las reglas
  quedaban duplicadas en el adapter vulnerando DRY, ademas carente de sentido el codigo del dominio debe validar la elegibilidad y depende de infraestructura vulnera la arquitectura.
- *Llamar directamente al cliente HTTP o al repositorio desde el caso de uso*: el dominio dependería de la tecnología.

**Costo.** Una interfaz y un adapter por cada dependencia externa: más archivos y un salto más al leer el
flujo. Cada indirección agrega un punto donde hay que traducir datos (adapter ↔ dominio).

**Preguntas que te pueden hacer.**
- ¿Qué diferencia hay entre Indirección y Variaciones Protegidas?
 variaciones protegidas es el objetivo (el que), identificar los puntos inestables y protegerlos para que un cambio no se propague por el sistema, indireccion es un mecanismo (el como), poner un intermediario, es una de las formas de lograr variaciones protegidas pero no la unica, polimorfismo tambien lo logra, puede haber indireccion sin variaciones protegidas, el `LoanController` es intermediario entre HTTP y el caso de uso sin proteger una variacion concreta, el puerto del buró aplica los dos porque el buró es inestable y el puerto lo aisla

- ¿Protegerías algo que no tiene evidencia de cambio?
 no, eso es YAGNI, proteger algo sin evidencia de cambio solo agrega costo, por ejemplo `CreditPolicy` quedo con constantes y no como configuracion porque no habia evidencia de que los umbrales cambien seguido, la salida queda anotada por si algun dia cambian, se protege lo que tiene evidencia de cambio

- ¿Por qué el puerto del buró no evalúa elegibilidad?
 porque dependeriamos de la tecnologia para evaluar la elegibilidad y eso lo debe hacer el dominio, las reglas cambian por negocio y el buró cambia por tecnologia, si se juntan en el adapter este tiene dos razones para cambiar (SRP) y cualquier cambio de uno obliga a tocar el otro, ademas con las reglas en el dominio se testean sin mockear el buró
