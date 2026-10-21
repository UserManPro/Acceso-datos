# AadTex Store Ingestion Engine (App 1)

## Descripción

He desarrollado esta aplicación para la actividad RA1 de Acceso a Datos. El programa recoge lotes de ventas, valida y procesa sus CSV, conserva los caracteres internacionales, descifra el lote financiero en memoria, lee el lote ZIP de EE. UU., copia las firmas JPEG y escribe un resumen JSON por lote en un bucket local simulado.

La alerta de soporte se simula únicamente mediante un registro de error. La carpeta `output/bucket` es almacenamiento local de ejemplo; no es un servicio real de nube.

## Datos del proyecto

- Java configurado: **17** (`java.version` del `pom.xml`).
- Spring Boot: **4.1.1**, versión que ya tenía el proyecto y que he conservado.
- Construcción: Maven Wrapper.
- Paquete de aplicación: `com.aadtex.aadtex`.
- Repositorio GitHub: [https://github.com/UserManPro/Acceso-datos](https://github.com/UserManPro/Acceso-datos).

## Organización del código

- `AadApplication`: punto de entrada de Spring Boot y activación de `@EnableScheduling`.
- `StoreDataProperties`: resuelve desde una sola raíz todas las rutas de datos.
- `CronStoreIngestionScheduler`: modalidad cron configurable.
- `StoreIngestionScheduler`: modalidad académica `@Scheduled(fixedRate = 5000)`.
- `StoreBatchProcessor`: crea directorios, detecta archivos, procesa CSV/ENC/ZIP/JPG, copia firmas, aísla errores y archiva lotes.
- `CsvSalesService`: decodifica en UTF-8, valida el encabezado y filas y crea ventas con importes `BigDecimal`.
- `CryptoService`: descifra AES-128/ECB/PKCS5Padding sin guardar el texto financiero en disco.
- `CloudBucketService`: genera JSON local válido y evita reutilizar nombres de resumen.
- `MultiLanguageDataGenerator`: como `CommandLineRunner`, crea datos de ejemplo solo cuando cada archivo todavía no existe.
- `Sale` y `BatchSummary`: modelos de datos.

## Directorios de datos

La raíz de datos no forma parte del proyecto Java. Por defecto he configurado `C:/workspace/store_data` en `src/main/resources/application.properties`, en la propiedad `aadtex.data-root`. Puedo cambiar esa ruta una vez, sin modificar las clases:

```properties
aadtex.data-root=C:/workspace/store_data
```

Spring permite sustituirla al arrancar con `--aadtex.data-root="D:/mis-datos"` o mediante la variable de entorno `AADTEX_DATA_ROOT`.

La aplicación crea las carpetas necesarias:

```text
C:/workspace/store_data/
├── keys/AadTexSecret.key
├── input/
└── output/
    ├── processed/
    │   └── signatures/
    ├── error/
    └── bucket/aadtex-daily-reports/2026/09/
```

Al iniciar, el generador solo crea los ficheros que faltan. Por eso no reemplaza lotes pendientes ni altera un fichero de entrada que haya preparado manualmente. Para repetir una demostración, primero guardo cualquier entrada que quiera conservar y retiro los archivos ya procesados de `output/processed/`; después retiro de `input/` únicamente los lotes que quiera regenerar. No se regeneran continuamente durante cada ciclo del Scheduler.

## Ficheros de entrada generados

El generador crea la clave `AadTexSecret2026` en `keys/AadTexSecret.key` y prepara:

- `daily_sales_spain.csv` con los tres registros de España, incluida `Vía`.
- `daily_sales_china.csv` con los dos registros CJK `上海南京东路店`.
- `daily_sales_financial.enc` con el CSV financiero cifrado.
- `batch_usa.zip` con las entradas `daily_sales_usa.csv` y `signature_usa.jpg`.
- `signature_spain.jpg`, `signature_china.jpg` y `signature_financial.jpg`.
- `daily_sales_broken_store.csv`, de cero bytes.

Los documentos de ejemplo describen las firmas por sus bytes mágicos y no adjuntan imágenes originales. Por ello genero con `ImageIO` una pequeña imagen JPEG válida de prueba y reutilizo sus bytes como fixture simulado para las cuatro firmas.

Al finalizar correctamente, los CSV/ENC/ZIP y firmas asociados se mueven a `output/processed/`; las copias JPEG quedan en `output/processed/signatures/`. Un archivo vacío o malformado se mueve a `output/error/`. Si el sistema de archivos permite el movimiento atómico, lo uso; si no, repito el movimiento con `REPLACE_EXISTING` sin la opción atómica.

## Cómo funciona el Scheduler

En Spring, `fixedRate` mide el intervalo entre inicios sucesivos de una tarea: `fixedRate = 5000` pide un inicio cada cinco segundos. Una expresión cron representa los momentos del calendario en que se activa la tarea. En este proyecto selecciono solo una implementación mediante `aadtex.scheduler.mode`, para evitar dos tareas compitiendo por los mismos archivos.

La configuración inicial prueba cron cada cinco segundos:

```properties
aadtex.scheduler.mode=cron
aadtex.scheduler.cron=*/5 * * * * *
```

Para dejar la ejecución definitiva a las 03:00 todos los días, mantengo el modo `cron` y cambio el segundo valor:

```properties
aadtex.scheduler.mode=cron
aadtex.scheduler.cron=0 0 3 * * *
```

Para demostrar literalmente el requisito del enunciado `@Scheduled(fixedRate = 5000)`, cambio el modo:

```properties
aadtex.scheduler.mode=fixed-rate
```

Cuando uso `fixed-rate`, se ignora la expresión cron y solo se activa `StoreIngestionScheduler`. El cambio también se puede pasar como argumento de ejecución, por ejemplo `--aadtex.scheduler.mode=fixed-rate`. El cron configurable se pasa como `--aadtex.scheduler.cron="0 0 3 * * *"`.

## Lectura UTF-8 y cálculo de importes

Leo los CSV con `BufferedReader` e `InputStreamReader` indicando siempre `StandardCharsets.UTF_8`. Compruebo que el encabezado sea `StoreID,StoreName,SalesAmount,Category,TransactionDate`; omito líneas vacías y aviso de las filas incorrectas sin cancelar el resto del lote. Los importes se convierten en `BigDecimal` para evitar errores de redondeo binario.

Totales esperados de los ejemplos:

| Lote | Registros válidos | Total |
|---|---:|---:|
| España | 3 | 20100.80 |
| China | 2 | 41300.80 |
| Financiero | 1 | 95000.00 |
| EE. UU. | 3 | 68450.75 |

## Descifrado financiero

Uso `AES/ECB/PKCS5Padding`, tal como requiere el ejercicio. Compruebo que la clave codificada en UTF-8 tenga 16 bytes. Los datos descifrados se entregan desde memoria a un `ByteArrayInputStream`; no escribo el CSV financiero en claro al disco y no incluyo importes financieros en registros de error. ECB es el modo exigido por la actividad, aunque en sistemas reales se suelen preferir modos autenticados.

## ZIP y firmas

Leo el ZIP mediante `ZipInputStream` y proceso la entrada CSV en memoria, sin extraerla como fichero temporal. Solo acepto los nombres exactos de las entradas solicitadas; rechazo directorios, rutas anidadas y nombres que puedan escapar de su ubicación prevista. La firma del ZIP se copia directamente a la carpeta de firmas.

Para copias de JPG independientes uso `BufferedInputStream`, `BufferedOutputStream` y un buffer declarado como `byte[4096]`. Compruebo que la firma empiece con los bytes JPEG `FF D8 FF` y decodifico una región de 8×8 píxeles para verificar que se puede leer sin cargar la imagen completa; no convierto imágenes a texto.

## Resumen JSON

Guardo cada resumen en `output/bucket/aadtex-daily-reports/2026/09/` con un nombre `summary_batch_[ID].json`. El identificador incluye el archivo de origen y un UUID, de modo que dos ejecuciones no sobrescriben el mismo resumen. Un ejemplo (fecha e identificador variables):

```json
{
  "id": "daily_sales_spain_csv_...",
  "source": "daily_sales_spain.csv",
  "processedAt": "2026-09-13T01:00:00Z",
  "recordCount": 3,
  "total": 20100.80
}
```

## Ejecución desde IntelliJ IDEA

1. Abro el proyecto Maven existente y espero a que IntelliJ importe dependencias.
2. Compruebo que el Project SDK sea JDK 17 o superior y que la propiedad Maven `java.version` siga en 17.
3. Ejecuto `AadApplication`.
4. Reviso los logs y las carpetas bajo la ruta indicada en `aadtex.data-root`.

La máquina indicada para esta actividad tiene Temurin 21; la compilación se mantiene compatible con Java 17.

## Ejecución mediante Maven

Desde la raíz del proyecto:

```powershell
.\mvnw.cmd spring-boot:run
```

Para cambiar las propiedades en la ejecución:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--aadtex.data-root=C:/workspace/store_data,--aadtex.scheduler.mode=cron,--aadtex.scheduler.cron=*/5 * * * * *"
```

En algunos terminales se puede ejecutar también `mvn spring-boot:run` si Maven está instalado y disponible en `PATH`.

## Pruebas

Ejecuto las pruebas automatizadas desde la raíz:

```powershell
.\mvnw.cmd test
```

Las pruebas usan directorios temporales e incluyen AES correcto y errores de clave, CSV español y chino, totales, filas malformadas, generación de datos, ZIP, firmas, archivo vacío, creación de rutas y las anotaciones cron y `fixedRate` del Scheduler.

## Ejemplos de logs

Al procesar correctamente un lote se registra el origen, número de registros, total y nombre del resumen. Para el lote financiero omito el total en el log para no exponer información financiera. Por ejemplo:

```text
INFO  Lote procesado: origen=daily_sales_spain.csv, registros=3, total=20100.80, resumen=summary_batch_...
ERROR [ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: daily_sales_broken_store.csv
```

El texto de alerta confirma únicamente la simulación por log; la aplicación no envía correo real.

## Solución de problemas

- **No aparecen ficheros de entrada:** compruebo `aadtex.data-root` y que `aadtex.generator.enabled=true`. El generador no reemplaza archivos existentes.
- **No se puede descifrar el lote financiero:** compruebo que `keys/AadTexSecret.key` contenga exactamente `AadTexSecret2026` y que el `.enc` se haya generado con AES-128/ECB/PKCS5Padding.
- **Una venta no aparece en el total:** reviso el encabezado, separadores, importe decimal y fecha `yyyy-MM-dd`; las filas inválidas se omiten con un aviso.
- **El Scheduler no procesa cada cinco segundos:** verifico `aadtex.scheduler.mode=cron` y `aadtex.scheduler.cron=*/5 * * * * *`. La expresión definitiva es `0 0 3 * * *`.
- **El wrapper no arranca:** verifico que `JAVA_HOME` apunte a JDK 17 o superior. En el entorno de desarrollo puede ser necesario reimportar Maven en IntelliJ.
- **Un ZIP o JPEG se aísla:** compruebo los nombres exactos de sus entradas y los bytes iniciales `FF D8 FF` de la firma.

## Discrepancias identificadas en el material

El enunciado general nombra el archivo vacío `daily_sales_broken.csv`, mientras que el documento detallado de ficheros y la solicitud del proyecto especifican `daily_sales_broken_store.csv`. He usado este último nombre. El enunciado menciona `/workspace/store_data/`; en Windows he configurado `C:/workspace/store_data` como se solicita. El enlace de GitHub queda pendiente porque el repositorio local no tenía un remoto definido.
