<p align="center">
  <img src="src/main/resources/branding/radiantitems.png" width="320" alt="Radiantitems logo">
</p>

# RadiantItems

Motor de **ítems personalizados ejecutables** para **Paper 1.21.4 → 26.x** (Java 21):
activadores, condiciones, cooldowns, usos/cargas, variables por ítem en el PDC, permisos por ítem,
editor visual in-game por GUI y un **catálogo de 100 ítems** listos para jugar.

**Todo funciona con ítems vanilla, sin resource pack.** El pack de texturas es opcional y
secundario (`resource-pack.enabled: false` por defecto).

- Paquete: `dev.privalo.items` · Licencia: MIT
- Dependencias opcionales: **Vault** (costes en dinero) y **PlaceholderAPI** (placeholders externos), ambas por reflexión.

## Instalación

1. Compila con `./build-local.sh` (o `mvn package`) y copia `target/RadiantItems-1.0.0.jar` a `plugins/`.
2. Arranca el servidor. Se crea:

```
plugins/RadiantItems/
├─ config.yml          ajustes, permisos, pack opcional y mensajes
├─ items.yml           ítems alternativos bajo "items:" (ejemplo: starter_wand)
├─ items/              catálogo de 100 ítems en 8 carpetas (se leen TODAS las subcarpetas)
│  ├─ pvp/ tools/ movement/ consumables/
│  └─ lottery/ cosmetics/ fun/ magic_and_utility/
├─ .secret             clave HMAC con la que se firman los ítems (no la borres)
└─ .last-version       última versión ejecutada (aviso de actualización en consola)
```

`items.generate-defaults: true` extrae el catálogo solo si `items/` no contiene ningún `.yml`.
Puedes crear tus propias carpetas (`items/pvp/espadas/…`, `items/mis_items/…`) a cualquier profundidad
y sin límite de ítems; las carpetas que empiezan por `.` (como `.trash`) se ignoran.

## Modo sin texturas (por defecto) y resource pack opcional

```yaml
resource-pack:
  enabled: false   # sin servidor HTTP, sin envío de packs; "texture:" se ignora
```

El aspecto de cada ítem se define con vanilla: material, nombre/lore MiniMessage, brillo,
encantamientos ocultos, atributos, color de cuero/pociones (`color:`) y cabezas base64 (`skull-texture:`).

Con `resource-pack.enabled: true` el plugin genera el pack a partir de `textures/items/<nombre>.png`
(iconos procedurales para los ejemplos que traen `texture:`), lo sirve en el puerto `http.port` (8166)
con SHA-1 determinista y asigna `item_model` a los ítems con `texture:`. Al activarlo o desactivarlo,
`/ri reload` actualiza solas las copias de los jugadores. Ver `textures/LEEME.txt` (se crea al activarlo).

## Comandos (`/radiantitems`, `/ri`)

| Comando | Permiso | Descripción |
|---|---|---|
| `/ri give <jugador> <item> [cantidad]` | `radiantitems.command.give` | Da ítems |
| `/ri givefolder <jugador> <carpeta> [cantidad]` | `radiantitems.command.givefolder` | Da todos los ítems de una carpeta (y sus subcarpetas) |
| `/ri drop <id> <mundo> <x> <y> <z> [cantidad]` | `radiantitems.command.drop` | Suelta el ítem en el suelo (admite `~` relativo; funciona desde consola) |
| `/ri show [carpeta]` · `/ri browser` | `radiantitems.command.show` | Navegador GUI por carpetas: clic = 1, shift = pila (requiere `give`), clic derecho = editar |
| `/ri create <id> [carpeta]` | `radiantitems.command.create` | Crea en `items/<carpeta>/` y abre el editor |
| `/ri edit [id]` | `radiantitems.command.edit` | Editor GUI (sin id: el de tu mano) |
| `/ri list [carpeta]` | `radiantitems.command.list` | Lista agrupada por carpeta, filtrable |
| `/ri info` | `radiantitems.command.info` | Usos, UUID, dueño, variables del ítem en mano |
| `/ri setuses <n>` | `radiantitems.command.setuses` | Ajusta los usos del ítem en tu mano |
| `/ri pack [send\|rebuild\|status]` | `radiantitems.command.pack` | Resource pack (si está activado) |
| `/ri reload` | `radiantitems.command.reload` | Recarga todo |
| `/ri delete <id>` | `radiantitems.command.delete` | Mueve el YAML a `items/.trash/` |

`radiantitems.admin` (op) incluye todo. Bypass: `radiantitems.bypass.permission` (permisos por ítem,
configurable en `permissions.bypass-item-permission`) y `radiantitems.bypass.soulbound`.

## Formato de un ítem

```yaml
id: mi_espada                       # debe coincidir con el nombre del archivo
material: DIAMOND_SWORD
name: "<gradient:#FB923C:#F97316><b>Mi Espada</b></gradient>"
lore:
  - "<white>Usos: <#F97316>%uses%</#F97316>/%max_uses% · Recarga: %cooldown%"
permission: "radiantitems.use.mi_espada"        # vacío = libre para todos
no-permission-message: "<#F97316>No eres digno."
glow: true
enchantments: { sharpness: 5 }
flags: [HIDE_ATTRIBUTES, HIDE_ENCHANTS]
unbreakable: true
attributes: ["ATTACK_DAMAGE: 10 mainhand", "MOVEMENT_SPEED: 0.1 offhand MULTIPLY"]
color: "#F97316"                    # cuero / pociones
skull-texture: "eyJ0ZXh0dXJlcyI6..." # PLAYER_HEAD
max-stack: 16
max-uses: 20
break-on-empty: true
recharge: { material: AMETHYST_SHARD, uses: 5 }
variables: { kills: 0 }
restrictions: { no-craft: true, no-anvil: true, no-store: false, keep-on-death: false, soulbound: false }
texture: mi_espada                  # OPCIONAL: solo con resource-pack.enabled: true
activators:
  golpe:
    type: HIT_ENTITY
    cooldown: 3
    chance: 50
    actions: ["@target EFFECT: SLOWNESS 60 1"]
```

### Activadores

`RIGHT_CLICK`, `LEFT_CLICK`, `SHIFT_RIGHT_CLICK`, `SHIFT_LEFT_CLICK`, `HIT_ENTITY`, `KILL_ENTITY`,
`PROJECTILE_HIT`, `SHOOT_BOW`, `BLOCK_BREAK`, `BLOCK_PLACE`, `CONSUME`, `SWAP_HANDS`,
`DROP_ITEM` (al soltarlo con Q; `cancel-event: false` deja que caiga), `PICKUP_ITEM` (al recogerlo del suelo;
`cancel-event: true` impide recogerlo) y, con ranura (`slot: MAIN_HAND|OFF_HAND|HAND|ARMOR|ANY`, que mira manos y armadura):

| Activador | Cuándo |
|---|---|
| `EQUIP` / `UNEQUIP` | al ponerlo / quitarlo de la ranura |
| `HOLD_LOOP` | cada `interval` ticks mientras está en la ranura |
| `SNEAK` / `JUMP` | al agacharse / saltar |
| `DAMAGED` | al recibir daño de una entidad (`@target` = atacante) |
| `DEATH` | al morir llevándolo en la ranura (`@target` = asesino, si lo hay) |

### Restricciones

```yaml
restrictions:
  soulbound: true      # se vincula al primer usuario; otros no pueden usarlo ni recogerlo; se conserva al morir
  keep-on-death: true  # vuelve al inventario al reaparecer
  no-drop: true        # = can-drop: false  → no se puede soltar con Q
  no-craft: true       # = can-craft: false → no sirve como ingrediente (mesa y herrería)
  no-anvil: true       # = can-anvil: false → no se puede usar en yunques
  no-smelt: true       # = can-smelt: false → ni fundir ni como combustible
  no-store: true       # = can-store: false → no entra en cofres, barriles, shulkers, cofre de ender, tolvas...
```

Se aceptan las dos formas (`no-X` o `can-X`); el editor guarda `can-X`. Por defecto: `can-craft`,
`can-smelt` y `can-anvil` desactivados; `can-drop` y `can-store` activados. Las tolvas nunca recogen
del suelo ítems `no-store` ni `soulbound`.

### Condiciones

Prefijo `!` para negar, mensaje propio con `"CONDICIÓN | mensaje"`.
`PERMISSION`, `WORLD`, `MIN/MAX_HEALTH`, `MIN/MAX_FOOD`, `MIN/MAX_LEVEL`, `SNEAKING`, `SPRINTING`, `FLYING`,
`GLIDING`, `ON_GROUND`, `IN_WATER`, `BLOCKING`, `GAMEMODE`, `TIME`, `PLACEHOLDER`, `VARIABLE`, `USES`,
`TARGET_TYPE`, `BLOCK_TYPE: *_ORE,*_LOG,CHEST` y costes `MONEY`, `ITEM: DIAMOND 2 | ri:id 1`, `XP_COST`.

### Acciones

Selector opcional: `@self`, `@target`, `@nearby:R`, `@block`, `@online`. Los números admiten expresiones.

| Grupo | Acciones |
|---|---|
| Flujo | `DELAY: 20`, `LOOT: 60=GIVE_ITEM: IRON_INGOT 8 && MESSAGE: … ;; 40=…` (premio ponderado, `%chance%`) |
| Mensajes | `MESSAGE`, `BROADCAST`, `ACTIONBAR`, `TITLE: a \| b \| 10 40 10`, `BOSSBAR`, `CONSOLE`, `PLAYER` |
| Efectos visuales | `SOUND`, `PARTICLE`, `PARTICLE_CIRCLE`, `PARTICLE_LINE`, `PARTICLE_SPIRAL`, `FIREWORK: BALL #FB923C 1 [instant]` |
| Jugador | `EFFECT`, `REMOVE_EFFECT`, `HEAL`, `FEED`, `DAMAGE`, `IGNITE`, `EXTINGUISH`, `GIVE_XP`, `TAKE_XP`, `MONEY`, `PLAYER_TIME`, `PLAYER_WEATHER` |
| Movimiento | `VELOCITY`, `GRAPPLE`, `BLINK`, `NO_FALL`, `TELEPORT` |
| Combate | `PROJECTILE` (incl. `ENDER_PEARL`, `WIND_CHARGE`), `@target HOMING: 16 60`, `LIGHTNING`, `EXPLOSION` (segura) |
| Bloques | `MINE_AREA: 1 0`, `VEIN_MINE: 48`, `HARVEST: 3`, `BUILD_LINE: 8` (respetan protecciones) |
| Utilidad | `MAGNET: 8`, `DETECT: ores 10 \| entities 30`, `REPAIR: 100 offhand\|armor\|all`, `OPEN: workbench\|enderchest\|anvil…`, `WEAR`, `GIVE_ITEM`, `TAKE_ITEM` |
| Estado del ítem | `VARIABLE: kills + 1`, `USES: +5` |
| Diversión | `LAUNCH_ENTITY: SHEEP 1.5 100` (desaparece, sin drops; TNT sin romper bloques ni dañar jugadores) |

## Catálogo por defecto (100 ítems)

| Carpeta | Ítems |
|---|---|
| `pvp/` (15) | espada vampírica, hoja del trueno (con permiso), filo glacial, colmillo venenoso, espada del vendaval, espada de brasas, hacha del señor de la guerra, hacha del verdugo, arco explosivo, arco rastreador, ballesta de ráfaga, escudo espejo, escudo de espinas, daga de las sombras, kris del asesino |
| `tools/` (12) | pico explosivo 3x3, taladro de vetas, hacha talabosques, pala excavadora, pala de terraformar 5x5 (con permiso), perforadora de túneles, azada de cosecha, hoz del segador, caña magnética, varita de construcción, varita de reparación, linterna del minero |
| `movement/` (12) | gancho impulsor, botas de doble salto, grebas resorte, pluma de planeo, perla eterna, bastón de blink, bastón del viajero, cohete infinito, guantelete del viento, sandalias del sprint, aletas de marea, brújula del hogar |
| `consumables/` (12) | elixires del fénix/vida/furia/sombra, manzana mística, ración infinita, estofado del viajero, galleta dorada, pergamino de retorno, pergamino del spawn, tótem guardián, tónico de miel |
| `lottery/` (12) | llaves de caja común/épica, boleto de lotería, rasca y gana, bolsa de tesoro, saco del minero, ruleta de monedas, ruleta de ítems, cofre sorpresa, moneda de la suerte, huevo misterioso, galleta de la fortuna |
| `cosmetics/` (12) | corona real, sombrero de calabaza, gorro de fiesta, máscara de dragón, cabeza de leyenda (base64), máscara de creeper, botas de estela ígnea, peto del ocaso, pantalones ámbar, anillo de la aureola, varita de destellos, varita de la aurora |
| `fun/` (13) | cañón de TNT, lanzador de fuegos, varita lanzaovejas, lanzagallinas, ametralladora de nieve, pelota saltarina, martillo celeste, trompeta de fiesta, lanzaconfeti, varita de chispazos, varita de burbujas, bola de disco, cerdo cohete |
| `magic_and_utility/` (12) | amuletos del viento/fuerza/búho, báculo de tormentas, imán de ítems, mesa portátil, bolsa del End, yunque de bolsillo, detector de minerales, radar de entidades, reloj del cielo, sello del alma |

## Editor visual

`/ri edit <id>`: material (clic con un ítem en el cursor), nombre, lore, brillo, color, cabeza base64,
encantamientos, flags, atributos, usos, recarga, variables, restricciones, **permiso del ítem**,
**carpeta/categoría** (mueve el archivo), textura opcional y todos los activadores (condiciones,
acciones y mensajes con validación). Cada cambio se guarda al instante en su archivo original.

## Comprobador de actualizaciones

Al arrancar (y con `/ri reload`) RadiantItems consulta en segundo plano si hay una versión más nueva.
Usa `java.net.http.HttpClient` fuera del hilo principal, con timeout de 5 s y cabecera `User-Agent`, así
que nunca afecta al TPS. Si no hay conexión, el repo es privado, todavía no tiene releases (HTTP 404) o la
respuesta no es válida, se ignora sin mensajes ni stacktraces (solo queda una línea en nivel `FINE`).

```yaml
update-checker:
  enabled: true
  source: "github"                        # "github" o "spigot"
  github-repo: "TTVPrivalo21/RadiantItems" # usuario/repositorio de GitHub
  spigot-resource-id: 0                   # ID del recurso en SpigotMC (0 = sin usar)
  notify-admins-on-join: true             # aviso clicable a quien tenga radiantitems.admin
```

**Con GitHub** (`source: "github"`): pon `usuario/repositorio`. Se consulta
`https://api.github.com/repos/<repo>/releases/latest` y se leen `tag_name` (por ejemplo `v1.2.0`) y `html_url`.
Para que avise, publica una *Release* (no basta con un tag ni con un pre-release o borrador) cuyo tag sea mayor
que la versión instalada. El prefijo `v` es opcional.

**Con SpigotMC** (`source: "spigot"`): pon el ID numérico del recurso, que es el número final de su URL.
Por ejemplo, en `https://www.spigotmc.org/resources/radiantitems.123456/` el ID es `123456`.
Se consulta `https://api.spiget.org/v2/resources/<id>/versions/latest` (campo `name`) y el enlace de descarga
es `https://www.spigotmc.org/resources/<id>/`. La página de autor
(`spigotmc.org/resources/authors/...`) no sirve: hace falta el ID del recurso.

Las versiones se comparan como SemVer: `v1.2.0` → `1.2.0`, `1.10.0 > 1.9.9`, `1.2.0 > 1.2.0-beta.1`
y se ignoran los metadatos `+build`. Si la remota es mayor, la consola muestra:

```
============================================================
 ¡Hay una nueva versión de RadiantItems disponible! (GitHub)
   Versión instalada : 1.1.0
   Nueva versión     : 1.2.0
   Descarga          : https://github.com/TTVPrivalo21/RadiantItems/releases/tag/v1.2.0
============================================================
```

Los admins que entren reciben el mensaje `messages.update-available`, con un `[Descargar]` clicable
en la paleta `#FB923C` / `#F97316`.

## Control de versión

La versión vive solo en `pom.xml` (`1.1.0`); `plugin.yml` la recibe con `${project.version}` y
`build-local.sh` la lee dinámicamente para generar `target/RadiantItems-<versión>.jar` (borra los jars de
otras versiones; `VERSION=x.y.z ./build-local.sh` la sobrescribe).

Al arrancar, el plugin compara la versión del jar con `plugins/RadiantItems/.last-version`. Si cambia,
imprime en consola un aviso del tipo:

```
============================================================
 RadiantItems ACTUALIZACIÓN detectada: 1.0.0 -> 1.1.0
 Revisa el README (novedades). Las claves nuevas de config.yml
 usan su valor por defecto hasta que las añadas a tu config.yml.
============================================================
```

(o `DOWNGRADE` si la versión baja) y solo después reescribe `.last-version`.

### Novedades 1.1.0

- Comandos `/ri givefolder`, `/ri drop` y navegador `/ri show` (`/ri browser`).
- Activadores `PICKUP_ITEM` y `DEATH`; restricción `no-store` y alias `no-*` para todas las restricciones.
- Los ítems soulbound no pueden recogerlos otros jugadores ni las tolvas.
- Comprobador de actualizaciones asíncrono (GitHub Releases / SpigotMC) con aviso clicable a admins.
- Mensajes nuevos en `config.yml` → `messages`: `update-available`, `given-folder`, `received-folder`, `unknown-folder`,
  `unknown-world`, `dropped`, `store-blocked`, `pickup-blocked` (si conservas un config.yml antiguo se usan los textos por defecto del jar).

## Compilar y validar

```bash
./build-local.sh     # Maven si está instalado; si no, javac --release 21 contra ~/.m2 + jar
./validate-yaml.sh   # validación estricta fuera del servidor (tras compilar)
```

`validate-yaml.sh` carga todos los YAML, comprueba IDs únicos (= nombre de archivo), claves
desconocidas, materiales, `ri:id`, partículas, efectos, proyectiles, entidades, fuegos artificiales,
sub-acciones de `LOOT`, mensajes de `config.yml`, el round-trip del editor y que el pack sea determinista.
