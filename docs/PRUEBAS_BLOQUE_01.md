# Pruebas aisladas del bloque 1

La campaña verifica sesión, cuentas y vínculos mediante HTTP real con Spring Security, JPA, Flyway y PostgreSQL. No sustituye F09 completo ni pruebas de navegador.

## Ejecución

Desde la raíz del backend, con PowerShell, Java 21, PostgreSQL 18 y dependencias Maven disponibles:

```powershell
./scripts/test-block1.ps1
```

El script prepara un cluster nuevo dentro de `target/block1-isolated`, inicia PostgreSQL solo en `127.0.0.1:55431`, crea `coronyx_block1` y ejecuta únicamente `Block1SessionIntegrationTest`. Usa almacenamiento de pruebas separado y detiene PostgreSQL al terminar, incluso si las pruebas fallan. Los archivos del cluster quedan en esa carpeta para inspección; el script no elimina datos.

Si esos programas están en otra ubicación, usar `-PostgresBin` y `-JavaHome`. Para repetir sobre uno de los clusters de prueba ya generados, usar `-ReuseCluster pg-<identificador>` con el nombre exacto de su directorio. Solo admite nombres de ese formato dentro de `target/block1-isolated`; no acepta una ruta arbitraria. Los casos generan identidades únicas y no requieren truncar tablas.

El perfil `block1-test` se encuentra en recursos de pruebas. Su inicializador rechaza una URL/usuario diferentes a los del entorno aislado antes de iniciar los componentes de acceso a BD. El script también rechaza un puerto ocupado; no se conecta por defecto a otro PostgreSQL ya iniciado.

No ejecutar las suites históricas generales contra la BD habitual: contienen escrituras y supuestos sobre cuentas/IDs. Este bloque ejecuta solamente su campaña independiente.

## Cobertura

- Login y `/auth/me` de los cuatro roles con identidades persistidas y separadas.
- Sin concesión de SUPER_ADMIN por correo y rechazo de tokens con ese rol.
- Estados actuales de usuario, membresía, clínica, rol y paciente.
- Paciente directo sin membresía: clínica abierta, rol habilitado y expediente único.
- Desactivar/reactivar, cambiar contraseña/rol y desvincular/revincular: tokens anteriores revocados.
- Reasignación del expediente: el anterior propietario pierde acceso.
- Vinculación por personal administrativo solo a cuenta PACIENTE activa de la misma clínica.
- Rechazo de cuenta ajena, inactiva, de personal o vinculada a otro expediente.
- La edición/alta general de pacientes tampoco elude esas reglas.
- Creación por correo existente y modificación de identidad compartida con otra sede rechazadas sin mutación.
- IDs malformados y expediente ajeno/ocupado no dejan cuentas parcialmente creadas.
- Creación de los cuatro roles, hash persistido, auto-desactivación rechazada y RBAC.
- Claims incoherentes, token expirado, manipulado o sin versión de sesión rechazados.
- Dos vinculaciones simultáneas: una cuenta queda asociada a un único expediente.
- Dos altas simultáneas con el mismo correo: una creación y un conflicto controlado.
- Migraciones V1–V10 y conservación de cuentas históricas en el entorno de prueba.

Los reportes quedan en `target/surefire-reports`. La campaña del 10/10/2026 terminó con **38 pruebas, 0 fallos, 0 errores y 0 omitidas** sobre PostgreSQL **18.6**. Esa versión corresponde al entorno de prueba; no se consultó la versión de la BD de presentación.

## Aplicación a una instalación existente

`V10__session_revocation_version.sql` añade una columna y una restricción sin borrar cuentas, pacientes, citas ni archivos. Las migraciones V1–V9 permanecen intactas. Al arrancar el backend actualizado, Flyway aplicará V10 si corresponde al historial de esa instalación; revisar ese historial y disponer de copia antes de aplicar cambios en la BD de presentación.

Los tokens anteriores a este cambio no tienen `sessionVersion` y se rechazan: iniciar sesión de nuevo. Una cuenta PACIENTE necesita un expediente activo vinculado antes de entrar al portal. Las cuentas con varias membresías o vínculos ambiguos requieren revisar su asignación; el código conserva sus registros y evita elegir una clínica/expediente arbitrarios.
