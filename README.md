# Rio4 OBD Launcher — proyecto Android

Launcher horizontal para radios Android, con panel visual Kia Rio 4, reloj/fecha, lectura OBD2 en vivo, controles multimedia y accesos inferiores configurables.

## Funciones incluidas
- Bluetooth clásico SPP para adaptadores ELM327 emparejados (conexión segura y alternativa para clones que solo aceptan SPP inseguro).
- RPM por PID 010C, temperatura de refrigerante por PID 0105 y voltaje informado por el adaptador mediante ATRV. Los valores quedan en blanco hasta recibir una lectura real.
- Los seis accesos inferiores se pueden reasignar manteniendo pulsado cada uno.
- Controles multimedia Android (anterior, reproducir/pausar, siguiente).
- No se ejecuta descubrimiento Bluetooth desde la app; se elige un adaptador ya emparejado desde Ajustes. Así se evita la llamada `cancelDiscovery()` que originaba el error de permiso `BLUETOOTH_SCAN`.

## Importante: sincronización de luces del auto
La imagen del vehículo está sin matrícula. El cambio automático de luces todavía requiere identificar la interfaz CAN/MCU de esta radio. El estado de luces no es un PID OBD2 genérico y cada fabricante de radio/CANBUS expone esa señal de forma distinta. No se simula ni se inventa un estado. Para terminar esa integración hace falta el modelo exacto de la radio y la versión/nombre de la aplicación CANBUS o MCU (pantalla “Acerca del dispositivo” y pantalla de la app CANBUS).

## Compilar APK en GitHub
1. Sube el contenido completo de este ZIP a la raíz de un repositorio GitHub.
2. Abre **Actions** y ejecuta **Construir Rio4 OBD APK** (también se ejecuta al hacer push a `main`).
3. En la ejecución terminada, descarga el artefacto `Rio4-OBD-Launcher`; dentro estará `app-debug.apk`.

## Emparejar el ELM327
Empareja primero el adaptador desde Ajustes Bluetooth de Android (PIN habitual `1234` o `0000`, depende del adaptador). Abre el launcher, pulsa **CONECTAR OBD2** y selecciona el adaptador emparejado.

Los valores dependen de que el ELM327 y la ECU respondan correctamente a los PIDs estándar. El voltaje ATRV es el voltaje que informa el adaptador, no una lectura independiente de un sensor de batería.
