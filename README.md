# Buscaminas MC

Manual de uso para operadores (`OP`) del mod de Buscaminas en Fabric `1.21.11`.

Este documento describe el comportamiento actual del mod en el repositorio. Si algo cambia en código, el manual debe actualizarse.

## Resumen

El mod convierte Minecraft en un buscaminas 3D por equipos.

- Los jugadores interactúan con tableros físicos en el mundo.
- Las minas pueden activarse al revelarlas o al pisarlas.
- Hay objetos especiales: `Interaccion`, `Desactivador de minas`, `Totem de inmortalidad` y `Bandera`.
- Las rondas tienen soporte para selectores de Minecraft, equipos, dificultad, tiempo de ronda, respawn y progresión por tableros.
- Al iniciar una ronda se crea una sesión de partida que controla:
  - introducción de `2 minutos`
  - progresión de tableros
  - puntos por equipo
  - cambio automático a espectador cuando un equipo termina o es eliminado

## Qué Necesita Un OP Antes De Jugar

Antes de usar `iniciar_ronda`, prepara lo siguiente:

1. Crear los tableros.
2. Decidir si mantendrás equipos existentes o si el mod los repartirá aleatoriamente.
3. Configurar la ronda.
4. Iniciarla con el selector correcto.

## Cómo Funciona El Minijuego

### Objetivo

Completar un tablero significa:

- todas las minas tienen bandera encima
- no existe ninguna bandera sobre una casilla que no sea mina

### Objetos Del Juego

`Interaccion`
- Revela una casilla.
- Solo funciona a rango corto: `1 bloque` real de interacción.

`Desactivador de minas`
- Si la casilla es mina, abre un minijuego de desactivación.
- Si la casilla no es mina, revela la casilla.
- Tiene cooldown.

`Totem de inmortalidad`
- Evita una muerte por explosión.
- Cuando se activa entra en cooldown.

`Bandera`
- Se recoge de la caja de banderas del tablero.
- Si un jugador carga una bandera, no puede usar otros objetos del juego hasta soltarla o colocarla.
- Colocar bandera no desactiva la mina; solo la marca.

### Minas

- Una mina puede activarse al ser revelada.
- Una mina también puede activarse al ser pisada.
- Si no fue desactivada, explota y puede seguir siendo peligrosa después.
- Una mina desactivada sí queda segura.

### Respawn Y Eliminación

- Si un jugador muere, entra en espera de respawn según la configuración activa.
- Si todos los integrantes de un equipo están muertos al mismo tiempo, el equipo queda eliminado.
- Un equipo eliminado pasa a modo espectador y ve el POV de otros jugadores que sigan en partida.

### Puntos

Los equipos ganan puntos por minas correctamente descubiertas y por tableros completados.

### Introducción De Ronda

Actualmente el mod hace una introducción in-game de `2 minutos` con mensajes de guía y bloqueo temporal del gameplay.

Importante:
- hoy no hay una cinemática de video separada
- hoy no hay una pantalla de tutorial independiente de 2 minutos
- lo implementado es una fase de introducción dentro de la sesión

## Dificultades Clásicas

`facil`
- tablero `10x10`
- `10` minas
- retardo de mina `12 ticks`
- respawn `5s`
- minijuego de desactivación `14s`
- cooldown desactivador `60 ticks`
- cooldown tótem `30s`
- tiempo de ronda `20 min`

`medio`
- tablero `18x18`
- `40` minas
- retardo de mina `8 ticks`
- respawn `8s`
- minijuego de desactivación `10s`
- cooldown desactivador `80 ticks`
- cooldown tótem `45s`
- tiempo de ronda `20 min`

`dificil`
- tablero `24x24`
- `99` minas
- retardo de mina `4 ticks`
- respawn `12s`
- minijuego de desactivación `7s`
- cooldown desactivador `100 ticks`
- cooldown tótem `60s`
- tiempo de ronda `20 min`

## Cómo Funciona La Sesión De Partida

Al ejecutar `iniciar_ronda`, el mod crea una sesión.

### Si existen suficientes tableros clásicos

Si hay suficientes tableros de:

- `facil`
- `medio`
- `dificil`

y existe al menos `1 tablero por equipo` en cada dificultad, la sesión corre como campaña:

`facil -> medio -> dificil`

### Si no existe la campaña completa

Si no hay suficientes tableros de las tres dificultades, la sesión intenta correr una sola etapa usando la dificultad configurada actualmente.

Ejemplo:
- si la ronda está en `medio`
- y hay suficientes tableros `medio` para todos los equipos
- pero no hay suficientes `facil` o `dificil`

entonces la sesión se juega solo en `medio`.

### Si no hay suficientes tableros

La ronda no inicia.

## Reglas Importantes Para OPs

- Todos los comandos del mod requieren permisos de `gamemaster`/`OP`.
- Los selectores usan sintaxis estándar de Minecraft.
- `team=` usa el nombre real del equipo del scoreboard.
- Si inicias una ronda con `modo_equipos=aleatorios`, el mod puede redistribuir a los jugadores en nuevos equipos aunque el selector inicial haya filtrado por un team previo.
- `detener_ronda` y `terminar_ronda` restauran:
  - gamemode
  - cámara
  - items del mod
  - efectos
  - cooldowns del mod
  - estado de tableros

## Comandos

### 1. Crear Tableros

#### `/buscaminas crear_tablero <ancho> <alto> <minas> [origen]`

Crea un tablero manual.

Parámetros:
- `ancho`: tamaño X del tablero
- `alto`: tamaño Z del tablero
- `minas`: cantidad pedida de minas
- `origen`: posición opcional; si no se pone, usa la posición del ejecutor

Ejemplos:

```mcfunction
/buscaminas crear_tablero 10 10 10
/buscaminas crear_tablero 18 18 40 100 64 100
```

#### `/buscaminas crear_tablero_clasico facil|medio|dificil [origen]`

Crea un tablero clásico ya configurado.

Ejemplos:

```mcfunction
/buscaminas crear_tablero_clasico facil
/buscaminas crear_tablero_clasico medio 120 64 90
/buscaminas crear_tablero_clasico dificil
```

### 2. Borrar Tableros

#### `/buscaminas borrar_tablero <id>`

Borra un tablero por ID y restaura los bloques originales del mundo.

Ejemplo:

```mcfunction
/buscaminas borrar_tablero 3
```

### 3. Dar Objetos

#### `/buscaminas dar_objetos [selector]`

Entrega a los jugadores:

- `Interaccion`
- `Desactivador de minas`
- `Totem de inmortalidad`

Si no se indica selector, se entregan al jugador que ejecuta el comando.

Ejemplos:

```mcfunction
/buscaminas dar_objetos
/buscaminas dar_objetos @a[team=2]
```

### 4. Crear Equipos Aleatorios

#### `/buscaminas equipos_aleatorios [selector] [tamano_equipo]`

Genera equipos usando el scoreboard.

Parámetros:
- `selector`: jugadores a repartir
- `tamano_equipo`: tamaño máximo de cada equipo

Ejemplos:

```mcfunction
/buscaminas equipos_aleatorios
/buscaminas equipos_aleatorios @a[gamemode=!spectator] 5
```

### 5. Configurar La Ronda

#### `/buscaminas configurar_ronda dificultad facil|medio|dificil`

Guarda una configuración rápida basada en una dificultad clásica.

Ejemplos:

```mcfunction
/buscaminas configurar_ronda dificultad facil
/buscaminas configurar_ronda dificultad medio
```

#### `/buscaminas configurar_ronda personalizada <modo_equipos> <tamano_equipo> <dar_objetos> <retardo_mina_ticks> <respawn_segundos> <tiempo_desactivacion_segundos> <cooldown_desactivador_ticks> <cooldown_totem_segundos>`

Guarda una configuración personalizada.

Parámetros:
- `modo_equipos`: `mantener` o `aleatorios`
- `tamano_equipo`: máximo de jugadores por equipo si usas `aleatorios`
- `dar_objetos`: `true` o `false`
- `retardo_mina_ticks`: cuántos ticks tarda una mina pisada en dispararse
- `respawn_segundos`: espera antes de volver a jugar
- `tiempo_desactivacion_segundos`: duración del minijuego de desactivación
- `cooldown_desactivador_ticks`: cooldown del desactivador
- `cooldown_totem_segundos`: cooldown del tótem

Ejemplo:

```mcfunction
/buscaminas configurar_ronda personalizada aleatorios 5 true 8 8 10 80 45
```

Eso significa:
- equipos aleatorios
- equipos de hasta 5
- sí entregar objetos
- minas con retardo de 8 ticks
- respawn de 8 segundos
- minijuego de desactivación de 10 segundos
- desactivador con cooldown de 80 ticks
- tótem con cooldown de 45 segundos

### 6. Iniciar La Ronda

#### `/buscaminas iniciar_ronda`

Inicia la ronda usando:
- la configuración guardada actual
- todos los jugadores que no estén en `creative` ni en `spectator`

Ejemplo:

```mcfunction
/buscaminas iniciar_ronda
```

#### `/buscaminas iniciar_ronda <selector>`

Inicia la ronda para un grupo concreto.

Ejemplos:

```mcfunction
/buscaminas iniciar_ronda @a[gamemode=!creative]
/buscaminas iniciar_ronda @a[gamemode=!creative,team=2]
```

#### `/buscaminas iniciar_ronda <selector> dificultad facil|medio|dificil`

Inicia usando el selector indicado y fuerza la dificultad.

Ejemplo:

```mcfunction
/buscaminas iniciar_ronda @a[gamemode=!spectator] dificultad medio
```

#### `/buscaminas iniciar_ronda <selector> personalizada ...`

Inicia la ronda con una configuración personalizada escrita en ese mismo momento.

Ejemplo:

```mcfunction
/buscaminas iniciar_ronda @a[gamemode=!creative] personalizada aleatorios 5 true 8 8 10 80 45
```

### 7. Terminar O Detener La Ronda

#### `/buscaminas detener_ronda`

Detiene la ronda y restaura estado de jugadores y tableros.

#### `/buscaminas terminar_ronda`

Termina la ronda y restaura estado de jugadores y tableros.

En la práctica actual ambos restauran lo mismo; la diferencia es semántica para el host.

### 8. Probar La Explosión

#### `/buscaminas test_explosion_animacion [ticks]`

Genera la animación visual de explosión frente al jugador.

Ejemplos:

```mcfunction
/buscaminas test_explosion_animacion
/buscaminas test_explosion_animacion 20
```

## Selectores Útiles

Algunos ejemplos válidos:

```mcfunction
@a[gamemode=!creative]
@a[gamemode=!spectator]
@a[team=2]
@a[team=ms_team_1]
@a[gamemode=!creative,team=2]
```

Notas:
- `team=2` solo funciona si el nombre real del team en scoreboard es `2`
- si el team se llama `ms_team_1`, entonces debes usar `team=ms_team_1`

## Flujo Recomendado Para Una Partida

### Opción A: campaña completa por equipos

Si quieres campaña `facil -> medio -> dificil`, crea al menos `1 tablero por equipo` para cada dificultad.

Ejemplo para `2 equipos`:

```mcfunction
/buscaminas crear_tablero_clasico facil 0 64 0
/buscaminas crear_tablero_clasico facil 30 64 0
/buscaminas crear_tablero_clasico medio 0 64 40
/buscaminas crear_tablero_clasico medio 40 64 40
/buscaminas crear_tablero_clasico dificil 0 64 90
/buscaminas crear_tablero_clasico dificil 50 64 90
```

Luego:

```mcfunction
/buscaminas equipos_aleatorios @a[gamemode=!spectator] 5
/buscaminas configurar_ronda dificultad facil
/buscaminas iniciar_ronda @a[gamemode=!spectator]
```

### Opción B: una sola etapa en una dificultad

Si solo quieres jugar `medio`, crea suficientes tableros `medio` para todos los equipos.

Ejemplo:

```mcfunction
/buscaminas crear_tablero_clasico medio
/buscaminas crear_tablero_clasico medio 40 64 0
/buscaminas configurar_ronda dificultad medio
/buscaminas iniciar_ronda @a[gamemode=!spectator]
```

## Qué Hace La Ronda Al Iniciar

Cuando una ronda inicia correctamente:

1. Selecciona participantes.
2. Guarda el estado original de esos jugadores.
3. Si corresponde, crea equipos aleatorios.
4. Entrega objetos si la configuración lo indica.
5. Busca tableros válidos para la sesión.
6. Teleporta equipos a su tablero correspondiente.
7. Inicia la introducción de 2 minutos.
8. Abre la partida real al terminar la introducción.

## Qué Hace La Ronda Al Terminar

Cuando la ronda termina por comando, por tiempo o porque ya no quedan equipos activos:

- restaura tableros
- restaura gamemode de participantes
- restaura cámara
- limpia cooldowns del mod
- limpia items del mod y repone los que el jugador tenía antes
- limpia efectos temporales del mod
- elimina equipos temporales creados por la ronda

## Limitaciones Actuales

Esto es importante para hosts:

- La introducción actual es por mensajes, no una cinemática de video.
- No existe todavía un comando dedicado de estado de ronda para ver scoreboard detallado desde chat.
- La sesión depende de que existan suficientes tableros clásicos en el mundo para repartir por equipo.
- Si faltan tableros suficientes para la configuración actual, `iniciar_ronda` fallará.

## Ejemplos Rápidos

### Iniciar con un team concreto

```mcfunction
/buscaminas iniciar_ronda @a[gamemode=!creative,team=2]
```

### Iniciar para todos menos espectadores y forzar medio

```mcfunction
/buscaminas iniciar_ronda @a[gamemode=!spectator] dificultad medio
```

### Guardar una ronda personalizada

```mcfunction
/buscaminas configurar_ronda personalizada aleatorios 5 true 8 8 10 80 45
```

## Recomendación De Host

Antes de una grabación o evento:

1. Crea todos los tableros.
2. Prueba la explosión con `test_explosion_animacion`.
3. Verifica los nombres reales de los teams del scoreboard.
4. Haz una ronda corta de prueba con 2 jugadores.
5. Solo después inicia la ronda real.
