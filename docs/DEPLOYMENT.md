# Estrategia de Despliegue Continuo

## Contexto

Linker1 corre en una única VM de OCI (producción, `https://1.n-la-c.app`) creada con la IaC de `infra/` (`assign_public_ip = false`: la instancia no tiene IP pública). Antes de este cambio, `.github/workflows/pipeline.yml` no desplegaba nada real: los jobs `deploy-dev`, `validate-dev` y `deploy-prod` eran placeholders que solo hacían `echo`, y el único job real (`rollback`) asumía SSH directo a un `VM_HOST` público que nunca existió ni se configuró.

El mecanismo real, indicado por el profesor del curso, es un composite action compartido (`co-eiv-devsecops/material-curso/actions/oci-bastion-deploy@main`) que llega a la VM a través de un **OCI Bastion** (sesión SSH gestionada por OCI, sin necesidad de IP pública). Este documento describe cómo quedó armado el pipeline con ese mecanismo.

No existe todavía una VM separada para el ambiente de desarrollo, así que este pipeline solo cubre `prod`. Los jobs `deploy-dev`/`validate-dev` se retiraron del archivo en vez de dejarse como simulación; cuando exista una segunda VM se pueden agregar siguiendo el mismo patrón que `deploy-prod`/`validate-prod`.

## Jobs de `.github/workflows/pipeline.yml`

Se dispara en cada `push` a `main` (y manualmente vía `workflow_dispatch`):

1. **`Build`**: compila el jar (`mvn package -DskipTests`) y lo sube como artefacto `linker-app`. Es un job independiente del `package` de `ci.yml` (los artefactos de `actions/upload-artifact` no cruzan entre workflows distintos sin plumbing extra), así que se duplica este paso barato en vez de complicar la descarga cross-workflow.
2. **`deploy-prod`**: corre en el contexto del GitHub Environment `prod` (scoping de secrets/variables). Usa `oci-bastion-deploy` para subir el jar a la VM vía el bastión y ejecutar un script remoto que:
   - copia el jar a `/opt/linker1/linker1.jar`
   - reescribe el unit de `systemd` (`linker1.service`), incluyendo ahora `Environment="LD_SDK_KEY=..."` (antes faltaba: `Main.java` hace `System.exit(1)` si esa variable no está presente)
   - reinicia el servicio y verifica `systemctl is-active` + `curl localhost:8080` → `200`
3. **`validate-prod`**: chequeos de solo lectura contra `https://1.n-la-c.app` (`/`, `/app.js`, `/styles.css`, 404 en ruta inexistente). No repite las pruebas mutantes (crear link, alias, conflicto) porque esas ya las cubre el job `api-tests` (Newman) de `ci.yml` contra la misma instancia productiva; correrlas de nuevo en cada deploy solo ensuciaría la base de datos real.
4. **`rollback`**: se dispara si `deploy-prod` o `validate-prod` fallan. Resuelve el tag SemVer anterior (igual que antes) y ahora usa el mismo `oci-bastion-deploy` (en vez de SSH crudo) para correr `bash scripts/rollback.sh <tag>` sobre el checkout que ya existe en la VM.

## Sobre la aprobación manual del Environment `prod`

GitHub permite configurar "required reviewers" en un Environment para pausar un deploy hasta que alguien lo apruebe manualmente — pero esa protección **no está disponible para repos privados en el plan gratuito de GitHub** (solo en repos públicos o en planes Team/Enterprise). Se verificó en Settings > Environments > `prod`: la página no ofrece la sección de reviewers, solo "Deployment branches and tags" y secrets/variables.

En la práctica esto no deja el despliegue sin control: el gate real ya lo da la protección de rama en `main` (PR + 1 aprobación obligatoria + checks de CI en verde, ver [`BRANCH_PROTECTION.md`](BRANCH_PROTECTION.md)) — nada llega a `main` (y por lo tanto dispara `deploy-prod`) sin revisión humana previa. El `environment: prod` en el job se mantiene igual porque sirve para el scoping de secrets/variables, aunque no añada una pausa adicional.

## Secrets y variables requeridos

Ya configurados en el repositorio (verificado en Settings > Secrets and variables > Actions):

| Nombre | Tipo | Nivel | Uso |
| --- | --- | --- | --- |
| `OCI_CLI_USER` | secret | repo | Autenticación de OCI CLI |
| `OCI_CLI_TENANCY` | secret | organización | Autenticación de OCI CLI |
| `OCI_CLI_FINGERPRINT` | secret | repo | Autenticación de OCI CLI |
| `OCI_CLI_KEY_CONTENT` | secret | repo | Clave privada de la API key de OCI |
| `OCI_CLI_REGION` | variable | organización | Región de OCI (`sa-bogota-1`) |
| `OCI_BASTION_OCID` | variable | organización | Bastion de OCI usado para la sesión SSH gestionada |
| `DEPLOYMENT_PRIVATE_KEY` | secret | repo | Llave privada SSH usada por la acción para conectarse a través del bastión |
| `DEPLOYMENT_PUBLIC_KEY` | secret | repo | Llave pública correspondiente, autorizada en la sesión de bastión |
| `LD_SDK_KEY` | secret | repo | Ya la usaba el job `smoke` de `ci.yml`; se reutiliza para el `systemd` unit de prod |
| `OCI_INSTANCE_OCID` | variable | repo | OCID de `vm-linker1-app`, obtenido con `oci compute instance list --compartment-id $CMP_ID` desde Cloud Shell (la VM no fue creada con Terraform, así que `terraform output` no sirve para esto) |

Nota: `DEPLOYMENT_PUBLIC_KEY` es un **secret**, no una variable, a pesar de que la plantilla original del profesor lo referenciaba como `vars.DEPLOYMENT_PUBLIC_KEY` — `pipeline.yml` ya quedó ajustado para leerlo como `secrets.DEPLOYMENT_PUBLIC_KEY`.

También existe un secret `OCI_VM_SSHKEY_CONTENT` en el repo que no usa este pipeline ni la plantilla del profesor — no se tocó, queda sin uso hasta que se identifique para qué era.

## Riesgos abiertos / a verificar

- No fue posible inspeccionar el `action.yml` real de `oci-bastion-deploy` (repo privado `co-eiv-devsecops/material-curso`). Antes de confiar completamente en el pipeline, alguien con acceso debería confirmar:
  - Si `artifact-name` es un input obligatorio (el job `rollback` lo omite a propósito, porque no necesita un jar nuevo).
  - Si el `script:` corre como `ubuntu` con `sudo` sin contraseña ya configurado en la sesión del bastión (igual que asume `deploy.sh` hoy).

## Documentos relacionados

- [Estrategia de Rollback](ROLLBACK_STRATEGY.md)
- [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml)
- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
- [`deploy.sh`](../deploy.sh)
- [`infra/`](../infra/) — Terraform de la VM
