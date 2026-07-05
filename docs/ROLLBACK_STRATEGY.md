# Estrategia de Rollback

## Contexto

Linker1 se despliega en una VM de OCI mediante `deploy.sh`, que compila el proyecto directamente sobre la VM (arquitectura x86_64) y lo deja corriendo como el servicio systemd `linker1.service` detrás de Nginx. Las versiones se marcan con tags Git siguiendo [Semantic Versioning](https://semver.org/) (`vMAJOR.MINOR.PATCH`); cada tag `v*.*.*` dispara el workflow `release.yml`, que publica un GitHub Release con el jar empaquetado y el script de arranque (`scripts/linker1`) como artefactos.

Esta estrategia de rollback usa exactamente esos dos mecanismos que ya existen (tags SemVer + `deploy.sh`) en lugar de introducir infraestructura nueva: revertir significa parar el servicio, volver al código de un tag anterior y reconstruir/reiniciar con `deploy.sh`.

## Cuándo hacer rollback

- El `deploy-prod` de `pipeline.yml` falla o el servicio no arranca después de un despliegue.
- El smoke test post-despliegue (los mismos checks que corren en CI: estáticos, crear enlace, alias, redirección, 404) falla contra la instancia en vivo.
- Se detecta un bug crítico o una regresión de datos poco después de un release.

## Estrategia

1. **Identificar la última versión estable**: el tag inmediatamente anterior al que falló (ej. si `v1.2.0` falla, el rollback es a `v1.1.0`).
2. **Detener el servicio** en la VM (`systemctl stop linker1.service`) para no dejarlo sirviendo una versión rota.
3. **Volver al código de ese tag** en el checkout que ya existe en la VM (`git fetch --tags && git checkout <tag>`), sin re-clonar ni depender de artefactos externos.
4. **Reconstruir con `deploy.sh`**, exactamente el mismo camino que un despliegue normal — así el rollback no es un procedimiento paralelo sin probar, sino el mismo script que ya se usa a diario, apuntado a un commit distinto.
5. **Verificar** que el servicio está `active (running)` y que las rutas principales responden como se espera.

Este es el mismo enfoque documentado por Esteban para releases: los tags ya existen y ya son la fuente de verdad de "qué versión es esta"; el rollback simplemente se mueve a un tag anterior en vez de a uno nuevo.

## Procedimiento manual (hoy, sin acceso automatizado a la VM)

Mientras no se reciban las credenciales de OCI CLI para automatizar esto desde GitHub Actions (mencionadas como pendientes en un correo aparte), el rollback se ejecuta a mano por SSH, usando el script [`scripts/rollback.sh`](../scripts/rollback.sh) incluido en este repo:

```bash
# En la VM, dentro del clon existente del repo (p. ej. /home/ubuntu/linker1)
bash scripts/rollback.sh v1.0.0
```

El script:

1. Verifica que el tag solicitado existe (`git rev-parse`).
2. Detiene `linker1.service`.
3. Hace `git fetch --tags` y `git checkout <tag>`.
4. Ejecuta `deploy.sh` para reconstruir y reiniciar el servicio con ese código.
5. Verifica que el servicio quedó `active (running)` y que `GET /` responde `200`.
6. Si algo falla en el camino, se detiene (`set -euo pipefail`) e imprime en qué paso quedó, en vez de dejar el sistema a medio migrar silenciosamente.

## Procedimiento automatizado (cuando se disponga de acceso SSH/OCI CLI desde Actions)

El job `rollback` en [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml) ya está conectado a `deploy-prod`: se dispara automáticamente si ese job falla (`if: failure()`). Su implementación real se conecta a la VM por SSH y ejecuta el mismo `scripts/rollback.sh`, apuntando al tag anterior al que se intentó desplegar.

Requiere estos secrets en el repositorio (aún no configurados — se agregarán cuando lleguen las instrucciones de acceso vía OCI CLI):

| Secret | Descripción |
| --- | --- |
| `VM_HOST` | IP o hostname de la VM de producción |
| `VM_USER` | Usuario SSH (`ubuntu`, según `deploy.sh`) |
| `VM_SSH_KEY` | Llave privada SSH para conectarse a la VM |

## Simulación

Para validar el procedimiento sin depender de un despliegue roto real, `scripts/rollback.sh` se puede correr contra cualquier tag existente en cualquier momento — no depende de que haya habido una falla previa. Pasos para simular:

```bash
# 1. Confirmar el tag actual desplegado
git -C /home/ubuntu/linker1 describe --tags

# 2. Ejecutar el rollback hacia el mismo tag (v1.0.0) como prueba,
#    ya que hoy solo existe un tag publicado
bash scripts/rollback.sh v1.0.0

# 3. Confirmar que el servicio sigue activo y responde
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/
```

Cuando exista más de un tag publicado (ej. tras un `v1.1.0`), la simulación real es: desplegar `v1.1.0`, luego correr `bash scripts/rollback.sh v1.0.0` y confirmar que el servicio vuelve a responder con el comportamiento de `v1.0.0`.

## Criterios de éxito

- El servicio queda `active (running)` según `systemctl status linker1.service`.
- `GET /` responde `200` en menos de 2 minutos desde que se ejecuta el script.
- El código en la VM (`git describe --tags`) coincide con el tag objetivo del rollback.
- No se pierde la base de datos SQLite (`deploy.sh` no la toca; vive en `/var/lib/linker1`, fuera del directorio del repo).

## Documentos relacionados

- [`deploy.sh`](../deploy.sh) — script de despliegue del que depende el rollback.
- [`scripts/rollback.sh`](../scripts/rollback.sh) — implementación del rollback.
- [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml) — pipeline de despliegue continuo con el job de rollback automático.
- [`.github/workflows/release.yml`](../.github/workflows/release.yml) — genera los tags/releases que el rollback usa como destino.
