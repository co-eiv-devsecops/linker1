# Linker1

Un acortador de URLs simple construido con **Java** y **Javalin**, con base de datos SQLite y frontend estático.

## ¿Qué es?

Linker1 es una aplicación web que permite:
- Ingresar una URL larga
- Generar un código corto (8 caracteres)
- Redirigir automáticamente cuando accedes al código corto

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
├── infra/                    # Infraestructura como código (Terraform)
│   ├── main.tf
│   ├── variables.tf
│   ├── provider.tf
│   ├── outputs.tf
│   ├── cloud-init.yaml
│   └── terraform.tfvars.example
├── src/Main.java           # Backend con rutas API
├── public/                 # Frontend estático
│   ├── index.html         # Interfaz web
│   ├── app.js             # Lógica del cliente
│   └── styles.css         # Estilos
├── pom.xml                # Configuración Maven
├── deploy.sh              # Script de despliegue (OCI)
└── README.md              # Este archivo
```

## Integrantes

- ANDERSSON DAVID SÁNCHEZ MÉNDEZ
- ANDERSON FABIAN GARCIA NIETO
- DANIEL PATIÑO MEJIA
- ESTEBAN AGUILERA CONTRERAS
- JUAN JOSE DIAZ GOMEZ
