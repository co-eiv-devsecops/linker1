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

## Procedimiento manual (por OCI Cloud Shell)

La VM no tiene IP pública (`assign_public_ip = false` en `infra/main.tf`), así que no se puede hacer `ssh ubuntu@<host>` desde cualquier máquina. El acceso manual se hace desde **OCI Cloud Shell** (que ya corre dentro de la red de OCI) usando una llave SSH guardada en OCI Vault:

```bash
# 0. Configurar IDs relevantes una sola vez (~/.bash_env, cargado desde ~/.bashrc)
export CMP_ID="ocid1.compartment.oc1..xxxxxxxx"
export SUBNET_ID="ocid1.subnet.oc1.sa-bogota-1.xxxxxxxx"
export VM_KEY_SECRET_ID="ocid1.vaultsecret.oc1..xxxxxxxx"

# 1. Traer la llave privada SSH desde el Vault y darle una entrada corta en ~/.ssh/config
oci secrets secret-bundle get --secret-id "$VM_KEY_SECRET_ID" --stage Current \
  --query 'data."secret-bundle-content".content' --raw-output | base64 -d > ~/.ssh/linker.key
chmod 600 ~/.ssh/linker.key
# Agregar a ~/.ssh/config:
#   Host linker
#       HostName A.B.C.D
#       User ubuntu
#       IdentityFile ~/.ssh/linker.key
#       IdentitiesOnly yes

# 2. Conectarse y correr el rollback
ssh linker
cd linker1
bash scripts/rollback.sh v1.0.0
```

El script:

1. Verifica que el tag solicitado existe (`git rev-parse`).
2. Detiene `linker1.service`.
3. Hace `git fetch --tags` y `git checkout <tag>`.
4. Ejecuta `deploy.sh` para reconstruir y reiniciar el servicio con ese código.
5. Verifica que el servicio quedó `active (running)` y que `GET /` responde `200`.
6. Si algo falla en el camino, se detiene (`set -euo pipefail`) e imprime en qué paso quedó, en vez de dejar el sistema a medio migrar silenciosamente.

## Procedimiento automatizado (GitHub Actions vía OCI Bastion)

El job `rollback` en [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml) está conectado a `deploy-prod` y `validate-prod`: se dispara automáticamente si cualquiera de los dos falla (`if: failure()`). Como la VM no tiene IP pública, no se conecta por SSH directo — usa el composite action del curso `co-eiv-devsecops/material-curso/actions/oci-bastion-deploy@main`, que abre una sesión gestionada contra un OCI Bastion y ejecuta remotamente `cd linker1 && bash scripts/rollback.sh <tag>`, apuntando al tag anterior al que se intentó desplegar.

Requiere estas variables/secrets en el repositorio (ver detalle y estado en [`DEPLOYMENT.md`](DEPLOYMENT.md)):

| Nombre | Tipo | Descripción |
| --- | --- | --- |
| `OCI_CLI_USER`/`OCI_CLI_TENANCY`/`OCI_CLI_FINGERPRINT`/`OCI_CLI_KEY_CONTENT` | secret | Autenticación de OCI CLI |
| `OCI_CLI_REGION` | variable | Región de OCI |
| `OCI_BASTION_OCID` | variable | Bastion usado para la sesión SSH gestionada |
| `DEPLOYMENT_PRIVATE_KEY` / `DEPLOYMENT_PUBLIC_KEY` | secret / variable | Par de llaves SSH que usa la acción a través del bastión |
| `OCI_INSTANCE_OCID` | variable | OCID de la VM de producción (el único valor que el equipo debe agregar) |

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
- [Estrategia de Despliegue Continuo](DEPLOYMENT.md) — describe el pipeline de `deploy-prod`/`validate-prod` completo.
