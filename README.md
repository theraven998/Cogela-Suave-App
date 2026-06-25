# CógelaSuave 🌬️

App Android nativa (Kotlin + Jetpack Compose) que interpone una **pausa consciente**
antes de abrir las apps que tú elijas. Cuando intentas abrir una app vigilada, aparece
una pantalla de respiración a pantalla completa con un temporizador obligatorio y, al
terminar, te pregunta si de verdad quieres entrar o es solo inercia.

Estilo *one sec*, pero de uso personal y con el código limpio por si algún día se publica.

---

## Funcionalidad

- **Intercepción**: detecta la apertura de apps vigiladas vía `AccessibilityService` y dibuja
  un overlay (`SYSTEM_ALERT_WINDOW`) por encima.
- **Respiración**: círculo que se expande (Inhala…) y se contrae (Exhala…).
- **Espera obligatoria**: el botón *Abrir* está bloqueado hasta que termina el temporizador
  (por defecto 10 s, configurable global y por app).
- **Contador diario**: cuántas veces has intentado abrir esa app hoy (se reinicia a medianoche).
- **Decisión**: *Abrir* (deja pasar) o *Mejor no* (te lleva al home).
- **Configuración**: lista de apps instaladas con toggle, ajuste de tiempos, y estadísticas
  diarias (intentos, veces que elegiste *Mejor no*, tiempo estimado ahorrado).

Todo se guarda **localmente** (Room). No se envía nada a ningún servidor.

### Extras
- **Pausa de vigilancia (snooze)**: desactiva la intercepción durante 15/30/60 min desde la
  pestaña *Tiempo* o desde un **tile de Ajustes Rápidos** (un toque pausa, otro reanuda).
- **Modo estricto**: bloquea la opción de snooze para que no puedas saltarte tu propia pausa.
- **Histórico de 7 días**: sección *Últimos 7 días* en *Estadísticas* con totales y mini-gráfico
  de barras por día (altura = intentos, franja resaltada = veces que frenaste).
- **Animación de respiración** con halo multicapa, pulso continuo, transición de color
  inhala/exhala y anillo de progreso del temporizador.

---

## Arquitectura

MVVM limpia por capas:

```
com.cogelasuave
├── data/                 # Room (entities/dao), repos impl, PackageManager, fecha
│   ├── local/            # AppDatabase, entities, DAOs
│   ├── repository/       # *RepositoryImpl
│   └── system/           # InstalledAppsProvider, SystemDateProvider
├── domain/               # Modelos, interfaces de repositorio, casos de uso (sin Android)
│   ├── model/
│   ├── repository/
│   └── usecase/
├── service/              # AccessibilityService + overlay (WindowManager + Compose)
│   └── overlay/
├── ui/                   # Compose: permisos, apps, tiempos, estadísticas, overlay
│   ├── apps/  settings/  stats/  permissions/  overlay/  theme/
└── di/                   # Módulos Hilt
```

- **DI**: Hilt.
- **UI**: Jetpack Compose + Material 3, soporte modo oscuro automático.
- **Persistencia**: Room.
- **minSdk 26**, **targetSdk/compileSdk 35**, Java 17, Kotlin 2.0.

---

## Compilar el APK

Requisitos: **JDK 17** y el **Android SDK** (instalado por Android Studio o `sdkmanager`).

### Opción A — Android Studio (recomendado)
1. *File → Open* y selecciona la carpeta del proyecto.
2. Deja que sincronice Gradle (descarga dependencias).
3. *Build → Build Bundle(s)/APK(s) → Build APK(s)*.
4. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Opción B — Línea de comandos
```bash
# Indica dónde está tu Android SDK (o crea local.properties con sdk.dir=...)
export ANDROID_HOME=$HOME/Android/Sdk

# APK de depuración
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# APK de release sin firmar
./gradlew assembleRelease

# Tests unitarios (JVM)
./gradlew test
# Tests instrumentados de Room (requieren emulador/dispositivo)
./gradlew connectedAndroidTest
```

> Si Gradle se queja de que no encuentra el SDK, crea un archivo `local.properties` en la raíz:
> ```
> sdk.dir=/ruta/a/tu/Android/Sdk
> ```

---

## Instalar (sideload por ADB)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Conceder los permisos (primer arranque)

La app abre directamente la **pantalla de configuración guiada**. Necesita **dos** permisos:

### 1. Mostrar sobre otras apps (overlay)
- Pulsa **Conceder** en el Paso 1.
- Se abre *Ajustes → Aplicaciones que pueden mostrarse sobre otras*.
- Activa el interruptor para **CógelaSuave** y vuelve atrás.

### 2. Servicio de accesibilidad
- Pulsa **Abrir ajustes** en el Paso 2.
- En *Ajustes → Accesibilidad*, busca **CógelaSuave – pausa consciente**
  (suele estar en «Apps instaladas» o «Servicios descargados»).
- Actívalo y confirma el diálogo del sistema.

Al volver a la app, la pantalla de permisos se cierra sola en cuanto detecta ambos
concedidos (se re-comprueba al volver a primer plano).

> En algunos fabricantes (Xiaomi, Huawei, Samsung…) conviene además **fijar la app**
> y permitir su ejecución en segundo plano / autoarranque para que el servicio de
> accesibilidad no se cierre.

---

## Uso

1. Pestaña **Apps**: activa el toggle de las apps que quieras vigilar. Toca «Espera» para
   darle un tiempo propio o usar el global.
2. Pestaña **Tiempo**: ajusta la espera global y la duración estimada de sesión (para el
   cálculo de tiempo ahorrado).
3. Pestaña **Estadísticas**: mira tus intentos del día, cuántos frenaste y el tiempo ahorrado.

Cuando abras una app vigilada, aparecerá la pausa. Respira. Y decide.

---

## Notas técnicas

- La detección usa `TYPE_WINDOW_STATE_CHANGED`. Se ignora la propia app y la UI del
  sistema, y se evita re-disparar mientras sigues dentro de la misma app.
- El overlay se dibuja con `WindowManager` + `TYPE_APPLICATION_OVERLAY`, renderizando
  Compose mediante un `LifecycleOwner`/`SavedStateRegistryOwner` propio.
- `QUERY_ALL_PACKAGES` se usa solo para listar tus apps lanzables en la pantalla de
  configuración; no se comparte nada.
