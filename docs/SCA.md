# Análisis de dependencias (SCA) con OWASP Dependency-Check

Basado en el laboratorio de la clase: https://eci-arcn.github.io/Labs/sca-dependency-check/

Complementa a SonarCloud (análisis del código propio) revisando las **librerías de terceros**, incluidas las transitivas, contra la base de vulnerabilidades NVD. Dependabot avisa de versiones nuevas; Dependency-Check dice qué CVE afectan a lo que ya usamos.

## Activarlo (una vez)
1. Pide una API key en https://nvd.nist.gov/developers/request-an-api-key y actívala con el correo que llega.
2. Guárdala como secreto de Actions `NVD_API_KEY` en Settings → Secrets and variables → Actions. Nunca va en el `pom.xml`.
3. Haz un push o un PR: aparece el check **Dependency Check (SCA)**. Sin el secreto el job se salta y queda en verde.

La primera ejecución descarga la base NVD y tarda varios minutos; las siguientes usan caché.

## Qué hace
- Falla si encuentra una vulnerabilidad con **CVSS ≥ 7** (High o Critical), con `failBuildOnCVSS` en el `pom.xml`.
- Genera un informe HTML (artefacto del run) y un SARIF que se sube a Security → Code scanning.
- No se ejecuta con `mvn verify`; en local: `NVD_API_KEY=... mvn org.owasp:dependency-check-maven:13.0.0:check`.

## Si encuentra algo
1. Actualiza la dependencia a la versión corregida (preferido).
2. Si es un falso positivo o no aplica, agrégalo a `dependency-check-suppression.xml` con motivo, quién lo revisó y fecha de expiración (`until`).

## Ejercicio del laboratorio (pendiente del equipo)
Crear una rama con `commons-text` 1.9 (CVE-2022-42889), ver cómo el check falla, subir a 1.15.0 y ver cómo pasa, y practicar una supresión. Debe hacerlo el equipo y documentar el resultado con capturas; no se ejecutó aquí.

## Primer resultado (26 nov 2026) y qué se hizo
La primera ejecución falló con CVSS ≥ 7 en cuatro grupos de librerías:

| Librería | Hallazgo | Acción |
|---|---|---|
| `sqlite-jdbc` 3.49.1.0 | Varios CVE de SQLite (FTS5), corregidos en SQLite 3.54.0 | Actualizada a `3.54.1.0` |
| `mysql-connector-j` 9.7.0 | CVE-2026-60586 y CVE-2026-60623 (afectan 9.7.0–9.7.1) | Actualizada a `26.10.0` |
| `kotlin-stdlib` y sus `-jdk7`/`-jdk8` 1.9.25 (transitivas de Javalin) | CVE-2026-53914 (Kotlin anterior a 2.4.20; trata de la caché de compilación) | Se fijan en `2.4.21` en `dependencyManagement` |
| Jetty 11.0.24 (transitiva de Javalin 6.5.0) | CVE-2026-2332, CVE-2026-5795, CVE-2026-10050, CVE-2025-5115 | **Suprimida con vencimiento 2027-03-01**: no existe un Jetty 11 corregido en Maven Central (el más alto es 11.0.26). Solución real: migrar a Javalin 7 (Jetty 12) |

Pendiente del equipo: confirmar que la aplicación sigue funcionando con las versiones nuevas (CI en verde y prueba manual) y decidir cuándo migrar a Javalin 7. La supresión de Jetty es una decisión de riesgo, no una corrección.
