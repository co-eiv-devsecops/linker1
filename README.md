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

### Verificar que está funcionando

Debería verse la página estática en la URL:
```bash
https://1.n-la-c.app/
```
Así:

![alt text](doc\image.png)


## Estructura del Proyecto

```
linker1/
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