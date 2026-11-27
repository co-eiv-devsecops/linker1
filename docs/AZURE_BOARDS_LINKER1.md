|# Azure Boards — Linker1: enlaces seguros y contador de aperturas

## Para qué sirve este documento

Contiene una propuesta lista para cargar en Azure Boards para las dos funcionalidades elegidas por el equipo:

1. **Crear enlaces seguros y fáciles de entender.**
2. **Contar y consultar las aperturas de un enlace.**

También incluye historias para registrar las evidencias de cliente, Scrum, Lean, DevOps y entrega final que solicita la guía de Ingeniería de Software.

## Cómo usarlo

- `E1`, `F1.1`, `HU-01` y `T-01-01` son **referencias temporales de este documento**, no IDs asignados por Azure. Azure generará sus propios IDs.
- Crear los elementos con la jerarquía **Epic → Feature → User Story → Task**. En un proceso Scrum, `User Story` puede aparecer como `Product Backlog Item`.
- `Area Path`, `Iteration Path`, asignados, story points y fechas se dejan como campos editables porque el nombre real del proyecto, los sprints y el reparto no están confirmados aquí.
- Estado inicial sugerido: `New`. Cambiarlo solo según el trabajo y la evidencia reales.
- Prioridad `1`/`Must` es una propuesta para ordenar el backlog; el equipo debe confirmarla.
- **Regla de estimación acordada:** estimar cada subtarea en horas-persona; sumar las subtareas de una HU y dividir el total entre 8. El resultado de la HU se registra en días-persona (`Story Points`/`Effort`, según el proceso de Azure). Los totales de Feature y Épica son la suma de sus HU, también en días-persona.
- Las estimaciones de abajo son propuestas iniciales del backlog, no tiempo ya invertido ni compromiso de calendario. Si la subtarea ya está terminada, conservar su estimación original y registrar el trabajo real en el campo correspondiente; si Azure registra Remaining Work, poner 0 al terminar.
- La propuesta del equipo menciona el 26 de noviembre de 2026 como entrega. La guía oficial dice que rige la fecha comunicada por la docente; confirmar la fecha antes de llenar `Target Date`.
- No marcar como hechos entrevistas, ceremonias, pruebas, despliegues ni revisiones que no hayan ocurrido. No crear evidencia retroactiva.
- La guía adjunta de ejemplo describe SaludDeUna, MongoDB, NestJS, React Native, REThUS y JWT. Esos elementos **no aplican a Linker1** y no se trasladan a este backlog.

---

## 1. EPIC

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | Epic |
| Referencia temporal | E1 |
| Title | Linker1: creación segura y medición de aperturas |
| State | New |
| Area Path | `[NombreProyecto]\Linker1` (ajustar al proyecto Azure real) |
| Iteration Path | `[NombreProyecto]\[Sprint correspondiente]` |
| Priority | 1 — propuesta Must |
| Business Value | Ayudar al usuario objetivo a crear enlaces cortos seguros y comprensibles y a consultar si fueron abiertos; demostrar una mejora del proyecto heredado basada en evidencia de usuarios. |
| Risk | Alto para el contador: requiere persistencia, incremento por redirección, forma de consulta y actualización de verificaciones que esperan HTTP 301. |
| Effort | **9.0625 días-persona** propuestos (72.5 horas-persona en subtareas; total de HU dividido entre 8). |
| Tags | `linker1; trabajo-final; cliente; scrum; lean; devops` |
| Target Date | Confirmar con la fecha comunicada por la docente. La propuesta inicial menciona 2026-11-26. |

### Description

```text
Linker1 es un acortador de URL existente construido con Java 21, Javalin, SQLite y una interfaz web estática. Esta épica organiza el trabajo del curso para mejorar el recorrido principal y demostrar el proceso aplicado.

Objetivo del producto:
Permitir que el usuario objetivo cree un enlace corto seguro y fácil de compartir y consulte cuántas veces fue abierto, con un recorrido que pueda completar y entender sin ayuda innecesaria.

Alcance funcional elegido:
- Aceptar únicamente destinos HTTP o HTTPS y rechazar esquemas peligrosos o URL inválidas.
- Rechazar alias reservados o no disponibles y comunicar el motivo con mensajes claros en español.
- Registrar aperturas del enlace corto de manera persistente.
- Mostrar el conteo de aperturas mediante una consulta entendible para el usuario.

Trabajo de gestión y evaluación relacionado:
- Conectar las decisiones de producto con entrevistas/observación de usuarios, MVP, indicador de valor y matriz de océano azul.
- Mantener evidencia real de backlog, sprints, revisiones, retrospectivas y mejora Lean.
- Mantener trazabilidad de una historia hasta revisión, prueba, integración y demo reproducible.
- Preparar índice de evidencias, informe, sustentación, reflexión individual y video de respaldo.

Restricciones y decisiones pendientes:
- No se incluyen cuentas de usuario, login, dominio propio, analítica avanzada, QR ni ampliación de infraestructura.
- La ruta de consulta del conteo que aparece en esta propuesta es una decisión técnica sugerida: GET /link/{id}/stats. Confirmar su contrato con el equipo antes de implementarla.
- Sin token de gestión, cualquier persona que conozca el identificador corto podría consultar su conteo. Si el equipo considera que el conteo debe ser privado, deberá acordar e incluir autenticación por token como alcance adicional.
- Para contar aperturas repetidas con consistencia desde el servidor, se propone usar HTTP 302 en la redirección, ya que el comportamiento actual usa 301 y los navegadores pueden cachear las redirecciones permanentes.
- El código inicial cuenta como línea base; la evaluación del curso debe evidenciar trabajo y proceso realizados durante la asignatura.
```

### Epic Acceptance Criteria

```gherkin
Feature: Linker1 con creación segura y medición de aperturas

  Scenario: Usuario crea un enlace seguro y entiende el resultado
    Given el usuario introduce una URL HTTP o HTTPS válida
    When crea un enlace corto
    Then recibe un enlace corto funcional
    And la interfaz comunica el resultado y cualquier validación en español claro

  Scenario: El sistema rechaza destinos no permitidos
    Given el usuario introduce una URL con esquema distinto de HTTP o HTTPS
    When intenta crear el enlace
    Then la solicitud es rechazada con un error de validación
    And no se guarda el enlace

  Scenario: Usuario consulta aperturas registradas
    Given existe un enlace corto
    When una persona lo abre y luego se consulta su estadística
    Then el conteo persistido refleja las solicitudes de redirección recibidas

  Scenario: El equipo demuestra la historia de extremo a extremo
    Given una historia elegida y enlazada a su evidencia
    When el equipo presenta la entrega
    Then puede relacionar necesidad, prioridad, desarrollo, revisión, prueba, integración y demo
```

---

## 2. FEATURES

### F1.1 — Crear enlaces seguros y fáciles de entender

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | Feature |
| Referencia temporal | F1.1 |
| Title | Crear enlaces seguros y fáciles de entender |
| Parent | E1 — Linker1: creación segura y medición de aperturas |
| State | New |
| Area Path | `[NombreProyecto]\Linker1\Producto` |
| Iteration Path | `[NombreProyecto]\[Sprint correspondiente]` |
| Priority / MoSCoW | 1 / Must — confirmar con el equipo |
| Effort | **1.4375 días-persona** (11.5 horas-persona / 8). |
| Tags | `linker1; seguridad; urls; alias; ux; must` |

#### Description

```text
El usuario puede crear un enlace corto a un destino permitido, recibir una explicación clara cuando una URL o alias no sea aceptable y usar el flujo principal en español.

Alcance:
- Validar que el destino sea una URI absoluta con esquema HTTP o HTTPS y host válido.
- Rechazar javascript:, data:, file:, URL relativas y otros esquemas no HTTP(S).
- Rechazar alias reservados para rutas y archivos estáticos del proyecto, incluyendo healthz.
- Mantener la respuesta funcional para URLs y alias válidos.
- Mostrar mensajes claros en español para URL inválida, alias reservado/no válido, alias ocupado y éxito.
- Añadir o actualizar pruebas del servicio/rutas y verificaciones de API afectadas.

La interfaz actual tiene versiones v1 y v2; confirmar qué versión se usará en la demo y mantener coherencia en las interfaces que permanezcan accesibles.
```

#### Feature Acceptance Criteria

```gherkin
Feature: Crear enlaces seguros y fáciles de entender

  Scenario: URL HTTPS aceptada
    Given el usuario introduce una URL HTTPS absoluta con host válido
    When envía la solicitud de creación
    Then el sistema crea o devuelve el enlace corto correspondiente

  Scenario: URL HTTP aceptada
    Given el usuario introduce una URL HTTP absoluta con host válido
    When envía la solicitud de creación
    Then el sistema crea o devuelve el enlace corto correspondiente

  Scenario Outline: Esquema de URL rechazado
    Given el usuario introduce una URL con esquema <esquema>
    When envía la solicitud de creación
    Then el sistema devuelve un error de validación
    And no persiste el enlace

    Examples:
      | esquema    |
      | javascript |
      | data       |
      | file       |

  Scenario: Alias reservado rechazado
    Given el usuario intenta usar un alias reservado como healthz
    When envía la solicitud de creación
    Then el sistema rechaza el alias con un error claro

  Scenario: Error entendible en la interfaz
    Given la creación falla por URL o alias inválido
    When la interfaz recibe el error
    Then muestra una explicación breve en español
    And no expone una traza técnica al usuario
```

### F1.2 — Contar y consultar las aperturas

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | Feature |
| Referencia temporal | F1.2 |
| Title | Contar y consultar las aperturas de un enlace |
| Parent | E1 — Linker1: creación segura y medición de aperturas |
| State | New |
| Area Path | `[NombreProyecto]\Linker1\Backend` (interfaz en `\Frontend` si se divide por área) |
| Iteration Path | `[NombreProyecto]\[Sprint correspondiente]` |
| Priority / MoSCoW | 1 / Must — confirmar con el equipo |
| Effort | **2 días-persona** (16 horas-persona / 8); riesgo medio/alto por cambios de DB y redirect. |
| Tags | `linker1; metricas; sqlite; redirect; must` |

#### Description

```text
El sistema registra cada solicitud recibida al abrir un enlace corto y permite consultar el conteo para demostrar el valor del enlace.

Alcance propuesto:
- Guardar un contador entero por enlace en SQLite, inicializado en cero.
- Migrar de forma compatible una base SQLite preexistente que solo tiene id y url.
- Incrementar el contador para cada solicitud GET de redirección a un enlace existente.
- No incrementar por HEAD, consulta de estadísticas, enlaces inexistentes ni solicitudes que no redirigen.
- Exponer el conteo mediante GET /link/{id}/stats (ruta propuesta; confirmar antes de desarrollar).
- Mostrar el conteo en una vista sencilla después de crear el enlace o en la sección de consulta elegida por el equipo.
- Usar HTTP 302 para que las aperturas repetidas lleguen al servidor y puedan contarse; actualizar pruebas y checks que esperan 301.
- Configurar la redirección para no cachearse (`Cache-Control: no-store`) si se requiere contar cada solicitud GET recibida en el servidor.
- No incluir analítica avanzada, usuarios, segmentación, gráficas ni exportaciones.
- El contador representa solicitudes GET recibidas por el servidor; no identifica personas únicas ni distingue usuarios de bots.

Decisión de acceso: en esta versión el conteo se consulta por identificador corto y no hay token de propietario. Confirmar que el equipo acepta que quien conoce el identificador pueda consultar el conteo. Si no, crear una historia separada para token; no ocultar esta limitación.
```

#### Feature Acceptance Criteria

```gherkin
Feature: Contador de aperturas

  Scenario: Enlace nuevo inicia con cero aperturas
    Given se crea un enlace corto
    When se consulta su estadística antes de abrirlo
    Then el conteo es cero

  Scenario: GET de redirección incrementa el conteo
    Given existe un enlace corto con un conteo conocido
    When una persona solicita el enlace con GET
    Then el servidor incrementa el conteo en uno
    And redirige al destino con HTTP 302
    And la respuesta indica que la redirección no debe almacenarse en caché

  Scenario: Aperturas repetidas se cuentan por separado
    Given existe un enlace corto
    When llegan dos solicitudes GET separadas para ese enlace
    Then el conteo aumenta en dos

  Scenario: HEAD no incrementa el conteo
    Given existe un enlace corto
    When se consulta con HEAD
    Then el conteo no cambia

  Scenario: La estadística se conserva tras reiniciar
    Given un enlace tiene aperturas registradas en SQLite
    When la aplicación reinicia y se consulta la estadística
    Then el conteo previamente registrado se conserva

  Scenario: Enlace inexistente no incrementa ningún conteo
    Given el identificador solicitado no existe
    When se solicita la redirección
    Then el sistema responde como enlace no encontrado
    And no modifica contadores
```

---

## 3. USER STORIES Y TASKS

> Las horas y días-persona de abajo son estimaciones iniciales propuestas. `Assigned To`, `Iteration Path` y estados quedan para acordar en Azure. No se asignan integrantes por nombre.

### HU-01 — Aceptar únicamente destinos HTTP/HTTPS

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | User Story / Product Backlog Item |
| Title | Como usuario quiero que solo se acepten destinos HTTP y HTTPS para compartir enlaces seguros |
| Parent | F1.1 |
| State | New |
| Priority / MoSCoW | 1 / Must |
| Story Points / Effort | **0.625 días-persona** (5 h de subtareas / 8) |
| Tags | `linker1; seguridad; backend; must` |

#### Description

```text
Como usuario de Linker1
Quiero que el sistema acepte únicamente destinos web HTTP o HTTPS
Para evitar crear enlaces cortos que redirijan a esquemas peligrosos o no web.
```

#### Acceptance Criteria — Gherkin

```gherkin
Feature: Validación del destino

  Scenario Outline: Aceptar URL web válida
    Given una URL absoluta con esquema <esquema> y host válido
    When la envío para crear un enlace
    Then el sistema la acepta

    Examples:
      | esquema |
      | http    |
      | https   |

  Scenario Outline: Rechazar esquema no permitido
    Given una URL cuyo esquema es <esquema>
    When la envío para crear un enlace
    Then el sistema responde con error de validación
    And no crea ni persiste un enlace

    Examples:
      | esquema    |
      | javascript |
      | data       |
      | file       |

  Scenario: Rechazar URL relativa o sin host
    Given una dirección que no es absoluta o no tiene host
    When la envío para crear un enlace
    Then el sistema responde con error de validación
```

#### Tasks

**T-01-01 — Restringir la validación de destino a HTTP/HTTPS**

- Work Item Type: Task
- Estimate: **3 horas-persona**
- Remaining Work (h): 3 al crear; actualizar al avanzar
- Parent: HU-01
- Activity: Development
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Actualizar LinkService.isValidUrl para aceptar solo URI absolutas con esquema http o https y host válido. Mantener la validación en el backend como autoridad, independientemente de la validación del navegador. No registrar el enlace cuando la entrada no pase la validación.
Archivos de referencia: src/linker/LinkService.java y src/linker/routes/LinkRoutes.java.
```

- Acceptance Criteria:
  - `http://example.com` y `https://example.com/path` son válidas.
  - `javascript:`, `data:`, `file:`, URI relativa y URL sin host son inválidas.
  - El rechazo se devuelve como error de validación controlado, no como 500.

**T-01-02 — Agregar pruebas de validación HTTP/HTTPS**

- Work Item Type: Task
- Estimate: **2 horas-persona**
- Remaining Work (h): 2 al crear; actualizar al avanzar
- Parent: HU-01
- Activity: Testing
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Añadir pruebas unitarias de LinkService para URLs válidas e inválidas y una prueba de ruta para verificar el código de respuesta de una entrada no permitida. Mantener compatibles las pruebas existentes.
Archivos de referencia: test/linker/LinkServiceTest.java, test/linker/LinkServiceAliasTest.java y test/linker/routes/LinkRoutesTest.java.
```

- Acceptance Criteria:
  - Hay casos positivos para HTTP y HTTPS.
  - Hay casos negativos para javascript:, data:, file:, URL relativa y host vacío/inválido.
  - Se verifica que una entrada inválida no se persiste.

### HU-02 — Rechazar alias reservados y explicar los errores en español

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | User Story / Product Backlog Item |
| Title | Como usuario quiero recibir mensajes claros en español si mi URL o alias no sirve para corregirlo |
| Parent | F1.1 |
| State | New |
| Priority / MoSCoW | 1 / Must |
| Story Points / Effort | **0.8125 días-persona** (6.5 h de subtareas / 8) |
| Tags | `linker1; alias; ux; frontend; must` |

#### Description

```text
Como usuario que crea un enlace corto
Quiero que el sistema me explique en español por qué rechazó una URL o alias
Para corregir la entrada sin interpretar errores técnicos.
```

#### Acceptance Criteria — Gherkin

```gherkin
Feature: Mensajes claros de creación

  Scenario: Alias reservado
    Given intento usar el alias reservado healthz
    When envío el formulario
    Then no se crea el enlace
    And veo un mensaje en español que indica que el alias está reservado

  Scenario: Alias ocupado
    Given el alias ya está asociado con otro destino
    When intento crear el enlace con ese alias
    Then el sistema conserva el conflicto HTTP 409
    And la interfaz indica en español que el alias no está disponible

  Scenario: URL inválida
    Given escribo una URL no permitida
    When intento crear el enlace
    Then veo un mensaje breve en español que explica qué tipos de URL se aceptan

  Scenario: Creación correcta
    Given ingreso una URL válida y un alias válido o dejo el alias vacío
    When creo el enlace
    Then recibo confirmación clara y el enlace corto funcional
```

#### Tasks

**T-02-01 — Completar la lista de alias reservados**

- Work Item Type: Task
- Estimate: **1 hora-persona**
- Remaining Work (h): 1 al crear; actualizar al avanzar
- Parent: HU-02
- Activity: Development
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Revisar las rutas exactas y los recursos estáticos del proyecto; extender RESERVED_ALIASES para incluir healthz y cualquier nombre que colisione con una ruta/asset real de Linker1. No reservar nombres arbitrarios sin comprobar colisión.
Archivo de referencia: src/linker/LinkService.java. La lista actual incluye link, app.js y styles.css.
```

- Acceptance Criteria:
  - `healthz` no se puede crear como alias.
  - Las rutas y assets actuales no se pueden sombrear mediante alias.
  - Los alias válidos actuales siguen funcionando.

**T-02-02 — Presentar errores de API en español claro**

- Work Item Type: Task
- Estimate: **1.5 horas-persona**
- Remaining Work (h): 1.5 al crear; actualizar al avanzar
- Parent: HU-02
- Activity: Development
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Mapear errores controlados de URL inválida, alias inválido/reservado y alias ocupado a respuestas entendibles en español. No exponer excepciones, stack traces ni detalles internos a la interfaz.
Archivo de referencia: src/linker/routes/LinkRoutes.java.
```

- Acceptance Criteria:
  - Entrada inválida mantiene respuesta 400.
  - Alias ocupado mantiene respuesta 409.
  - Los mensajes que ve el usuario están en español y sugieren una corrección.

**T-02-03 — Actualizar la interfaz de creación y errores**

- Work Item Type: Task
- Estimate: **2 horas-persona**
- Remaining Work (h): 2 al crear; actualizar al avanzar
- Parent: HU-02
- Activity: Development
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Actualizar los textos del flujo de creación para que el usuario sepa qué URL puede ingresar, qué significa un alias no disponible y si el enlace fue creado. Mantener coherencia en las versiones de UI que sigan disponibles para la demo.
Archivos de referencia: public/app.js, public/v1/index.html, public/v2/index-v2.html y estilos correspondientes si se requieren.
```

- Acceptance Criteria:
  - Los errores de validación se muestran sin recargar la página y en español claro.
  - La respuesta exitosa identifica el enlace corto generado.
  - El texto no presenta detalles internos del servidor.

**T-02-04 — Probar alias reservados y respuestas 400/409**

- Work Item Type: Task
- Parent: HU-02
- Activity: Testing
- Assigned To: `[asignar en Azure]`
- State: New
- Estimate: **2 horas-persona**
- Remaining Work (h): 2 al crear; actualizar al avanzar
- Description:

```text
Agregar o actualizar pruebas para alias reservados/no válidos y alias ocupado. Verificar que healthz no se pueda registrar, que la entrada inválida responda 400, que el alias ocupado responda 409 y que el cuerpo/mensaje sea entendible. Actualizar pruebas de API si el contrato cambia.
Archivos de referencia: test/linker/LinkServiceAliasTest.java, test/linker/routes/LinkRoutesTest.java y postman/linker1.postman_collection.json.
```

- Acceptance Criteria:
  - Alias reservado devuelve 400 y no se persiste.
  - Alias ocupado conserva 409.
  - Se cubren respuestas de error y caso válido en pruebas automatizadas.

### HU-03 — Contar aperturas de forma persistente

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | User Story / Product Backlog Item |
| Title | Como usuario quiero que se cuenten las aperturas de cada enlace para conocer su uso |
| Parent | F1.2 |
| State | New |
| Priority / MoSCoW | 1 / Must — alcance técnico alto; confirmar capacidad |
| Story Points / Effort | **1.25 días-persona** (10 h de subtareas / 8) |
| Tags | `linker1; contador; sqlite; backend; must` |

#### Description

```text
Como usuario que comparte un enlace corto
Quiero que cada apertura se cuente y el dato se conserve
Para saber si el enlace fue utilizado.
```

#### Acceptance Criteria — Gherkin

```gherkin
Feature: Registrar aperturas

  Scenario: Contador inicial
    Given se crea un enlace
    When consulto el conteo antes de recibir aperturas
    Then el conteo es cero

  Scenario: Cada GET de enlace existente suma una apertura
    Given un enlace corto existente con conteo N
    When una solicitud GET abre ese enlace
    Then el conteo persistido queda en N más uno
    And el servidor responde con una redirección HTTP 302

  Scenario: Solicitudes GET sucesivas se cuentan
    Given un enlace corto existente
    When dos solicitudes GET independientes llegan al servidor
    Then ambas se reflejan en el conteo

  Scenario: HEAD y consulta de estadísticas no suman
    Given un enlace existente
    When llega una solicitud HEAD o se consulta su estadística
    Then el conteo permanece igual

  Scenario: Datos existentes sobreviven la migración
    Given la base contiene enlaces creados antes de añadir el contador
    When se inicia la nueva versión
    Then los enlaces y destinos existentes siguen disponibles
    And sus contadores comienzan en cero

  Scenario: Redirección no encontrada
    Given el identificador no existe
    When se solicita con GET
    Then el servidor devuelve 404
    And no aumenta ningún contador
```

#### Tasks

**T-03-01 — Añadir migración compatible para la columna de aperturas**

- Work Item Type: Task
- Estimate: **2.5 horas-persona**
- Remaining Work (h): 2.5 al crear; actualizar al avanzar
- Parent: HU-03
- Activity: Development
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Extender el esquema SQLite shorturl con clicks INTEGER NOT NULL DEFAULT 0. Asegurar que el arranque también actualice una base existente: CREATE TABLE IF NOT EXISTS no añade columnas a una tabla ya creada. Implementar una migración/idempotencia que detecte si la columna ya existe antes de alterarla. Conservar id y url sin pérdida.
Archivo de referencia: src/Main.java.
```

- Acceptance Criteria:
  - Una base nueva crea la columna con valor inicial 0.
  - Una base existente con id,url se migra sin borrar enlaces.
  - Reiniciar la app no falla ni ejecuta repetidamente un ALTER incompatible.

**T-03-02 — Implementar lectura e incremento atómico del contador**

- Work Item Type: Task
- Estimate: **2 horas-persona**
- Remaining Work (h): 2 al crear; actualizar al avanzar
- Parent: HU-03
- Activity: Development
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Agregar a LinkRepository las operaciones para incrementar aperturas de un id existente y consultar su conteo. Usar sentencias preparadas y una actualización SQL que incremente el valor en la base. Manejar id inexistente sin modificar datos.
Archivo de referencia: src/linker/LinkRepository.java.
```

- Acceptance Criteria:
  - El incremento modifica exactamente el enlace solicitado.
  - Consultar devuelve el valor persistido.
  - Un id inexistente se distingue de un contador cero.

**T-03-03 — Integrar conteo en redirección y cambiar a HTTP 302**

- Work Item Type: Task
- Estimate: **2.5 horas-persona**
- Remaining Work (h): 2.5 al crear; actualizar al avanzar
- Parent: HU-03
- Activity: Development
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
En GET /{id}, incrementar el conteo solo cuando se resuelve un enlace existente y antes de completar la redirección. Responder HTTP 302. No incrementar HEAD, GET 404 ni la consulta de estadísticas. Revisar el impacto del status code en clientes y pruebas existentes.
Archivo de referencia: src/linker/routes/LinkRoutes.java.
```

- Acceptance Criteria:
  - GET válido incrementa una vez y redirige con 302.
  - La respuesta de redirección incluye `Cache-Control: no-store` para evitar contar menos por caché del cliente.
  - GET inexistente devuelve 404 y no incrementa.
  - HEAD conserva su comportamiento y no incrementa.

**T-03-04 — Actualizar pruebas que esperan HTTP 301 y cubrir conteo**

- Work Item Type: Task
- Estimate: **3 horas-persona**
- Remaining Work (h): 3 al crear; actualizar al avanzar
- Parent: HU-03
- Activity: Testing
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Actualizar pruebas automatizadas y verificaciones que afirman 301 para que validen 302. Añadir pruebas del incremento, persistencia, HEAD sin incremento y 404 sin incremento. Revisar JUnit, smoke checks de CI, colección Postman/Newman y checks k6 afectados.
Archivos de referencia: test/, postman/linker1.postman_collection.json, tests/k6/ y .github/workflows/ci.yml.
```

- Acceptance Criteria:
  - No quedan expectativas 301 obsoletas en las verificaciones del flujo.
  - Las pruebas distinguen GET de redirección, HEAD y 404.
  - La prueba automatizada relevante queda explicada para la sustentación.

### HU-04 — Consultar y mostrar el número de aperturas

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | User Story / Product Backlog Item |
| Title | Como usuario quiero consultar cuántas veces se abrió mi enlace para valorar su uso |
| Parent | F1.2 |
| State | New |
| Priority / MoSCoW | 1 / Must, dependiente de HU-03 |
| Story Points / Effort | **0.75 días-persona** (6 h de subtareas / 8) |
| Tags | `linker1; contador; api; frontend; must` |

#### Description

```text
Como usuario que acaba de crear un enlace corto
Quiero consultar el número de aperturas asociado a su identificador
Para saber cuántas solicitudes de redirección recibió.

Contrato sugerido (confirmar antes de implementar): GET /link/{id}/stats
Respuesta exitosa sugerida: HTTP 200, JSON {"id":"<id>","clicks":<entero>}
Id desconocido: HTTP 404.
```

#### Acceptance Criteria — Gherkin

```gherkin
Feature: Consultar aperturas

  Scenario: Consultar estadística de un enlace
    Given existe un enlace con identificador conocido
    When consulto GET /link/{id}/stats
    Then recibo HTTP 200 con el identificador y el conteo actual

  Scenario: Consultar enlace inexistente
    Given no existe el identificador solicitado
    When consulto su estadística
    Then recibo HTTP 404 con un mensaje claro

  Scenario: Usuario ve el conteo en la interfaz
    Given el usuario crea un enlace corto
    When solicita consultar sus aperturas
    Then la interfaz muestra el conteo recibido en español
```

#### Tasks

**T-04-01 — Exponer endpoint de consulta del contador**

- Work Item Type: Task
- Estimate: **2 horas-persona**
- Remaining Work (h): 2 al crear; actualizar al avanzar
- Parent: HU-04
- Activity: Development
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Implementar la ruta acordada para consultar las aperturas de un enlace. Ruta sugerida: GET /link/{id}/stats. Devolver JSON con id y clicks, HTTP 404 si el enlace no existe y no cambiar el contador durante la consulta.
Archivos de referencia: src/linker/routes/LinkRoutes.java y src/linker/LinkRepository.java.
```

- Acceptance Criteria:
  - El contrato coincide con lo acordado y documentado.
  - La consulta no incrementa el contador.
  - Errores 404 se mantienen diferenciados de errores 500.

**T-04-02 — Mostrar el conteo de forma entendible en la UI**

- Work Item Type: Task
- Estimate: **2 horas-persona**
- Remaining Work (h): 2 al crear; actualizar al avanzar
- Parent: HU-04
- Activity: Development
- Assigned To: `[asignar en Azure]`
- State: New
- Description:

```text
Agregar un control sencillo para consultar el conteo del enlace corto y mostrar el resultado en español. No añadir gráficas, segmentación ni historial analítico. Definir el flujo junto con la experiencia de creación ya existente.
Archivos de referencia: public/app.js, public/v1/ y public/v2/.
```

- Acceptance Criteria:
  - El usuario puede consultar el conteo del enlace creado.
  - La carga, el resultado y el error tienen estados visibles.
  - El valor mostrado coincide con el JSON del endpoint.

**T-04-03 — Probar endpoint de estadísticas y actualizar Postman**

- Work Item Type: Task
- Parent: HU-04
- Activity: Testing
- Assigned To: `[asignar en Azure]`
- State: New
- Estimate: **2 horas-persona**
- Remaining Work (h): 2 al crear; actualizar al avanzar
- Description:

```text
Agregar pruebas automatizadas para GET /link/{id}/stats (o la ruta finalmente acordada) y actualizar la colección Postman. Cubrir respuesta 200 con clicks, id inexistente 404, forma del JSON y que consultar estadísticas no incremente el contador.
Archivos de referencia: test/linker/routes/LinkRoutesTest.java, postman/linker1.postman_collection.json.
```

- Acceptance Criteria:
  - El caso válido comprueba status, id y conteo.
  - El caso inexistente devuelve 404.
  - La consulta no cambia el conteo.
  - La colección Postman refleja el contrato implementado.

---

## 4. HISTORIAS TRANSVERSALES: EVIDENCIA QUE PIDE LA GUÍA

Estas historias no agregan funcionalidades de producto. Se incluyen porque la rúbrica califica también el proceso, la colaboración y la entrega. Crear solo las tareas que aún no tengan evidencia verificable; enlazar pruebas externas (Azure Boards, GitHub, video) desde cada historia.

### F1.3 — Proceso, evidencias y entrega del curso

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | Feature |
| Referencia temporal | F1.3 |
| Title | Proceso, evidencias y entrega del curso |
| Parent | E1 — Linker1: creación segura y medición de aperturas |
| State | New; actualizar según avance real |
| Area Path | `[NombreProyecto]\Linker1\Proceso` (ajustar al proyecto Azure real) |
| Iteration Path | `[NombreProyecto]\[Sprint correspondiente]` |
| Priority / MoSCoW | 1 / Must para requisitos de evaluación |
| Effort | **5.625 días-persona** (45 horas-persona / 8) |
| Tags | `linker1; proceso; scrum; lean; devops; entrega` |

#### Description

```text
Agrupa el trabajo de proceso y las evidencias requeridas por la guía final: validación del cliente y océano azul, prácticas Scrum, mejora Lean, colaboración y DevOps, trazabilidad, informe, presentación, reflexiones individuales y demo de respaldo.

No se considera funcionalidad del producto. Crear o mantener solo los artefactos pendientes y enlazar los que ya existen. Toda evidencia debe reflejar actividades reales del equipo.
```

### HU-05 — Validar necesidad, valor y diferenciación con usuarios

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | User Story / Product Backlog Item |
| Title | Como equipo queremos validar el problema y el incremento con usuarios para priorizar con evidencia |
| Parent | F1.3 |
| Priority / MoSCoW | 1 / Must |
| Story Points / Effort | **1.75 días-persona** (14 h de subtareas / 8) |
| State | New; actualizar según evidencia real |
| Tags | `cliente; discovery; ocean-blue; valor` |

#### Description

```text
Como equipo
Queremos documentar quién tiene la necesidad, qué hace actualmente y qué valor aporta el incremento
Para justificar el MVP, la matriz de océano azul y las decisiones del backlog con evidencia observada.
```

#### Acceptance Criteria

```gherkin
Feature: Validación de cliente y valor

  Scenario: Evidencia inicial de usuarios
    Given hay al menos dos usuarios reales o potenciales consultados
    When el equipo registra la evidencia
    Then documenta fecha, perfil o tipo de usuario, hallazgos y limitaciones
    And distingue observación de hipótesis

  Scenario: Problema y MVP documentados
    Given existen hallazgos de usuario
    When el equipo formula la necesidad
    Then registra usuario principal, necesidad, solución actual, problema con evidencia, propuesta de valor, MVP y recorrido principal

  Scenario: Comparación de alternativas
    Given se define la propuesta de Linker1
    When el equipo compara alternativas actuales
    Then compara al menos dos alternativas e incluye la manual cuando aplique
    And registra la matriz eliminar, reducir, incrementar y crear con utilidad y esfuerzo

  Scenario: Decisión de océano azul conectada al producto
    Given existe una decisión de la matriz
    When el equipo prioriza una funcionalidad
    Then enlaza la decisión con la historia del backlog y explica quién se beneficia

  Scenario: Validación de incremento
    Given existe un incremento demostrable
    When usuarios reales o potenciales ejecutan la tarea acordada
    Then el equipo registra qué observó, qué esperaba, qué mantiene/cambia/descarta y el indicador medido
```

#### Tasks

- **T-05-01 — Registrar entrevistas u observaciones:** 4 h. Realizar al menos dos; guardar notas reales y declarar si son representantes.
- **T-05-02 — Documentar problema, usuario, propuesta de valor, MVP y recorrido:** 2 h; enlazar hallazgos al backlog.
- **T-05-03 — Comparar alternativas y completar matriz ERIC:** 2 h; incluir al menos dos alternativas (manual cuando corresponda), utilidad para el cliente y esfuerzo.
- **T-05-04 — Definir y medir indicador de valor:** 4 h; usar indicador acordado (por ejemplo, tiempo de completar la tarea o porcentaje sin ayuda); conservar datos observados y limitaciones.
- **T-05-05 — Registrar validación del incremento:** 2 h; anotar fecha, participantes/limitación, resultado esperado vs observado, decisión mantener/cambiar/descartar y cambios que originó.

**Ubicación sugerida:** `docs/proceso/cliente/` o artefactos vinculados en Azure Boards. Las rutas son organización propuesta, no requisito literal de la docente.

### HU-06 — Mantener evidencia de Scrum y adaptación del equipo

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | User Story / Product Backlog Item |
| Title | Como equipo queremos mantener los artefactos Scrum para inspeccionar el avance y adaptar el trabajo |
| Parent | F1.3 |
| Priority / MoSCoW | 1 / Must |
| Story Points / Effort | **1.1875 días-persona** (9.5 h de subtareas / 8) |
| State | New; actualizar según registros reales |
| Tags | `scrum; backlog; planning; review; retrospective` |

#### Acceptance Criteria

```gherkin
Feature: Evidencias de Scrum

  Scenario: Objetivo, roles y backlog visibles
    Given inicia el trabajo del equipo
    When se organiza el backlog
    Then el objetivo del producto y responsabilidades PO, SM y Developers están registrados
    And las historias tienen criterios de aceptación y orden visible

  Scenario: Sprint planificado
    Given el equipo inicia un sprint
    When realiza Sprint Planning
    Then registra objetivo, historias seleccionadas, responsables, bloqueos y tablero

  Scenario: Daily con decisión o impedimento
    Given hay días de trabajo durante el sprint
    When el equipo inspecciona el avance
    Then conserva evidencia breve de decisiones o impedimentos relevantes

  Scenario: Review y retrospectiva
    Given se cierra un sprint con un incremento
    When el equipo realiza Review y retrospectiva
    Then registra demo y retroalimentación
    And acuerda una mejora, responsable y forma de comprobarla

  Scenario: Definición de Terminado aplicada
    Given una historia se propone para Done
    When se revisa el trabajo
    Then cumple la definición acordada de funcionamiento, verificación y disponibilidad para demo
```

#### Tasks

- **T-06-01 — Registrar objetivo del producto, integrantes y responsabilidades:** 1 h; incluir combinaciones/rotación de roles acordadas realmente.
- **T-06-02 — Mantener Product Backlog ordenado e historial de prioridades:** 1 h; historia, criterio de aceptación, prioridad y comentario al cambiar el orden.
- **T-06-03 — Registrar Planning y Sprint Backlog:** 1.5 h; objetivo, selección viable, responsables, bloqueos y enlace al tablero por sprint.
- **T-06-04 — Registrar Daily Scrum de forma breve:** 1 h; decisiones e impedimentos, sin crear actas largas.
- **T-06-05 — Registrar Sprint Review y retrospectiva:** 2 h; incremento, retroalimentación y mejora con responsable y comprobación.
- **T-06-06 — Acordar y aplicar Definition of Done:** 1 h; incluir funcionamiento, verificación y disponibilidad para demo.
- **T-06-07 — Evidenciar dos sprints y dos incrementos demostrables:** 2 h; enlazar artefactos reales de cada sprint; si hubo limitación temporal, informar lo ocurrido sin simular registros.

### HU-07 — Aplicar Lean y medir el flujo de trabajo

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | User Story / Product Backlog Item |
| Title | Como equipo queremos observar y mejorar el flujo para reducir desperdicios del trabajo |
| Parent | F1.3 |
| Priority / MoSCoW | 1 / Must |
| Story Points / Effort | **0.5625 días-persona** (4.5 h de subtareas / 8) |
| State | New; actualizar según evidencia real |
| Tags | `lean; mejora-continua; metricas` |

#### Acceptance Criteria

```gherkin
Feature: Mejora Lean

  Scenario: Identificar desperdicios con evidencia
    Given el equipo observa el flujo de necesidad a entrega
    When identifica desperdicios
    Then documenta al menos dos, dónde aparecen y cómo afectan al equipo o al usuario

  Scenario: Aplicar una mejora y observar resultado
    Given existe un desperdicio priorizado
    When el equipo aplica una mejora durante el siguiente sprint
    Then registra situación inicial, acción y resultado observado

  Scenario: Usar dos métricas
    Given el equipo mide el flujo entre sprints
    When actualiza las métricas acordadas
    Then registra dos métricas sencillas y las usa para decidir una mejora
```

#### Tasks

- **T-07-01 — Registrar al menos dos desperdicios observados:** 1.5 h; no presentarlos como hechos si solo son hipótesis.
- **T-07-02 — Capturar línea base de dos métricas:** 1 h; ejemplos permitidos por la guía: tiempo de una historia desde inicio hasta terminar e ítems terminados por sprint. La propuesta también sugiere tiempo de PR abierto a integrado.
- **T-07-03 — Aplicar una mejora en el sprint siguiente y comparar:** 2 h; estado inicial, acción, datos/resultado y qué falta medir.

### HU-08 — Demostrar colaboración, DevOps y trazabilidad

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | User Story / Product Backlog Item |
| Title | Como equipo queremos integrar, verificar y entregar Linker1 de forma reproducible |
| Parent | F1.3 |
| Priority / MoSCoW | 1 / Must |
| Story Points / Effort | **0.9375 días-persona** (7.5 h de subtareas / 8) |
| State | New; actualizar según evidencia real |
| Tags | `devops; git; ci; demo; traceability` |

#### Acceptance Criteria

```gherkin
Feature: Evidencia DevOps

  Scenario: Cambio revisado por otro integrante
    Given una historia implementada en una rama
    When se integra mediante pull request
    Then otro integrante deja comentarios o verificaciones útiles
    And se conserva el enlace a la revisión

  Scenario: CI valida cambios
    Given se sube o integra un cambio
    When se ejecuta CI
    Then se conserva el resultado de una validación o prueba relevante

  Scenario: Demo reproducible
    Given una persona evaluadora sin credenciales privadas
    When sigue las instrucciones de ejecución
    Then puede iniciar la versión de demostración y ejecutar el recorrido principal

  Scenario: Operación y recuperación
    Given la aplicación está ejecutándose
    When se comprueba salud/logs o se simula el procedimiento documentado de recuperación
    Then el equipo puede explicar cómo verificar la app y volver a la versión funcional

  Scenario: Historia trazable
    Given se selecciona una historia
    When se sigue su enlace de trazabilidad
    Then se puede llegar a necesidad, priorización, desarrollo, PR/revisión, prueba, CI y demo/entrega
```

#### Tasks

- **T-08-01 — Mantener contribuciones identificables y revisión cruzada:** 1.5 h; usar identidades de Git de cada integrante; ningún autor aprueba su propio PR; enlazar PR y comentarios útiles.
- **T-08-02 — Conservar una ejecución CI y una prueba relevante:** 1 h; explicar qué comprueba la prueba y mostrar resultado real.
- **T-08-03 — Preparar Quick Start sin credenciales privadas:** 2 h; ajustar README a Linker1 y al curso; reproducir pasos desde entorno limpio/devcontainer cuando sea posible.
- **T-08-04 — Documentar verificación operativa y rollback:** 1 h; mostrar logs o healthcheck aplicable a la demo y el procedimiento para volver al último JAR funcional; registrar el ensayo solo si se ejecuta.
- **T-08-05 — Revisar secretos, fuentes, licencias, reutilización y uso de IA:** 1 h; declarar lo utilizado y cómo se verificó; no incluir claves en Git.
- **T-08-06 — Completar trazabilidad de una historia:** 1 h; enlazar Azure Story → PR → CI/tests → versión/demo.

### HU-09 — Preparar paquete de entrega y sustentación

| Campo Azure Boards | Valor propuesto |
|---|---|
| Work Item Type | User Story / Product Backlog Item |
| Title | Como equipo queremos preparar evidencias accesibles y una demo funcional para sustentar el trabajo |
| Parent | F1.3 |
| Priority / MoSCoW | 1 / Must |
| Story Points / Effort | **1.1875 días-persona** (9.5 h de subtareas / 8) |
| State | New; actualizar según evidencia real |
| Tags | `entrega; informe; sustentacion; evidencia-individual` |

#### Acceptance Criteria

```gherkin
Feature: Entrega final

  Scenario: Índice e informe
    Given se consolidan las evidencias
    When se prepara el paquete final
    Then existe un índice con enlaces accesibles
    And el informe tiene máximo ocho páginas sin anexos

  Scenario: Sustentación y demostración
    Given la versión final ejecutable
    When el equipo presenta
    Then cubre los bloques indicados por la guía en 15 minutos y reserva 5 minutos para preguntas
    And participan los tres integrantes

  Scenario: Aporte y aprendizaje individual
    Given cada integrante sustenta
    When explica sus decisiones y contribución
    Then muestra evidencias y una reflexión individual breve

  Scenario: Video de respaldo
    Given la demo principal está lista
    When el equipo prepara el respaldo
    Then conserva una grabación breve del funcionamiento real
```

#### Tasks

- **T-09-01 — Crear índice enlazado y verificar permisos:** 1 h; reunir links de repo, tablero, entrevistas, sprints, PR, CI, demo y demás evidencias existentes; probar acceso de la docente.
- **T-09-02 — Redactar informe de máximo ocho páginas sin anexos:** 3 h; sintetizar cliente/innovación, sprints/incrementos, Lean, DevOps, resultados y límites; no duplicar anexos enlazados.
- **T-09-03 — Preparar presentación de 15 minutos y preguntas:** 2 h; 3 min problema/cliente/valor/diferenciación; 4 min demo/validación; 4 min sprints/Lean; 4 min trazabilidad/DevOps; reservar 5 min de preguntas.
- **T-09-04 — Preparar reflexión breve por integrante y reparto de sustentación:** 1.5 h; cada persona explica aporte, decisiones, aprendizaje y dificultades reales.
- **T-09-05 — Grabar demo breve de respaldo:** 1 h; grabar recorrido funcional real y conservar el archivo o enlace accesible.
- **T-09-06 — Revisar fuentes, licencias, código reutilizado e IA en la entrega:** 1 h; declarar elementos y uso; cada integrante debe poder explicar el código que presenta.

### Resumen de estimaciones propuestas

| Nivel | Ítem | Horas-persona de subtareas | Cálculo | Estimación a registrar |
|---|---|---:|---:|---:|
| Feature | F1.1 — Crear enlaces seguros y fáciles de entender | 11.5 h | HU-01 + HU-02 | **1.4375 días-persona** |
| Feature | F1.2 — Contar y consultar aperturas | 16 h | HU-03 + HU-04 | **2 días-persona** |
| Feature | F1.3 — Proceso, evidencias y entrega del curso | 45 h | HU-05 a HU-09 | **5.625 días-persona** |
| Epic | E1 — Linker1: creación segura y medición de aperturas | **72.5 h** | 72.5 ÷ 8 | **9.0625 días-persona** |

| User Story | Horas de sus tareas | División entre 8 | Story Points / Effort (días-persona) |
|---|---:|---:|---:|
| HU-01 | 5 h | 5 ÷ 8 | **0.625** |
| HU-02 | 6.5 h | 6.5 ÷ 8 | **0.8125** |
| HU-03 | 10 h | 10 ÷ 8 | **1.25** |
| HU-04 | 6 h | 6 ÷ 8 | **0.75** |
| HU-05 | 14 h | 14 ÷ 8 | **1.75** |
| HU-06 | 9.5 h | 9.5 ÷ 8 | **1.1875** |
| HU-07 | 4.5 h | 4.5 ÷ 8 | **0.5625** |
| HU-08 | 7.5 h | 7.5 ÷ 8 | **0.9375** |
| HU-09 | 9.5 h | 9.5 ÷ 8 | **1.1875** |
| **Total** | **72.5 h** | **72.5 ÷ 8** | **9.0625 días-persona** |

**Lectura para la fecha cercana:** son horas-persona estimadas si todo el trabajo listado estuviera pendiente. Con tres integrantes y dos jornadas de ocho horas, la capacidad teórica máxima sería 48 horas-persona; por tanto, no cabría completar todo desde cero en ese plazo. Antes de cargar `Remaining Work`, revisar qué evidencias y tareas ya existen, enlazarlas y dejar como restantes solo las horas que faltan. Estas cifras son esfuerzo, no duración de calendario; dependencias como entrevistas, pruebas con usuarios y revisiones pueden alargar la secuencia.

---

## 5. Definition of Ready (DoR) sugerida

Aplicar al trabajo nuevo, no marcar casillas de manera retroactiva.

- [ ] Historia escrita en formato Como / Quiero / Para.
- [ ] Parent Epic y Feature correctos.
- [ ] Criterios de aceptación verificables, incluyendo casos alternos relevantes.
- [ ] Prioridad acordada por el equipo y relación con evidencia de usuario o requisito de la guía identificada.
- [ ] Dependencias técnicas/funcionales identificadas.
- [ ] Story points y responsable acordados por el equipo, no estimados unilateralmente.
- [ ] Diseño/contrato necesario aclarado antes de desarrollo (en particular ruta y acceso de estadísticas).
- [ ] El equipo confirma capacidad de terminar el alcance dentro del sprint.

## 6. Definition of Done (DoD) sugerida

- [ ] Criterios de aceptación completados y revisados.
- [ ] Cambio integrado mediante PR revisado por otro integrante.
- [ ] Pruebas relevantes ejecutadas y resultado enlazado; CI en verde según el flujo real del proyecto.
- [ ] Cambios de Postman/Newman, smoke o k6 actualizados si el contrato cambió.
- [ ] Instrucciones y documentación relacionadas actualizadas.
- [ ] Demo reproducible en entorno elegido, sin secretos expuestos.
- [ ] Historia enlazada a PR, revisión, pruebas/CI y demo/entrega.
- [ ] Funcionalidad disponible para la demostración.

## 7. Estructura en Azure Boards

```text
EPIC E1 — Linker1: creación segura y medición de aperturas
├── FEATURE F1.1 — Crear enlaces seguros y fáciles de entender
│   ├── HU-01 — Aceptar únicamente destinos HTTP/HTTPS
│   │   ├── T-01-01 — Restringir validación backend
│   │   └── T-01-02 — Pruebas de validación
│   └── HU-02 — Rechazar alias reservados y explicar errores en español
│       ├── T-02-01 — Completar alias reservados
│       ├── T-02-02 — Errores claros en API
│       ├── T-02-03 — UI y mensajes
│       └── T-02-04 — Pruebas de alias reservados y respuestas 400/409
├── FEATURE F1.2 — Contar y consultar aperturas
│   ├── HU-03 — Contar aperturas de forma persistente
│   │   ├── T-03-01 — Migración SQLite
│   │   ├── T-03-02 — Repositorio: leer/incrementar
│   │   ├── T-03-03 — Redirect 302 e incremento
│   │   └── T-03-04 — Pruebas/checks y expectativas 302
│   └── HU-04 — Consultar y mostrar el conteo
│       ├── T-04-01 — Endpoint de estadísticas
│       ├── T-04-02 — Mostrar conteo en UI
│       └── T-04-03 — Pruebas del endpoint /stats y Postman
└── FEATURE F1.3 — Proceso, evidencias y entrega del curso
    ├── HU-05 — Validar necesidad, valor y diferenciación con usuarios
    ├── HU-06 — Mantener evidencia de Scrum y adaptación
    ├── HU-07 — Aplicar Lean y medir el flujo
    ├── HU-08 — Demostrar colaboración, DevOps y trazabilidad
    └── HU-09 — Preparar paquete de entrega y sustentación
```

## 8. Enlaces entre Azure Boards y el repositorio

Archivos y carpetas del repositorio relacionados:

- Backend: `src/linker/LinkService.java`, `src/linker/LinkRepository.java`, `src/linker/routes/LinkRoutes.java`, `src/Main.java`.
- Interfaz: `public/app.js`, `public/v1/`, `public/v2/`.
- Pruebas: `test/`, `postman/linker1.postman_collection.json`, `tests/k6/`.
- CI y demo: `.github/workflows/ci.yml`, `.devcontainer/`, `README.md`.
- Evidencia de proceso sugerida: `docs/proceso/00-estado-inicial.md`, `docs/proceso/cliente/`, `docs/proceso/sprint-1/`, `docs/proceso/sprint-2/`, `docs/proceso/lean/`, `docs/proceso/aportes.md`, `docs/proceso/uso-de-ia.md`.

Las rutas de `docs/proceso/` son una estructura recomendada por los documentos del equipo; verificar si ya existen en el repositorio remoto antes de crearlas.

## 9. Decisiones que el equipo debe cerrar antes de crear los ítems

1. Nombre exacto del proyecto, `Area Path` e `Iteration Path` en Azure.
2. Fecha oficial de entrega comunicada por la docente; la propuesta del equipo menciona 26-nov-2026.
3. Si el endpoint de estadísticas será `GET /link/{id}/stats` u otra ruta.
4. Si el conteo puede ser visible para cualquier persona que conozca el identificador corto o si hace falta token de gestión. Un token agrega alcance y debe estimarse aparte.
5. Cuál versión de UI (v1/v2) será la soportada en la demo y qué interfaz requiere actualización.
6. Asignados y estados según el trabajo real. Confirmar o ajustar estas estimaciones propuestas y registrar en `Remaining Work` solo el esfuerzo pendiente.
7. Si ya existen entrevistas, tablero, PRs, Reviews, retrospectivas, métricas, registros de CI o evidencias fuera de la carpeta local, enlazarlos en lugar de duplicarlos.

## 10. Referencias a requisitos de la guía final

| Requisito de la guía | Ítem Azure propuesto |
|---|---|
| Usuario principal, necesidad, situación actual, evidencia y propuesta de valor | HU-05 |
| Al menos dos usuarios reales o potenciales; declarar limitación si son representantes | T-05-01 |
| MVP, recorrido principal e indicador de valor | T-05-02, T-05-04 |
| Comparar al menos dos alternativas, matriz eliminar/reducir/incrementar/crear y decisión incorporada | T-05-03 y enlace a HU-01–HU-04 |
| Product Goal, responsabilidades, Product Backlog ordenado e historial | HU-06, T-06-01 y T-06-02 |
| Sprint Planning, tablero, Daily, Review, retrospectiva y Definition of Done | T-06-03 a T-06-06 |
| Al menos dos sprints y dos incrementos | T-06-07 |
| Dos desperdicios, una mejora aplicada y dos métricas sencillas | HU-07 y T-07-01 a T-07-03 |
| Repositorio, PR revisado, CI y prueba automatizada | HU-08, T-08-01 y T-08-02 |
| Demo reproducible, logs/health, rollback y secretos | T-08-03 a T-08-05 |
| Trazabilidad de una historia hasta integración y entrega | T-08-06 |
| Índice, informe máximo 8 páginas, demo, presentación 15+5, reflexión individual y video | HU-09, T-09-01 a T-09-06 |
| Fuentes, licencias, código reutilizado e IA; comprensión individual | T-08-05, T-09-04 y T-09-06 |

