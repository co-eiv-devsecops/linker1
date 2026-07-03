# Linker1

Un acortador de URLs simple construido con **Java** y **Javalin**, con base de datos SQLite y frontend estático.

## ¿Qué es?

Linker1 es una aplicación web que permite:
- Ingresar una URL larga
- Generar un código corto (8 caracteres) o definir un **alias personalizado**
- Redirigir automáticamente cuando accedes al código corto o al alias

## API

- `POST /link` — Crea un enlace corto. Body JSON: `{"url": "https://..."}`. Si la URL ya existía, responde `200` con el mismo código; si es nueva, responde `201`.
  - Alias personalizado (opcional): `{"url": "https://...", "alias": "mi-alias"}`. Reglas del alias: solo letras, números, guion (`-`) y guion bajo (`_`), entre 1 y 64 caracteres, sin espacios. Si el alias ya está en uso por otra URL, responde `409`; si el alias no es válido, responde `400`.
- `GET /{id}` — Redirige (`301`) a la URL asociada al código corto o alias. Responde `404` si no existe.

## Requisitos

- **Java 21**
- **Maven 3.7+**
- **Git**

## Instalación y Ejecución en la VM

### 1. Clonar el repositorio
```bash
git clone <repo-url>
cd linker1
```

### 2. Compilar el proyecto
```bash
mvn clean package
```

### 3. Ejecutar la aplicación
```bash
java -jar target/linker1-1.0-jar-with-dependencies.jar
```

La aplicación estará disponible en `http://localhost:8080`

### 4. Despliegue

Se creó el script para poder desplegar de manera más sencilla, lo único necesario es tener el repo clonado en la VM y usar el comando:

```bash
bash deploy.sh
```

Este script:
- Hace pull de los cambios del repo
- Instala dependencias (Java, Maven, Nginx)
- Compila el proyecto
- Configura el servicio systemd
- Inicia Nginx como reverse proxy
- Expone la aplicación en puerto 8080

### 5. Paridad de entornos (IaC)

Además del despliegue manual con `deploy.sh`, el proyecto incluye infraestructura como código en `infra/` usando Terraform, que permite crear una VM nueva con todo lo necesario para correr Linker1 de forma reproducible.

**Qué crea:** una instancia compute en OCI (misma subnet y compartment del equipo), configurada automáticamente vía cloud-init: instala Java 21, Maven, Nginx, clona el repo, compila y deja el servicio corriendo — sin pasos manuales.

**Requisitos:** Terraform >= 1.5, acceso a OCI (vía OCI Cloud Shell, que ya viene autenticado).

**Uso:**
```bash
cd infra
cp terraform.tfvars.example terraform.tfvars
# Completa terraform.tfvars con tus valores (compartment_id, subnet_id, image_id, ssh_public_key)
terraform init
terraform plan
terraform apply
```

**Destruir el entorno:**
```bash
terraform destroy
```
---
### Verificar que está funcionando

Debería verse la página estática en la URL:
```bash
https://1.n-la-c.app/
```
Así:

![alt text](doc/image.png)

## Pipeline y calidad

El pipeline de CI (`.github/workflows/ci.yml`) corre en cada `push` a `main` y en cada `pull_request`, con estos jobs:

- **Build**: compila el código (`mvn compile`).
- **Tests**: ejecuta las pruebas unitarias y de integración (`mvn test`), y verifica el umbral de cobertura con JaCoCo (`mvn verify`).
- **Package**: genera el jar ejecutable (`target/linker1-1.0-jar-with-dependencies.jar`) junto con un script de arranque (`scripts/linker1`).
- **Summary**: confirma que el artefacto empaquetado existe y es ejecutable.
- **Smoke Test**: descarga el jar empaquetado, lo levanta y prueba en caliente las rutas principales (estáticos, crear enlace, alias, redirección, 404).
- **API Tests (Live)**: solo en `push` a `main`, corre una colección de Postman con [Newman](https://github.com/postmanlabs/newman) contra la instancia desplegada (`https://1.n-la-c.app/`). No se ejecuta en pull requests para no afectar el entorno productivo compartido.

`main` está protegida: requiere al menos una aprobación y que los checks Build/Tests/Package/Summary/Smoke Test pasen antes de poder hacer merge.

### Cobertura de pruebas

El proyecto usa [JaCoCo](https://www.jacoco.org/jacoco/) para medir cobertura de código. El objetivo es 100% de cobertura de líneas (excluyendo `Main.class`, el punto de entrada que abre una conexión real a la base de datos y levanta un servidor real — por convención se excluye de las métricas de cobertura unitaria). El reporte se genera en `target/site/jacoco/` con `mvn verify`.

### Pruebas de API con Postman/Newman

La colección `postman/linker1.postman_collection.json` cubre: rutas estáticas, creación de enlaces (caso válido, duplicado, URL inválida), alias (caso válido, alias repetido, alias inválido) y redirección (por id, por alias, 404). Para correrla localmente:

```bash
npx newman run postman/linker1.postman_collection.json --env-var "baseUrl=http://localhost:8080"
```

## Ejecución con Dev Container (Visual Studio Code)

Como alternativa a la instalación local, el proyecto puede ejecutarse utilizando un **Dev Container**. Esta opción proporciona un entorno de desarrollo reproducible y evita instalar manualmente las dependencias del proyecto en el equipo.

### Requisitos

- Docker Desktop (Windows/macOS) o Docker Engine (Linux).
- Visual Studio Code.
- Extensión **Dev Containers** de Microsoft.

### 1. Abrir el proyecto en el Dev Container

Con el repositorio ya clonado y abierto en Visual Studio Code:

1. Presiona `Ctrl + Shift + P`.
2. Ejecuta el comando:

```text
Dev Containers: Reopen in Container
```

Si es la primera vez que se abre el proyecto, Visual Studio Code construirá automáticamente la imagen del contenedor y configurará el entorno de desarrollo.

> **Nota:** Este proceso puede tardar algunos minutos dependiendo del rendimiento del equipo y de la velocidad de la conexión a Internet.

### 2. Esperar la configuración del entorno

Una vez construido el contenedor, Visual Studio Code volverá a abrir el proyecto dentro del entorno de desarrollo configurado.

Todas las dependencias necesarias para el proyecto estarán disponibles automáticamente.

### 3. Compilar el proyecto

Desde la terminal integrada de Visual Studio Code, ejecutar:

```bash
mvn clean package
```

### 4. Ejecutar la aplicación

En la misma terminal ejecutar:

```bash
java -jar target/linker1-1.0-jar-with-dependencies.jar
```

### 5. Probar la aplicación

Abrir el navegador y acceder a:

```text
http://localhost:8080
```

### 6. Validar el funcionamiento

1. Ingresar una URL completa (por ejemplo: `https://www.google.com`).
2. Generar el enlace corto.
3. Copiar el código generado.
4. Acceder a:

```text
http://localhost:8080
```

para verificar que la redirección se realiza correctamente.

### Reconstruir el Dev Container

Si se realizan cambios en el `Dockerfile` o en el archivo `devcontainer.json`, reconstruir el entorno desde la paleta de comandos (`Ctrl + Shift + P`) ejecutando:

```text
Dev Containers: Rebuild and Reopen in Container
```


## Estructura del Proyecto

```
linker1/
├── .github/                  # Workflows de CI y archivos de salud de comunidad
│   ├── workflows/ci.yml      # Pipeline: build, tests, package, summary, smoke test, API tests
│   ├── CONTRIBUTING.md       # Estándares de colaboración (branching, TDD, PRs)
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── CODEOWNERS
├── infra/                    # Infraestructura como código (Terraform)
│   ├── main.tf
│   ├── variables.tf
│   ├── provider.tf
│   ├── outputs.tf
│   ├── cloud-init.yaml
│   └── terraform.tfvars.example
├── src/
│   ├── Main.java             # Arranque: conexión a BD, servidor Javalin, registro de rutas
│   └── linker/                # Lógica de negocio y acceso a datos
│       ├── Link.java
│       ├── LinkService.java   # Validación, generación de ids, alias
│       ├── LinkRepository.java # Acceso JDBC a SQLite
│       ├── AliasConflictException.java
│       └── routes/            # Handlers HTTP (Javalin)
│           ├── LinkRoutes.java
│           └── StaticRoutes.java
├── test/                      # Pruebas unitarias y de integración (JUnit 5)
├── postman/                   # Colección de Postman para pruebas de API (Newman)
├── public/                 # Frontend estático
│   ├── index.html         # Interfaz web
│   ├── app.js             # Lógica del cliente
│   └── styles.css         # Estilos
├── scripts/linker1         # Script de arranque ejecutable (empaquetado junto al jar)
├── pom.xml                # Configuración Maven
├── deploy.sh              # Script de despliegue (OCI)
└── README.md              # Este archivo
```

## Contribuir

El equipo trabaja con ramas cortas por tarea y *small batch development*: cada cambio va en su propia rama (`feature/...`, `refactor/...`, `fix/...`), se abre un pull request pequeño y enfocado, y se hace *squash and merge* hacia `main` una vez aprobado y con el pipeline en verde. El desarrollo sigue TDD (rojo → verde → refactor), reflejado en el historial de commits de cada PR.

Ver [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) para el detalle completo del flujo de trabajo, y [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md) para el checklist que debe cumplir cada PR.

## Integrantes

- ANDERSSON DAVID SÁNCHEZ MÉNDEZ
- ANDERSON FABIAN GARCIA NIETO
- DANIEL PATIÑO MEJIA
- ESTEBAN AGUILERA CONTRERAS
- JUAN JOSE DIAZ GOMEZ
