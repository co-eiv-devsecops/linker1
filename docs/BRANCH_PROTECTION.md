# Protección de Ramas

## Contexto

`main` ya quedó protegida en la entrega anterior (Small Batch Development): requiere PR, 1 aprobación, y los checks `Build`/`Tests`/`Package`/`Summary`/`Smoke Test` en verde antes de mergear.

Desde entonces el equipo adoptó `DEV` como la rama de integración real (el flujo actual es: rama de feature → PR hacia `DEV` → aprobación → merge; `main` recibe cambios ya validados en `DEV`). `DEV`, sin embargo, no tenía ninguna regla de protección configurada — se podía pushear directo sin PR ni revisión. Este documento cierra ese hueco.

## Estado verificado antes de configurar

```bash
gh api repos/co-eiv-devsecops/linker1/branches/DEV/protection
```

Antes de este cambio, la respuesta no incluía `required_status_checks` ni `required_pull_request_reviews` — solo los flags por defecto (`allow_force_pushes: false`, etc.), es decir, sin exigencia real de PR ni de checks para mergear a `DEV`.

## Reglas aplicadas a `DEV`

- **Requiere Pull Request**: no se permite push directo a `DEV`.
- **1 aprobación mínima**, y se descartan aprobaciones previas si se suben nuevos commits (`dismiss_stale_reviews`).
- **Checks de estado obligatorios** (deben pasar en verde antes de mergear), tomados de `.github/workflows/ci.yml`, el único workflow que realmente corre sobre un `pull_request` hacia `DEV`:
  - `Build`
  - `Tests`
  - `Package`
  - `Summary`
  - `Smoke Test`
- **`strict: true`**: la rama debe estar actualizada con `DEV` antes de mergear.
- **Conversaciones resueltas**: no se puede mergear con comentarios de revisión sin resolver.
- **Sin force-push ni borrado de la rama.**
- **`enforce_admins: true`**: las reglas aplican también a administradores del repo, sin bypass.

No se incluyeron como requeridos ni `API Tests (Live)` ni los jobs de `.github/workflows/pipeline.yml` (`Deploy to Development`, `Validate Development`, `Deploy to Production`, `Rollback`). Ese workflow solo se dispara con `on: push` (y `workflow_dispatch`), nunca con `pull_request` — sus jobs simplemente no existen como checks sobre un PR. Esto se descubrió de la forma dura: la primera versión de esta configuración sí los incluyó como requeridos, lo que dejó **todo PR hacia `DEV` bloqueado permanentemente** (`mergeStateStatus: BLOCKED`, sin ningún check posible que lo satisficiera). Se corrigió de inmediato al detectarlo en el primer PR real (#38) que pasó por esta regla.

`required_linear_history` se dejó en `false` para `DEV` (a diferencia de `main`): al ser la rama de integración de varias features en paralelo, tiene sentido permitir merge commits; `main` sigue usando squash-merge como único punto de verdad.

## Cómo se aplicó

```bash
gh api repos/co-eiv-devsecops/linker1/branches/DEV/protection \
  -X PUT --input docs/protection-config.json
```

Usando el archivo [`protection-config.json`](protection-config.json) de este mismo directorio.

## Verificación

```bash
gh api repos/co-eiv-devsecops/linker1/branches/DEV/protection
```

Debe devolver `required_status_checks.contexts` con los 5 checks listados arriba y `required_pull_request_reviews.required_approving_review_count: 1`.

Como prueba adicional, se puede intentar un push directo a `DEV` (sin PR) desde un checkout local:

```bash
git checkout DEV
git commit --allow-empty -m "test: push directo bloqueado"
git push origin DEV
```

Debe rechazarse con un error indicando que se requiere un pull request.

## Impacto en el flujo del equipo

- Todo cambio hacia `DEV` pasa por una rama `feature/*`, `fix/*` o `chore/*` y un PR.
- Cada PR necesita al menos una aprobación de otro integrante.
- Los checks de CI/CD deben pasar antes de poder mergear — ya no es posible mergear con el pipeline roto.
- `main` conserva sus propias reglas (ya configuradas), heredando solo cambios que ya pasaron por `DEV`.

## Documentos relacionados

- [Estrategia de Rollback](ROLLBACK_STRATEGY.md)
- [`.github/CONTRIBUTING.md`](../.github/CONTRIBUTING.md)
- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
- [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml)
