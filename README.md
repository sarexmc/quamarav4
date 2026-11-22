# QUMARA — jefe cinemático (Minecraft 1.21.1 · Fabric)

**Cambios de esta versión:** modelo orgánico profesional (el OBJ `carnivorous_plant`) con esqueleto de 29 huesos y **un solo brazo**,
~1.8× más grande, **19 ataques** (6 nuevos: garra que agarra/lanza/estrella y 4 de veneno) y **veneno acumulable que sí mata**.

Versiones: Minecraft 1.21.1, Fabric Loader 0.16.14, Fabric API 0.116.10+1.21.1, Fabric Loom 1.9.2, Yarn 1.21.1+build.3, Java 21, Gradle 8.11+.
**Ya no necesita GeckoLib** (solo admite cubos): el modelo se dibuja con un renderer propio (`client/render/QumaraRenderer`).

## Compilar
```
gradle wrapper --gradle-version 8.11.1
./gradlew build            # build/libs/qumara-1.0.0.jar
./gradlew runClient
```
`/summon qumara:qumara ~ ~ ~` o el huevo generador. Solo requiere Fabric API.

## Modelo y animaciones (tools/)
`python3 tools/build_model.py ruta/carnivorous_plant.obj [celdas=90] [textura=2048]` (numpy, scipy, pillow) genera:
- `assets/qumara/model/qumara.mesh` — malla decimada (47k vértices / 32k cuads) con 4 pesos por vértice.
- `assets/qumara/model/qumara.qani` — esqueleto + 39 animaciones a 20 fps + trayectoria de la mano.
- `textures/entity/qumara.png` y `qumara_fury.png` (la emisión se hornea en el albedo; la furia es un tinte rojo).
Los archivos generados ya están incluidos; solo hay que ejecutarlo si cambias el modelo o las animaciones.

| archivo | qué hace |
|---|---|
| `qumara_rig.py` | huesos (pivotes), FK |
| `skin.py` | reglas de skinning (color + posición) |
| `anims.py` | **todas las animaciones** (edítalas aquí); el brazo usa IK hacia objetivos en bloques |
| `animlib.py`, `mesh_tools.py` | IK, cuaterniones, exportadores |

Huesos: `raiz, raices_o/e/n/s, tronco, cuello, cabeza, mandibula, lengua_1..5, petalo_sup/izq/der/fizq/fder, hoja_1/2, flor_1/2,
brazo, antebrazo, mano, dedo_1, dedo_2, pulgar`. Convención: +Z frente, −X lado del brazo (derecha del jefe).

**Agarres sincronizados:** `QumaraEntity.handPos(anim, clock)` devuelve la posición real de la palma en esa animación (sale de la
trayectoria exportada), por eso la víctima agarrada se mueve exactamente con la garra que ve el jugador.

## Tamaño
`combat/Scale.java` → `RENDER` (1.3 = ~24 bloques de alto, el brazo alcanza ~17). Es el único valor a tocar: hitbox, alcances, zonas y
cámara se recalculan. Si lo cambias, cambia también `RENDER` en `tools/anims.py` y vuelve a ejecutar `build_model.py`
(los objetivos del brazo están en bloques).

## Ataques (19) y fases
Fase 0 normal · 1 FURIA (<50 % vida, x1.25) · 2 IRA (<25 %, x1.45).
Antiguos (13): raíces atrapadoras, lengua depredadora (+agarre encadenado), brazo aplastante, barrido, jardín maldito, enredadera,
martillo, grieta de raíces, ciclón, devorar, lluvia de espinas, abanico de lengua, rugido. Con un solo brazo, los ataques «del otro
lado» cruzan el brazo por delante del cuerpo.

**Nuevos:**
- **Garra mortal — lanzar** (`GripAttack`): círculo rojo 9 bloques delante; atrapa hasta 1+fase jugadores, los sube, 3 estrujones
  (5 + veneno), y los lanza ~19 bloques; al caer: 16 de daño, veneno y charco tóxico.
- **Garra mortal — estrellar** (furia+): los golpea dos veces contra el suelo (20 + onda de choque cada vez) y los arroja de lado.
- **Escupitajo venenoso**: 3/4/5 gotas en abanico (círculo rojo por gota) → 10 de daño, veneno y charco.
- **Nube tóxica**: golpea el suelo y expulsa una nube que crece hasta 6.5·K bloques y dura ~5 s (anillo rojo = alcance máximo).
- **Aliento tóxico**: barre un cono de gas de 80° (zona roja del sector); 3 de daño + veneno cada 4 ticks dentro del cono.
- **Lluvia de veneno**: 4 andanadas de gotas con charcos; escala con el número de jugadores.

**Veneno** (`VenomManager`): daño real cada segundo (ignora armadura), acumulable hasta nivel 3 (1.5 de daño/s por nivel), dura 4–6 s
y se renueva con cada contacto. **Se cura con leche** (quita el efecto Veneno), así que siempre hay defensa.

## Multitud, daño y sonidos
Sin cambios: vida base 3000, daño dividido por participantes^0.75, tope 2 % por golpe, daño que ignora armadura (`hurtPlayer`),
densidad de partículas adaptativa. Sonidos: los 13 eventos de `ModSounds` apuntan a sonidos vanilla en `sounds.json`.

## Estado de la verificación
El pipeline (malla, pesos, IK, animaciones, cargador y skinning Java) está probado aquí y el skinning Java coincide con el de
Python. **El código que usa clases de Minecraft (renderer, ataques nuevos) no se pudo compilar ni ejecutar en este entorno** (sin
acceso a las dependencias de Gradle): puede que haya que corregir algún nombre de API de Yarn al compilar, y habrá que afinar en
juego tiempos, daños y poses de las animaciones.
