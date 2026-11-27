# Despliegue en AWS (Learner Lab) con blue/green

Alternativa a `DEPLOYMENT.md` (OCI). Sirve **toda** la aplicación (frontend, API, contador, feature flag) y replica el flujo del curso: VM nueva (verde) → pruebas k6 solo contra verde → 90/10 → 100% verde → se apaga la azul → rollback si algo falla.

> **Estado:** escrito y revisado por sintaxis, pero **no se ha ejecutado contra una cuenta AWS real** (desde donde se preparó no hay credenciales). El primer despliegue puede requerir ajustes; la sección «Si algo falla» cubre lo probable.

## Arquitectura
```
Usuario ──HTTPS──▶ CloudFront (*.cloudfront.net, sin caché)
                       │ HTTP
                       ▼
                 ALB linker1-alb ── :80 pesos ──▶ target group A / B
                       │ :8081 (solo IP del CI, solo durante pruebas)
                       ▼
                 VM EC2 (Java + app) en el grupo "inactivo" = verde
                       │ 3306
                       ▼
                 RDS MySQL (links persistentes)
```
- Terraform (`infra-aws/`) crea **una sola vez** el ALB, los dos grupos, los security groups y CloudFront. No usa IAM, por eso funciona en Learner Lab.
- Cada despliegue lo hace `scripts/aws/bluegreen.sh` (workflow `bluegreen-aws.yml`). El estado vive en AWS (pesos del listener y etiquetas), sin variables de GitHub.
- HTTPS sin dominio: CloudFront con su certificado `*.cloudfront.net`. La caché está desactivada para que cada `GET /{id}` llegue a la app y cuente (HU-04).

## Paso a paso

**1. Iniciar el lab.** En AWS Academy: *Start Lab*, esperar el punto verde, *AWS Details → Show* y copiar el bloque «AWS CLI» a `~/.aws/credentials`. Región `us-east-1`.

**2. Crear la infraestructura base (una vez, desde tu computador).**
```bash
cd infra-aws
terraform init
terraform apply -var 'db_password=ELIGE_UNA_CLAVE_DE_8+_CARACTERES'   # escribe "yes"; RDS tarda ~10 min
terraform output       # anota https_url y alb_url
```
Necesitas Terraform ≥ 1.5. Guarda la carpeta `infra-aws/` con su `terraform.tfstate`: lo necesitas para `terraform destroy` al final (si no, borra a mano ALB, target groups, security groups y CloudFront).

**3. Pasar las credenciales a GitHub.**
```bash
gh auth login
scripts/aws/set-lab-secrets.sh Anderfg13/linker1
```
**Repítelo cada vez que reinicies el lab** (las credenciales cambian y caducan en pocas horas). Guarda la **misma** clave de la base: `gh secret set MYSQL_PWD` (sin ella la app cae a SQLite local y los enlaces no persisten). Opcional: `gh secret set LD_SDK_KEY` si quieres mostrar el feature flag (ver paso 6). Opcional: `gh variable set AWS_INSTANCE_PROFILE --body LabInstanceProfile` para poder entrar a la VM por SSM.

**4. Desplegar.** GitHub → Actions → *Blue/Green Deployment (AWS)* → *Run workflow* → rama `main` (o `develop`). Tarda ~10–20 min la primera vez (la VM clona y compila con Maven). Al terminar, el resumen del run muestra la URL HTTPS.

**5. Verificar.** Abre la URL `https://xxxx.cloudfront.net`: debe verse el frontend. Prueba crear un enlace, abrirlo y «Ver aperturas». Con `curl -i https://xxxx.cloudfront.net/<id>` debe responder `302`.

**6. Feature flag `new-ui` en la presentación.** Crea (o reutiliza) una cuenta de LaunchDarkly con el flag booleano `new-ui`, guarda su *SDK key* en el secreto `LD_SDK_KEY` y vuelve a desplegar. Para la demo, activa/desactiva el flag en LaunchDarkly y recarga la página: cambia entre v1 y v2 sin reiniciar. Sin la clave la app funciona igual, siempre con la v1.

**7. Segundo despliegue (para mostrar el blue/green).** Vuelve a ejecutar el workflow: verás en el log la VM verde, las pruebas k6, el 90/10 y el retiro de la VM azul.

**8. Al terminar** (el ALB y RDS cobran por hora): `cd infra-aws && terraform destroy -var 'db_password=x'` y termina las instancias `linker1-*`.

## Limitaciones conocidas
- **Persistencia:** con RDS (por defecto) los enlaces sobreviven a los despliegues; la app usa MySQL cuando `LINKER_STORAGE=mysql`, que el script activa si existe la base y `MYSQL_PWD` está definido. Si usas `-var enable_rds=false` o no defines `MYSQL_PWD`, vuelve a SQLite local y cada despliegue empieza con base vacía. Las pruebas k6 también escriben en esa base.
- Con RDS, `/healthz` responde 200 (usa las mismas credenciales).
- Sin RDS, `/healthz` responde 503; no afecta, el ALB comprueba `/`.
- Learner Lab puede parar el lab y las instancias; al reiniciarlo, las VMs vuelven pero con IP nueva y pueden tardar en estar sanas. Si el sitio queda caído, lo más rápido es volver a ejecutar el workflow.
- Los workflows de OCI (`bluegreen.yml`, etc.) siguen en el repo y fallarán en este fork por falta de secretos; no afectan al despliegue AWS.
- OpenTelemetry/Grafana no están cableados en esta variante.

## Si algo falla
| Síntoma | Causa probable |
|---|---|
| `AWS credentials missing or expired` | Lab detenido o credenciales viejas → paso 1 y 3 |
| `ALB_ARN not found` | No se ejecutó `terraform apply`, o es otra cuenta/región |
| `AccessDenied` al crear CloudFront | El lab no lo permite → `terraform apply -var enable_cloudfront=false` y usar `alb_url` (HTTP) |
| Verde nunca queda sano | El log imprime la salida de consola de la VM; suele ser la compilación Maven o `repo_url` privado |
| k6 no conecta al puerto 8081 | La regla del security group tardó en propagar; reintenta el workflow |
