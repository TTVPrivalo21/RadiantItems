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
└─ .secret             clave HMAC con la que se firman los ítems (no la borres)
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
restrictions: { can-craft: false, can-smelt: false, can-anvil: false, can-drop: true, keep-on-death: false, soulbound: false }
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
`PROJECTILE_HIT`, `SHOOT_BOW`, `BLOCK_BREAK`, `BLOCK_PLACE`, `CONSUME`, `DROP_ITEM`, `SWAP_HANDS`,
`EQUIP`, `UNEQUIP`, `HOLD_LOOP`, y con ranura (`slot: MAIN_HAND|OFF_HAND|HAND|ARMOR|ANY`):
`SNEAK` (al agacharse), `JUMP` (al saltar) y `DAMAGED` (al recibir daño; `@target` = atacante).

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

## Compilar y validar

```bash
./build-local.sh     # Maven si está instalado; si no, javac --release 21 contra ~/.m2 + jar
./validate-yaml.sh   # validación estricta fuera del servidor (tras compilar)
```

`validate-yaml.sh` carga todos los YAML, comprueba IDs únicos (= nombre de archivo), claves
desconocidas, materiales, `ri:id`, partículas, efectos, proyectiles, entidades, fuegos artificiales,
sub-acciones de `LOOT`, mensajes de `config.yml`, el round-trip del editor y que el pack sea determinista.
