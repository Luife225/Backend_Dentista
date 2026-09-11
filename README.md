# CORONYX Backend

Backend del **Sistema Integral de Gestion Odontologica con Asistente de IA**.

## Tecnologias

- Java 21
- Spring Boot 4.0.8
- Spring Web MVC
- Spring Data JPA
- PostgreSQL 17
- Flyway
- Maven Wrapper
- Docker Compose

## Requisitos

- JDK 21
- Docker Desktop con Docker Compose
- Git

No es necesario instalar Maven ni PostgreSQL de forma global.

## Configuracion local

1. Copiar `.env.example` como `.env`.
2. Reemplazar las contrasenas de ejemplo por una clave local.
3. Cargar en la terminal las variables `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.
4. Iniciar PostgreSQL:

```powershell
docker compose up -d
```

5. Ejecutar el backend:

```powershell
.\mvnw.cmd spring-boot:run
```

6. Verificar el estado:

```text
http://localhost:8080/actuator/health
```

## Base de datos

El esquema se administra exclusivamente mediante archivos versionados en:

```text
src/main/resources/db/migration
```

Convencion de nombres:

```text
V1__create_initial_schema.sql
V2__add_new_feature.sql
```

No se deben modificar manualmente estructuras que ya esten representadas por una migracion. Hibernate utiliza `ddl-auto: validate` y no crea ni altera tablas.

## Flujo Git

Las funcionalidades se desarrollan en ramas creadas desde `develop`:

```text
feature/<descripcion>-<responsable>
```

Los commits del curso utilizan el formato:

```text
V1: descripcion del cambio
```

Los Pull Requests de funcionalidades se dirigen primero a `develop`. La rama `main` se reserva para versiones revisadas.
