# Generator świata: specyfikacja techniczna dla Claude Code

Nov 25, 2026 · @Wojciech Prusaczyk

## Cel, zakres i zasady nadrzędne

Mod (robocza nazwa **Sky Seas**, id `skyseas`) implementuje generator świata opisany w `docs/design.md` jako rozszerzenie vanillowego generatora NeoForge 1.21.1, działające wyłącznie po stronie serwera. Dokument designowy jest źródłem prawdy dla *co*; ta specyfikacja opisuje *jak*.

**Zasady nadrzędne (w kolejności ważności):**

1. **Wydajność przede wszystkim.** Każda decyzja implementacyjna jest oceniana kosztem generacji chunka. Budżet i zakazy są w sekcji Wydajność.
2. **Determinizm.** Ten sam seed i te same parametry dają identyczne chunki, niezależnie od kolejności generacji i liczby wątków. Żadnego globalnego stanu zależnego od kolejności.
3. **Rozszerzamy vanillę, nie zastępujemy.** Vanillowy `NoiseBasedChunkGenerator`, vanillowe `multi_noise` z presetem overworld, vanillowe jaskinie, aquifery i surface rules. Własny kod tylko tam, gdzie vanilla nie wystarcza.
4. **Tylko serwer.** Żadnych bloków, itemów, encji, pakietów sieciowych ani wpisów w rejestrach, które wymagają moda po stronie klienta. Klient nie potrzebuje moda. Biomy z datapacka wbudowanego w jar są dozwolone, bo serwer wysyła je klientowi jako dane.
5. **Dane ponad kod.** Struktura routera szumu w JSON (datapack wbudowany w jar), parametry w configu, matematyka kształtów w Javie.
6. **Testowalność.** Układ wysp i matematyka kształtów nie zależą od klas Minecrafta, więc da się je testować JUnitem.

**Poza zakresem:** jakiekolwiek własne biomy (regiony dna to formy moda), bloki i itemy; Nether i End; Distant Horizons; zmiany mobów.

## Środowisko i stos technologiczny

Projekt startuje z oficjalnego szablonu [MDK-1.21.1-ModDevGradle](https://github.com/neoforgemdks/MDK-1.21.1-ModDevGradle); wersja NeoForge ma być taka sama jak na serwerze.

| Element | Wybór | Uwagi |
| --- | --- | --- |
| Minecraft | 1.21.1 | Wersja serwera |
| NeoForge | 21.1.x, ta sama co na serwerze (co najmniej 21.1.226) | Taką wymagają już mody z paczki |
| Java | 21 (Temurin) | Wymagana przez 1.21.1 |
| Build | Gradle wrapper z MDK + ModDevGradle | `./gradlew build` daje jar w `build/libs` |
| Mapowania | Parchment dla 1.21.1 | Czytelne nazwy parametrów w kodzie MC |
| Lithostitched | Zależność wymagana (NeoForge 1.21.1) | Już w paczce przez Create Deep Seas; warunki spawnu struktur i modyfikatory |
| Create | Zależność opcjonalna | Tylko dla rudy cynku na wyspach (JSON z warunkiem `neoforge:mod_loaded`) |
| Testy | JUnit 5 + NeoForge GameTest | JUnit dla układu i kształtów, GameTest dla audytu w świecie |
| Profilowanie | Spark, Chunky | Tylko w środowisku testowym, nie zależności moda |
| Repozytorium | Git, gałąź `main` + tag dla wersji użytej do generacji świata | Patrz: Ryzyka (szwy między wersjami) |

**Polecenia:** `./gradlew runServer` (serwer deweloperski), `./gradlew runClient`, `./gradlew test` (JUnit), `./gradlew runGameTestServer` (GameTesty), `./gradlew build` (jar do wdrożenia).

## Architektura

Generator składa się z jednej warstwy planującej (układ wysp, liczony raz na region i cache’owany) oraz pięciu warstw, które wpinają się w vanillowe etapy generacji chunka i tylko odpytują układ.

| Warstwa | Etap chunka | Co robi | Implementacja |
| --- | --- | --- | --- |
| Układ (layout) | wywoływany leniwie z każdego etapu | Plan regionu 1024×1024 bloków: typy wysp, pozycje, wysokości, flagi (cenota, łańcuch, starter) | Czysta Java, cache regionów, bez klas MC |
| Struktury | `structure_starts` | Gdzie wolno postawić strukturę | Vanilla + warunki spawnu Lithostitched; mixiny tylko gdy konieczne |
| Klimat i biomy | `biomes` | Trójwymiarowy wybór vanillowych biomów | Vanillowe `multi_noise` + własne funkcje klimatu w routerze |
| Kształt | `noise` | Gęstość: wyspy, dno, wyspy oceaniczne, jaskinie, szyby; aquifery wypełniają wodę | `noise_settings` w JSON + własne typy `DensityFunction` |
| Powierzchnia | `surface` | Trawa, piasek, gołe skały dna, strefy wulkaniczne | Vanillowe surface rules + własne warunki |
| Carvery | `carvers` | Wyłączone | Jaskinie są w gęstości; carvery usuwa `neoforge:remove_carvers` |
| Dekoracja | `features` | Rudy, odsłonięcia, gniazda diamentów, kaskady, spody wysp, kominy | Biome modifiers NeoForge + własne `Feature` i `PlacementModifier` |

**Wejście do świata:** własny world preset `skyseas:sky_seas` (wymiar overworld z `NoiseBasedChunkGenerator`, ustawieniami `skyseas:sky_seas` i `multi_noise` z presetem `minecraft:overworld`). Na serwerze: `level-type=skyseas:sky_seas` w `server.properties`. Preset jest też widoczny w ekranie tworzenia świata (tag `minecraft:normal`) do testów lokalnych.

**Dostęp do układu z każdej warstwy:** funkcje gęstości trzymają referencję do obiektu `Layout` bezpośrednio; featury, surface rules i struktury pobierają go przez `LayoutAccess.get(RandomState)`, które raz odnajduje własną funkcję w routerze i zapamiętuje wynik (słaba mapa po `RandomState`).

_[Schemat: architektura · układ i etapy generacji chunka — dostępny tylko w wersji online dokumentu]_

Każdy etap odpytuje ten sam obiekt układu; carvery są wyłączone, bo jaskinie powstają w funkcji gęstości.

## Struktura projektu

Pakiet bazowy `pl.skyseas` (do zmiany przy starcie projektu); pakiety `layout` i `shape` nie importują klas Minecrafta, żeby dało się je testować JUnitem.

```text
skyseas/
├─ build.gradle, settings.gradle, gradle.properties   # MC 1.21.1, NeoForge, Parchment
├─ src/main/java/pl/skyseas/
│  ├─ SkySeas.java                 # @Mod: rejestracje DeferredRegister, eventy
│  ├─ config/                      # SkySeasConfig (ModConfigSpec), WorldParams (zamrożone per świat)
│  ├─ layout/                      # BEZ klas MC
│  │  ├─ Layout.java               # fasada: query(x,z), region(rx,rz)
│  │  ├─ LayoutCache.java          # cache regionów + MRU per wątek
│  │  ├─ RegionLayout.java         # tablice prymitywów (struct of arrays) + indeks kubełkowy
│  │  ├─ LayoutGenerator.java      # poziomy: kontynenty → wyspy oceaniczne → klastry → łańcuchy
│  │  ├─ StarterLayout.java        # gwarancje wokół spawnu
│  │  ├─ ZoneField.java            # współczynnik odległości 0..1
│  │  └─ Hash.java                 # SplitMix64 / hash pozycyjny
│  ├─ shape/                       # BEZ klas MC: czysta matematyka gęstości
│  │  ├─ ContinentShape, ArchipelagoShape, HallelujahShape, ChainShape
│  │  ├─ OceanIslandShape, SeabedShape, ShaftShape
│  │  └─ Noise2D.java              # lekki szum (OpenSimplex2S) dla kształtów
│  ├─ density/                     # typy DensityFunction (MAP_CODEC + rejestracja)
│  │  ├─ LayoutRootFunction.java   # trzyma Layout, wyprowadza seed z szumu
│  │  ├─ SkyIslandsFunction, SeabedFunction, RockThicknessFunction
│  │  ├─ CaveMaskFunction, ShaftsFunction, ZoneFactorFunction
│  │  └─ climate/                  # Continents, Erosion, Depth, Temperature, Humidity
│  ├─ surface/                     # własne SurfaceRules.ConditionSource (np. strefy wulkaniczne)
│  ├─ feature/                     # Feature: IslandOre, SeabedOutcrop, DiamondNest, Cascade,
│  │                                #          UndersideDecor, Vent, SkyLake
│  ├─ placement/                   # PlacementModifier: IslandRelativeHeight, ZoneCount, LayerFilter
│  ├─ structure/                   # warunki spawnu (Lithostitched) + StructureRules
│  ├─ mixin/                       # tylko to, czego nie da się zrobić inaczej
│  ├─ command/                     # /skyseas info|locate|map|audit|bench
│  └─ license/                     # LicenseVerifier, ModpackFingerprint (etap ostatni)
├─ src/main/resources/
│  ├─ META-INF/neoforge.mods.toml
│  ├─ skyseas.mixins.json
│  └─ data/
│     ├─ skyseas/worldgen/world_preset/sky_seas.json
│     ├─ skyseas/worldgen/noise_settings/sky_seas.json
│     ├─ skyseas/worldgen/density_function/**.json   # router rozbity na nazwane części
│     ├─ skyseas/worldgen/noise/**.json              # parametry szumów (w tym layout_seed)
│     ├─ skyseas/worldgen/configured_feature/**.json
│     ├─ skyseas/worldgen/placed_feature/**.json
│     ├─ skyseas/neoforge/biome_modifier/**.json     # add_features, remove_carvers
│     ├─ skyseas/lithostitched/worldgen_modifier/**.json  # warunki spawnu struktur
│     └─ minecraft/tags/worldgen/world_preset/normal.json
├─ src/test/java/pl/skyseas/      # JUnit: układ, kształty, determinizm
├─ src/gametest/...               # GameTesty audytu w świecie
└─ tools/license-signer/          # CLI do podpisywania licencji (etap ostatni)
```

## Warstwa układu (layout)

Układ to deterministyczny plan wysp liczony raz na region 1024×1024 bloków, w poziomach, z których każdy zależy wyłącznie od poziomów wcześniejszych; dzięki temu decyzje nie tworzą łańcuchów zależności między regionami i wynik nie zależy od kolejności generacji.

**Regiony i cache**

- Klucz regionu to `long`: `(rx << 32) | (rz & 0xFFFFFFFFL)`.
- `LayoutCache` jest ograniczony (domyślnie 512 regionów) i bezpieczny wątkowo. Każdy region liczy się dokładnie raz (blokada lub future per klucz), a każdy wątek trzyma w `ThreadLocal` ostatnio użyty region, bo większość zapytań trafia w ten sam.
- Region przechowuje wyspy jako tablice prymitywów (struct of arrays) plus dwa indeksy kubełkowe 64×64 bloki (wyspy na niebie i formy oceaniczne): lista wysp, których obrys przecina kubełek, łącznie z wyspami z regionów sąsiednich. Zapytanie o kolumnę to jeden kubełek i zwykle 0–3 wyspy.

**Poziomy (kolejność = priorytet)**

1. **Kontynenty.** Siatka 1700 bloków, jeden kontynent na komórkę. Kontynent zostawia z każdej strony komórki połowę przerwy między pudełkami obrysu (240–600 bloków, losowo dla kontynentu) i jest skalowany tak, żeby wypełnić resztę; luz wzdłuż krótszego boku pudełka staje się przesunięciem. Sylwetki nie wypełniają pudełek, więc zmierzona przerwa brzeg–brzeg do najbliższego sąsiada wynosi 400–800 bloków (runda 1: 96% kontynentów, zakres 431–855). Rozmiar wynika z komórki i przerwy (pudełko ok. 900–1300 bloków); parametr promienia 230–690 z E2 zniknął. Kontynent startowy na 0,0. Każdy kontynent dostaje sylwetkę z katalogu (wydłużona 1:2–1:4, półksiężyc lub podkowa, hak, rozgałęziona, nieregularna z elips); ok. 30% jest złożonych z 2–4 członów połączonych przesmykami albo mostami skalnymi, z różnicą wysokości członów 20–60 bloków.
2. **Formy oceaniczne.** Wyspy oceaniczne (klasy 70/25/5 według designu, gęstość ok. 2× względem E3a, większy udział średnich), góry podwodne, wulkany, iglice morskie, formacje monumentalne i strefy biomów dna. Formy wystające nad wodę są odrzucane pod rzutem kontynentu z marginesem 16 bloków (+ szum nawisów).
3. **Wyspy pojedyncze.** Wyspy-wieże (2–4 na region) oraz małe i średnie wyspy (10–20 na region; ok. połowa jako satelity 100–400 bloków od krawędzi kontynentu, reszta rozsiana). Odrzucane, gdy ich rzut z marginesem 16 nachodzi na ląd w oceanie lub inną wyspę na niebie, albo gdy przekraczają budżet zacienienia.
4. **Klastry: archipelagi i góry Alleluja.** Środek klastra z siatki, wyspy wewnątrz klastra. Wyspa jest odrzucana, gdy jej rzut z marginesem 16 nachodzi na ląd w oceanie lub kontynent, gdy przekracza budżet zacienienia albo gdy wisi nad inną wyspą klastra.
5. **Łańcuchy powrotne.** Z wybranych punktów krawędzi kontynentu ścieżka w dół do ok. Y 70: 5–15 wysp, krok pionowy do 12 bloków, przerwa pozioma do 6, promień wyspy co najmniej 4 (zapas ziemi). Zależą tylko od swojego kontynentu (poziom 1½); to wyspy oceaniczne ustępują łańcuchom, tak jak kontynentom, bo odwrotna kolejność nie byłaby deterministyczna między regionami.
6. **Elementy specjalne.** Cenoty (1–2 na dużej wyspie oceanicznej), błękitne dziury (płycizny), iglice pod spodami wysp z gniazdami diamentów (szansa wg strefy), ujścia kaskad (krawędzie kontynentów).

**Zacienienie (decyzja 2026-11-27).** Limit 30% zniesiony; test raportuje udział morza w cieniu. Twarde jest tylko czyste niebo nad lądami w oceanie.

W obrębie poziomu każda komórka siatki ma najwyżej jednego kandydata, więc nie ma konfliktów wewnątrz poziomu. Decyzje poziomu N czytają wyłącznie gotowe poziomy 1..N−1 z sąsiedztwa 3×3 regionów.

**Deskryptor wyspy**

| Pole | Typ | Znaczenie |
| --- | --- | --- |
| `type` | byte | CONTINENT, ARCHIPELAGO, HALLELUJAH, CHAIN, TOWER, SKY\_ISLAND (mała lub średnia), OCEAN\_ISLAND, SEAMOUNT, VOLCANO, SEA\_SPIRE, SEABED\_FORMATION |
| `cx`, `cz` | int | Środek |
| `radius` | float | Promień obrysu (do indeksu i odrzuceń) |
| `minY`, `maxY` | short | Pionowy zakres bryły (szybkie odrzucanie zapytań) |
| `shapeSeed` | long | Ziarno kształtu |
| `params` | double\[\] (spakowane, zmienna długość przez `paramOffset`) | Parametry kształtu: wysokość bazowa, siła gór, sylwetka, człony, przechylenie, iglice pod spodem |
| `flags` | int | STARTER, HAS\_CENOTE, HAS\_BLUE\_HOLE, MUSHROOM, CLUSTER\_BIOME itp. |
| `zone` | float | Współczynnik strefy w środku |

**Klimat w układzie.** Formy zależne od temperatury wody czytają ją raz, w swoim środku, przez `ClimateProbe` (czysta Java). `LayoutRootFunction` dostaje pole `temperature` podpinane przez ten sam `RandomState`, liczone poza cache chunka. Sonda nie może zależeć od samego układu (np. od biasu rafy), bo region liczony w trakcie liczenia samego siebie zakleszczyłby cache.

**Seed bez mixinów.** `LayoutRootFunction` zawiera `NoiseHolder` z własnymi parametrami szumu `skyseas:layout_seed`. Gdy `RandomState` podpina szumy (`mapAll` z wizytorem), funkcja wylicza 64-bitowe ziarno z hasha próbek podpiętego szumu w stałych punktach. Każdy `RandomState` ma własny obiekt `Layout`; nie ma globalnego statycznego seeda.

**Gwarancje startowe (`StarterLayout`).** Kontynent ok. 700 bloków na 0,0, nisko (baza ok. 140–150), z płaskowyżem pod spawn; w promieniu 1000 bloków: archipelag, niski klaster gór Alleluja, duża wyspa oceaniczna z cenotą, ciepła rafa i łańcuch powrotny. Rafa trafia w najcieplejszy punkt w promieniu; jeśli żaden nie jest ciepły, układ dodaje lokalny bias temperatury. Przy pierwszym uruchomieniu świata spawn jest ustawiany na środek płaskowyżu.

**Strefy (`ZoneField`).** `f(x,z) = clamp(smoothstep(0, Rmax, d + A·noise(x,z)), 0, 1)`, gdzie `d` to odległość od 0,0, `Rmax` = 8000, a szum o amplitudzie `A` poszarpuje granice. Progi stref: 1500 / 4000 / 8000. Wartość ciągła skaluje parametry, indeks strefy służy tylko do reguł typu „brak gniazd w Przystani”.

## Warstwa kształtu

Kształt świata to jeden `final_density` w `noise_settings`: maksimum z wysp na niebie i terenu oceanicznego z jaskiniami, przycięte szybami (cenoty, błękitne dziury). Duże formy liczą się w narożnikach komórek (`interpolated`), małe formy analitycznie per blok z szybkim odrzucaniem po obrysie.

**Ustawienia `skyseas:sky_seas`.** Punkt wyjścia: kopia `data/minecraft/worldgen/noise_settings/overworld.json` z jara 1.21.1, w której podmieniamy części terenu, a jaskinie zostawiamy.

```json
{
  "sea_level": 63,
  "default_block": { "Name": "minecraft:stone" },
  "default_fluid": { "Name": "minecraft:water", "Properties": { "level": "0" } },
  "noise": { "min_y": -64, "height": 384, "size_horizontal": 1, "size_vertical": 2 },
  "aquifers_enabled": true,
  "ore_veins_enabled": true,
  "legacy_random_source": false,
  "disable_mob_generation": false,
  "noise_router": { "final_density": "skyseas:final", "...": "reszta w sekcji Biomy i klimat" },
  "surface_rule": { "...": "vanillowe reguły + nasze, sekcja Powierzchnia" }
}
```

**Router (pseudokod).**

```text
oceanic   = seabed(x,y,z)                         # >0 skała pod dnem i wyspy oceaniczne
caves     = vanilla_caves(x,y,z)                  # cheese, spaghetti, noodle, entrances z overworld
oceanicCv = min(oceanic, max(caves, 1 - cave_mask))   # jaskinie tylko tam, gdzie pozwala maska
sky       = sky_islands(x,y,z)                    # >0 duże bryły na niebie
large     = interpolated(max(sky, oceanicCv))
small     = sky_details(x,y,z)                    # łańcuchy, małe wyspy, mostki; per blok, odrzucanie po obrysie
seaSmall  = seabed_details(x,y,z)                 # łuki, nawisy kanionów, podcięte słupy, głazy; per blok
final     = min(max(large, small, seaSmall), shafts(x,y,z)) # szyby wycinają skałę
```

`initial_density_without_jaggedness` (wstępna powierzchnia, używana przez aquifery i surface rules) opisuje dolną powierzchnię: dno i wyspy oceaniczne. Wyspy na niebie leżą nad nią, więc dostają normalną powierzchnię biomu, a aquifer wypełnia wodą tylko obszar poniżej Y 63.

**Własne typy funkcji gęstości**

| Typ | Wymiar | Co liczy | Cache / wywołanie |
| --- | --- | --- | --- |
| `skyseas:layout_root` | – | Trzyma `Layout`, wyprowadza seed; zwraca 0 | – |
| `skyseas:sky_islands` | 3D | Maksimum gęstości wysp z kubełka; natychmiastowe wyjście poza zakresem Y kolumny | W `interpolated`; zakres kolumny w `flat_cache` |
| `skyseas:sky_details` | 3D | Małe formy: wyspy łańcuchów, małe wyspy, mostki skalne, iglice pod spodami | Per blok, odrzucanie po obrysie |
| `skyseas:seabed_details` | 3D | Formy dna 3D: łuki, nawisy kanionów, podcięte słupy, głazy | Per blok, odrzucanie po obrysie i zakresie Y; koszt mierzony w benchu |
| `skyseas:cave_top` | 2D | Górna granica jaskiń: wysokość dna, w iglicach morskich dół świata | `cache_2d` |
| `skyseas:seabed_height` | 2D | Wysokość dna, wysp oceanicznych i form dna 2D (góry podwodne, wulkany, słupy, skarpy, grzbiety, płaskowyże) | `cache_2d` |
| `skyseas:seabed` | 3D | `(seabed_height − y) / skala` | W `interpolated` |
| `skyseas:rock_thickness` | 2D | `seabed_height + 64` | `cache_2d` |
| `skyseas:cave_mask` | 3D | 1 tam, gdzie wolno jaskiniom; skala komory wg grubości skały | W `interpolated` |
| `skyseas:shafts` | 3D | Cenoty i błękitne dziury | Per blok, odrzucanie po obrysie |
| `skyseas:zone_factor` | 2D | Współczynnik strefy | `cache_2d` |

Każdy typ: `MapCodec` zarejestrowany w `Registries.DENSITY_FUNCTION_TYPE` przez `DeferredRegister`, poprawne `minValue()`/`maxValue()` (vanilla używa ich do optymalizacji `range_choice`), `mapAll(visitor)` zwracający podpiętą kopię, `fillArray` nadpisany tam, gdzie da się policzyć kolumnę taniej niż punkt po punkcie.

**Kształty (pakiet `shape`)**

- **Kontynent.** Sylwetka z katalogu: wydłużona (proporcje 1:2–1:4), półksiężyc lub podkowa z zatoką, hak, rozgałęziona (palce lądu) albo nieregularna; budowana z nachodzących na siebie elips z zaszumionym obrysem (rdzeń i satelity rozmieszczone według sylwetki), z lekko zawirowaną domeną i fraktalnym detalem wybrzeża; pole krawędzi ma spadek ok. 1 na blok, więc detal nie odrywa okruchów (test: brak fragmentów < 32×32, odłączone < 3%). Kontynent złożony (ok. 30%): 2–4 człony o różnych sylwetkach i bazach różniących się o 20–60 bloków, połączone przesmykiem albo mostem skalnym, z urwiskiem lub stokiem między nimi. Wysokość: baza 100–200 z przechyleniem całej bryły o 20–40 bloków; startowy 140–150; od Pogranicza ok. 15% wyniesionych (baza 200–240); ok. 10% niskich (baza 70–90). Charakter kontynentu (nizinny 20%, mieszany 30%, wyżynny 15%, górski 20% z pasmem gór wzdłuż kręgosłupa, kaldera 15% z pierścieniem gór z przełęczami i kotliną) ustala wagi form; ok. 20% kontynentów (częściej dalej od spawnu) ma masyw ze szczytami 270–300. Wierzch: regiony terenu (równiny co najmniej 35%, obecnie ok. 49%), pagórki, wyżyny z uskokami, mesy o pionowych ścianach, rzadkie skupiska iglic, tarasowe góry; łagodna mikrorzeźba (±1,5 bloku, fala ok. 40) i lekko falujące linie uskoków; przy części wybrzeża stok zamiast klifu; płaskowyż pod spawn na startowym. Pod płaskim terenem 16–28 bloków skorupy z delikatnymi zwisami (szum 2D do ok. 6 bloków); przy brzegu krawędź jest klifem grubości co najmniej 16 bloków (komórki interpolacji mają 8 bloków wysokości, cieńsze płyty rozpadają się na grudki). Pod spodem 3–8 zwisających iglic skalnych długości 10–40 bloków (sloty na gniazda diamentów), bez masywnego kilu; na nisko położonych kontynentach krótsze albo żadne, bo czubek musi zostać nad Y 70. Iglice i mosty skalne liczy `sky_details` per blok. Szum 3D tylko wycina (amplituda 8, pas 32 bloków od brzegu i wysokie ściany gór); przy brzegu wycina tylko ścianę klifu (wnęki, nawisy), a wierzch i spód krawędzi zostają całe. Jego gradient jest mniejszy niż spadek odległości od powierzchni, więc nie odcina kawałków; łuki na krawędziach kontynentów nie powstają. Test JUnit sprawdza spójność brył w 3D z odtworzoną interpolacją 4×8×4, GameTest na wygenerowanych chunkach. Spód nigdy poniżej Y 30; ok. 10% kontynentów leży nisko (baza 70–90), z klifami do morza i bez zbocza wybrzeża.
- **Wyspa-wieża.** Pionowa bryła o szerokości 15–40 i wysokości 50–150 bloków, z zaszumionym promieniem zwężającym się ku dołowi, płaskim zielonym wierzchołkiem (Y 160–305) i iglicami pod spodem.
- **Mała i średnia wyspa.** Spłaszczona bryła 10–30 lub 30–80 bloków z płaskim wierzchem, zaszumionym obrysem i stożkowym spodem z 0–2 (mała) lub 3–8 (średnia) iglicami; małe liczone w `sky_details`, średnie w `sky_islands`.
- **Archipelag.** Superelipsoidy z zaszumionym promieniem, płaski wierzch, stożkowy spód; rzadkie łuki i mosty jako kapsuły między parą wysp.
- **Góry Alleluja.** Pionowe kapsuły 40–120 bloków, promień zwężający się ku dołowi + szum, lekkie pochylenie, płaski wierzch pod trawę.
- **Łańcuchy.** Spłaszczone elipsoidy o promieniu 4–10 w `sky_details`, z gwarantowaną warstwą ziemi na wierzchu.
- **Wyspy oceaniczne.** Kopuła nad Y 63 wg klasy wielkości, plaża, szelf na Y 35–60 z własnym nieregularnym obrysem i zbocze do dna (dłuższe przy dużych wyspach); atol (niski pierścień z przesmykami, laguna Y 52–59) w ciepłych morzach. Drobny detal obrysu tylko przy brzegu, żeby zbocze szelfu nie było urwiskiem.
- **Formacje monumentalne dna.** Każda strefa biomu dna ma własny zestaw: słupy i kolumny skalne 20–60 bloków w grupach (część do ok. Y 25–30), łuki o rozpiętości 20–60, skarpy 15–40 bloków dzielące dno na tarasy, długie grzbiety, kaniony z nawisami, płaskowyże 20–40 bloków nad dnem, pola głazów, w polach wulkanicznych grupy stożków i pola kolumn bazaltowych, skalne rafy na płyciznach, rzeźba w średniej skali (pagórki i rynny co 50–150 bloków). Formy 2D w `seabed_height`, formy 3D (łuki, nawisy, podcięcia, głazy) w `seabed_details`.
- **Dno (E1).** Baza od Y −5 do −20 (głębiej z odległością); wieloskalowa rzeźba (szum o 4 oktawach, fale od kilkuset do kilkudziesięciu bloków; typowo ±8–10, miękko ograniczona do ±12 bloków względem bazy; siła 0,6–1 zależna od regionu, więc są obszary spokojniejsze i bardziej pofałdowane); wydmy ±2; kaniony w oceanach umiarkowanych (8–14 bloków poniżej otoczenia); rowy jako linie szumu grzbietowego do Y −40 w zimnych i głębokich oceanach (od Pogranicza); wulkany jako stożki z kraterem w zimnych; podwodne góry. Dno leży na ok. Y +9…−34 poza kanionami i rowami (najwyższe pagórki zostawiają nad sobą ponad 50 bloków wody); kaniony i rowy schodzą najwyżej do Y −40 (nic nie jest głębsze od limitu rowów). Wulkany i podwodne góry w E3: wulkany w zimnej i umiarkowanej wodzie (gęściej w zimnej), góry podwodne w każdej poza najcieplejszą.
- **Formy oceaniczne z designu** (sekcja „Oceany”): podwodne łuki i skalne iglice wystające nad wodę (lukewarm) w E3, razem z układem, bo iglice nad wodą podlegają regule czystego nieba; lodowe sufity z kieszeniami powietrza i góry lodowe z głęboko zanurzonymi korzeniami (frozen) w E5 jako featury.
- **Jaskinie.** Vanillowe składniki overworld skopiowane z JSON. Maska: zero jaskiń w pasie stropu (domyślnie 12 bloków pod powierzchnią dna lub wyspy) poza zaprojektowanymi wejściami; próg komory (cheese) przesuwany wg `rock_thickness`, więc grubsza skała daje większe komory. W wyspach na niebie tylko korytarze (spaghetti, noodle) w partiach grubszych niż 30 bloków, bez komór.
- **Szyby.** Cenota: cylinder o średnicy 6–14 bloków od wierzchu wyspy do sufitu komory, z jeziorkiem na dnie. Błękitna dziura: cylinder 8–20 od dna płycizny do zalanej komory. Każdy szyb kotwiczy w `cave_mask` wymuszoną komorę pod sobą, więc nigdy nie kończy się w litej skale.

## Biomy i klimat

Biomy wybiera vanillowe `multi_noise` z presetem `minecraft:overworld` (zgodność z TerraBlender i modami biomów); sterujemy nim, licząc w routerze parametry klimatu trójwymiarowo, z innymi wartościami na niebie, w wodzie i pod powierzchnią skały.

| Parametr routera | Na niebie, w zasięgu wyspy | Woda i powietrze nad otwartym morzem | Pod powierzchnią skały |
| --- | --- | --- | --- |
| `continents` | Kontynent: daleko w głąb lądu (≥ 0,3); archipelag i Alleluja: 0,03–0,3; łańcuchy: ok. 0,03 | Głęboki ocean (≤ −0,455) przy wodzie > 30 bloków, ocean (−0,455…−0,19) płycej; nad wyspą oceaniczną wybrzeże (−0,19…0,03); wyspy grzybowe < −1,05 | Środek lądu (0,1–0,3), żeby wybierały się biomy jaskiniowe |
| `erosion` | Vanillowy szum; góry kontynentów i bryły Alleluja niska (szczyty), płaskie tereny wysoka | Vanillowy szum | Vanillowy szum |
| `ridges` | Vanillowy szum | Vanillowy szum | Vanillowy szum |
| `temperature` | Vanillowy szum 2D, chłodzenie o 0,1 na każde 25 bloków powyżej Y 200, bias z układu | Ten sam szum 2D (spójny region) | Vanillowy szum 2D |
| `vegetation` | Vanillowy szum 2D, przesunięcie klastrów ku ich biomowi | Vanillowy szum 2D | Vanillowy szum 2D |
| `depth` | 0 na powierzchni i w powietrzu | 0 | `(Y powierzchni − y) / depthScale` (domyślnie 100): biomy jaskiniowe od ok. 20 bloków, deep dark od ok. 110 bloków, czyli tylko pod grubą skałą |

Progi w tabeli to wartości z vanillowego `OverworldBiomeBuilder` i Claude Code ma je zweryfikować w kodzie 1.21.1 przed implementacją.

- **Zasięg wyspy** to jej obrys plus 16 bloków. Poza nim powietrze dostaje klimat oceaniczny, więc nad otwartym morzem gracz widzi biom oceaniczny.
- **Klastry tematyczne.** Każdy klaster ma wybrany punkt klimatu; w jego obrysie temperatura, wilgotność i erozja są mieszane w 80% ku temu punktowi, co daje jeden dominujący biom.
- **Teren czyta klimat.** Kształt dna (rafy w ciepłych, wulkany i rowy w zimnych) używa tej samej funkcji temperatury co biomy, więc rafa zawsze trafia w warm ocean.
- **Koszt.** Biomy są próbkowane co 4 bloki, ale dla każdej kwarty; funkcje klimatu korzystają z kubełka układu i danych 2D z cache, bez własnych szumów 3D.
- **Bez własnych biomów (decyzja 2026-11-27, `docs/decisions/2026-11-27-no-seabed-biomes.md`).** Regiony dna to prowincje z E3 (`SeabedProvinces`), formy moda, nie biomy; w wodzie zostają vanillowe biomy oceaniczne. Materiały i dekoracje regionów w E5 po prowincji.
- **Pozostałe pola routera** (`barrier`, `fluid_level_floodedness`, `fluid_level_spread`, `lava`, `vein_toggle`, `vein_ridged`, `vein_gap`) zostają vanillowe; vanillowe żyły rud same trafiają w skałę pod dnem.

## Powierzchnia, featury i rudy

Powierzchnia to vanillowe surface rules z kilkoma regułami dołożonymi na początku; dekoracja to własne featury dodane biome modifierami NeoForge do wszystkich biomów overworld, a o warstwie decydują placement modifiery czytające układ.

**Surface rules (kolejność = priorytet)**

1. **Gołe dno.** Biom oceaniczny, Y < 40, szum powyżej progu, nie warm ocean: na wierzchu deepslate, tuff albo basalt zamiast piasku i żwiru, żeby rudy były widoczne.
2. **Strefy wulkaniczne.** Własny warunek `skyseas:in_zone` (rejestr `Registries.MATERIAL_CONDITION`): basalt, blackstone, magma na krawędzi krateru.
3. **Rafy.** Ciepła płycizna: piasek; koralowce dokłada vanillowa featura warm ocean.
4. **Vanilla w całości.** Gradient deepslate (Y 0–8) i wszystkie reguły overworld (trawa, piasek, badlands, śnieg) zostają i działają też na wyspach, bo leżą nad wstępną powierzchnią.

**Biome modifiers** (`data/skyseas/neoforge/biome_modifier/`): `neoforge:remove_carvers` usuwa `minecraft:cave`, `minecraft:cave_extra_underground` i `minecraft:canyon` z `#minecraft:is_overworld`; `neoforge:add_features` dodaje nasze placed features do `#minecraft:is_overworld` w odpowiednich etapach.

**Placement modifiers**

| Typ | Działanie |
| --- | --- |
| `skyseas:island_relative_height` | Y liczony od lokalnego wierzchu wyspy (np. 4–60 bloków pod nim); odrzuca pozycje poza wyspami na niebie |
| `skyseas:zone_count` | Mnoży liczbę prób przez `lerp(1, maxMult, zoneFactor)` |
| `skyseas:layer_filter` | Przepuszcza tylko wybraną warstwę: SKY, SEABED, OCEAN\_ISLAND, UNDERGROUND |

**Featury**

| Featura | Etap | Gdzie | Uwagi |
| --- | --- | --- | --- |
| Rudy wysp: miedź, cynk, złoto, redstone, żelazo | `UNDERGROUND_ORES` | Wyspy na niebie, 4–60 bloków pod wierzchem | Vanillowy typ `ore`, `discard_chance_on_air_exposure: 0`, więc rudy widać na urwiskach; cynk tylko z Create przez warunek `neoforge:mod_loaded` |
| Odsłonięcia rud na dnie | `UNDERGROUND_DECORATION` | Gołe dno, ściany rowów, zbocza wulkanów | Skupiska rudy i bloki surowej rudy; liczba rośnie z głębokością i strefą |
| Gniazda diamentów | `UNDERGROUND_ORES` | Sloty w iglicach pod spodami wysp z układu | 3–8 rud diamentu; brak w Przystani |
| Kaskady wodospadów | `FLUID_SPRINGS` | Ujścia z układu na krawędziach kontynentów | Półki z oczkami co 20–30 bloków; woda stawiana jako gotowe stany spadającej wody, bez ticków płynów |
| Dekoracja spodów | `VEGETAL_DECORATION` | Dolne powierzchnie wysp | Zwisające korzenie, glow lichen, pnącza, dripstone |
| Kominy | `UNDERGROUND_DECORATION` | Zimne oceany | Soul sand lub magma plus gotowe bloki kolumny bąbelków, bez ticków |
| Oświetlenie dna | `UNDERGROUND_DECORATION` | Rowy i dno | Glow lichen na ścianach, sea pickle na dnie |
| Jeziorka na wyspach | `LAKES` | Kontynenty | Niecki z wodą, źródła rzek i kaskad |
| Ślady sculku (opcjonalnie) | `UNDERGROUND_DECORATION` | Dno rowu nad biomem deep dark | Wskazówka, że niżej jest starożytne miasto |

Vanillowe rudy zostają bez zmian: ich zakresy Y same umieszczają diamenty, lapis i główne złoża złota i redstone pod dnem. Każda featura wykonuje ograniczoną pracę: pozycje bierze z układu albo z heightmapy `OCEAN_FLOOR_WG`, bez skanowania całego chunka w 3D.

## Struktury

Struktury vanilli i modów zostają; trójwymiarowe biomy załatwiają większość przypisań do warstw, resztę robią warunki spawnu Lithostitched z własnymi typami warunków, a mixiny są ostatnią deską ratunku.

| Struktura lub grupa | Reguła | Mechanizm |
| --- | --- | --- |
| Wioski, outposty, świątynie, igloo, rezydencje, lądowe ruined portals | Start na wyspie, co najmniej 24 bloki od krawędzi (wioski 32), teren względnie płaski | Biomy 3D + `lithostitched:set_structure_spawn_condition` z warunkiem `skyseas:island_interior` i sprawdzeniem płaskości z Lithostitched |
| Monumenty | Głęboki ocean, dno pod spodem | Vanilla lub YUNG's; offset głębokości w configu YUNG's |
| Wraki, ruiny oceaniczne | Dno lub płycizna | Vanilla (heightmapa dna) |
| Zakopane skarby | Plaże wysp oceanicznych | Vanilla (biom plaży) |
| Twierdze | Pod dnem, najchętniej pod dużymi wyspami i płyciznami, strop co najmniej 12 bloków | Przesunięcie Y pod lokalne dno w miejscu, gdzie vanilla przesuwa twierdzę pod poziom morza (mixin); do weryfikacji |
| Kopalnie (zwykłe i badlands) | Pod dnem, strop co najmniej 12 bloków; wersja badlands nie generuje się na wyspach | Jak twierdze |
| Trial chambers | Pod dnem, strop co najmniej 12 bloków | Warunek `skyseas:rock_above` |
| Starożytne miasta | Tylko pod grubą skałą | Biom deep dark wybiera się tylko tam (parametr `depth`) + `skyseas:rock_above` |
| Struktury z modów | Działają jak vanilla; lekkie wystawanie poza krawędź jest akceptowane | Bez zmian; gdy któraś masowo psuje widok, dopisujemy ją do tagu z regułą |

**Własne warunki** (pakiet `structure`): `skyseas:island_interior` (minimalna odległość od krawędzi), `skyseas:rock_above` (minimalna grubość skały nad startem, liczona z `seabed_height`), `skyseas:layer` (SKY, SEABED, OCEAN\_ISLAND). Rejestrowane w rejestrze warunków Lithostitched, jeśli jest otwarty dla innych modów; jeśli nie, jeden mixin w punkcie walidacji pozycji struktury czyta tagi struktur.

**Konfiguracja przez tagi**, żeby dopisywać struktury z modów bez zmian w kodzie: `#skyseas:requires_island_interior`, `#skyseas:requires_rock_above`, `#skyseas:below_seabed`.

## Wydajność

Wydajność mierzymy względem vanillowego overworldu na tym samym seedzie i sprzęcie; generator mimo głębszego oceanu nie może być wyraźnie wolniejszy. Każdy etap wdrożenia kończy się pomiarem.

| Metryka | Cel | Pomiar |
| --- | --- | --- |
| Czas etapu `noise` na chunk | ≤ 1,3× vanilli | `/skyseas bench` |
| Przepustowość pregeneracji (chunki/s) | ≥ 75% vanilli | Chunky, ten sam seed i promień |
| Alokacje w `compute()` funkcji gęstości | 0 | Spark (profil alokacji) lub JFR |
| Pamięć cache układu | ≤ 64 MB | `/skyseas info cache` |
| Liczenie jednego regionu układu | ≤ 20 ms | Log + benchmark |
| TPS przy locie przez niewygenerowany teren, 4 graczy | ≥ 19,5 | Spark |

**Techniki (obowiązkowe)**

1. **Interpolacja.** Duże formy liczą się tylko w narożnikach komórek (`interpolated`), czyli na siatce 4×8×4 bloki.
2. **Cache 2D.** Wszystko, co zależy tylko od x i z (wysokość dna, grubość skały, strefa, zakres Y wysp w kolumnie), idzie przez `cache_2d` lub `flat_cache`, a `fillArray` liczy jedną wartość na kolumnę.
3. **Szybkie wyjścia.** Gdy y jest poza zakresem wysp w kolumnie (z zapasem 4 bloków), funkcja zwraca stałą. Kosztowne szumy jaskiń są za `range_choice` i liczą się tylko poniżej stropu.
4. **Układ.** Raz na region, indeks kubełkowy, ostatni region w `ThreadLocal`, dane jako tablice prymitywów.
5. **Szum 3D tylko tam, gdzie widać efekt.** Pasy krawędzi i stoki; wszędzie indziej 2D.
6. **Hash pozycyjny** (SplitMix64) zamiast `java.util.Random`; szum kształtów OpenSimplex2S z prekomputowanymi tablicami.
7. **Featury** biorą pozycje z układu i heightmap, mają ograniczone pętle, a wodę i kolumny bąbelków stawiają jako gotowe stany bez ticków.
8. **Puste sekcje.** Powietrze nad oceanem zostaje pustymi sekcjami (tanie w pamięci i przy wysyłce do klienta).

**Zakazy w gorących ścieżkach** (`compute`, `fillArray`, featury per blok): alokacje obiektów (w tym boxing, lambdy, streamy, iteratory kolekcji); `synchronized` i blokady (poza jednorazowym liczeniem regionu); dostęp do świata z funkcji gęstości; logowanie; wyjątki jako przepływ sterowania; nieograniczone pętle po wyspach.

**Pregeneracja.** Przed startem gry Chunky generuje obszar o promieniu 8000–10000 bloków; dalsze tereny generują się w locie i to dla nich liczy się cel TPS.

## Konfiguracja i parametry zamrożone per świat

Wszystkie liczby z dokumentu designowego są parametrami w configu COMMON (`config/skyseas-common.toml`), a przy pierwszym uruchomieniu świata ich kopia zostaje zamrożona w folderze świata, bo zmiana parametrów po wygenerowaniu części świata tworzy szwy na granicy starych i nowych chunków.

| Klucz | Domyślnie | Znaczenie |
| --- | --- | --- |
| `layout.regionSize` | 1024 | Bok regionu układu (bloki) |
| `layout.cacheRegions` | 512 | Maksymalna liczba regionów w cache |
| `zones.radii` | 1500, 4000, 8000 | Progi stref Przystań / Pogranicze / Dzicz |
| `skyForms.maxY` | 305 | Najwyższy wierzch formy na niebie poza kontynentami (limity stref zniesione 2026-11-27) |
| `seabed.baseY` | −5 … −20 | Baza dna od spawnu do Krańca |
| `seabed.relief` | ±12 (typowo ±8–10), min. siła 0,6 | Wieloskalowa rzeźba dna względem bazy |
| `seabed.duneAmplitude` | 2 | Wydmy (±) |
| `seabed.canyonDepth` | 8 … 14 | Głębokość kanionów (oceany umiarkowane) od spawnu do Krańca |
| `seabed.trenchMinY` | −40 | Najgłębsze rowy (pod ciśnienie w Create Deep Seas) |
| `continents.cellSize` | 1700 | Rozstaw kontynentów |
| `continents.boxGap` | 240 … 600 | Przerwa między pudełkami obrysu sąsiadów; brzeg–brzeg wychodzi 400–800 |
| `continents.crust` | 16 … 28 | Skorupa pod płaskim terenem; krawędź klifu 16 |
| `continents.edgeNoise`, `continents.edgeBand` | 8; 32 | Szum 3D przy brzegu: amplituda (tylko wycina), szerokość pasa |
| `continents.baseY` | 100 … 200; startowy 140 … 150 | Baza terenu kontynentów |
| `continents.tilt` | 20 … 40 | Przechylenie bryły (bloki różnicy między krańcami) |
| `continents.compositeShare` | 0,30 | Udział kontynentów złożonych (2–4 człony, różnica wysokości 20–60) |
| `continents.elevatedShare`, `continents.elevatedBaseY` | 0,15 (od Pogranicza); 200 … 240 | Kontynenty wyniesione nad chmury |
| `continents.lowShare`, `continents.lowBaseY` | 0,17 (z kontynentów niewyniesionych poza kalderami; wynik ok. 10%); 70 … 90 | Niskie kontynenty z klifami i spodem w morzu |
| `continents.massifShare`, `continents.massifPeakY` | 0,20 (rośnie z odległością); 270 … 300 | Masywy górskie |
| `islands.hangingSpires` | 3 … 8 (małe wyspy 0 … 2), długość 10 … 40 | Iglice pod spodami wysp |
| `towers.perRegion`, `towers.size` | 2 … 4; szerokość 15 … 40, wysokość 50 … 150 | Wyspy-wieże |
| `skyIslands.perRegion`, `skyIslands.size` | 10 … 20; 10 … 30 i 30 … 80 | Małe i średnie wyspy (ok. połowa jako satelity 100 … 400 od kontynentu) |
| `skyForms.cellSize`, `skyForms.cellChance`, `skyForms.densityMin`, `skyForms.towerShare` | 80; 0,8; 0,04; 0,10 | Siatka wież i wysp pojedynczych; szansa na komórkę z szumu gęstości (fala 1100, krzywa t², więc roje i otwarte przestrzenie) między 0,04 a 0,8 (wynik: ok. 2,5 wieży i 39 wysp na region) |
| `skyForms.heightScale`, `skyForms.sunkShare`, `skyForms.sunkTop`, `skyForms.islandTopMin`, `skyForms.towerTopMin` | 900; 0,1; 64 … 75; 80; 160 | Pasma wysokości wysp (szum), wyspy zatopione |
| `skyForms.detailRadiusMax`, `skyForms.detailOverlap`, `skyForms.continentOverlap` | 40; 0,45; 0,3 | Wyspy liczone per blok: mogą się nakładać (środki ≥ 0,6 sumy promieni w klastrze) i wchodzić do 30% promienia na brzeg kontynentu |
| `clusters.archipelagoChance`, `clusters.hallelujahChance`, `clusters.radius` | 0,45; 0,30; 300 … 480 | Klastry na narożnikach siatki kontynentów (archipelag 20–60 wysp, Alleluja 20–50 brył) |
| `clusters.edgeChance`, `clusters.edgeRadius` | 0,30; 200 … 260 | Klastry na środkach boków siatki |
| `clusters.guaranteeDistance`, `clusters.guaranteeRadius` | 650; 230 | Gwarantowany archipelag i klaster Alleluja przy spawnie |
| `ocean.islandDensity` | ok. 2× wartości z E3a | Gęstość wysp oceanicznych |
| `visibility.sky` | 1 wyspa w 250, 3 w 500 (z Y 150) | Reguła widoczności nieba |
| `visibility.sea` | coś w 250 | Reguła widoczności morza |
| `visibility.seabed` | forma w 150, monumentalna (> 30 bloków) w 500 | Reguła widoczności dna |
| `continents.minBottomY` | 30 | Nic z form na niebie poniżej (dno sięga Y 9); bufor 50 bloków zniesiony 2026-11-27 |
| `starter.continentRadius` | 350 | Kontynent startowy (płaskowyż pod spawn: promień 150) |
| `starter.guaranteeRadius` | 1000 | Promień gwarancji startowych |
| `shading.maxFraction` | — | Zniesiony 2026-11-27; zacienienie tylko raportowane |
| `clearSky.margin` | 16 | Margines czystego nieba nad lądem w oceanie |
| `clusters.minCorridor` | 32 | Minimalna szerokość głównego przelotu przez klaster (pod rozmiar statku) |
| `chains.maxStep`, `chains.maxGap` | 12, 6 | Krok pionowy i przerwa w łańcuchach powrotnych |
| `caves.roofThickness` | 12 | Strop bez jaskiń pod powierzchnią dna i wysp |
| `caves.thicknessScale` | 1,0 | Jak mocno grubość skały powiększa komory |
| `climate.depthScale` | 100 | Skala parametru `depth` (deep dark tylko pod grubą skałą) |
| `climate.altitudeCooling` | 0,1 na 25 bloków powyżej Y 200 | Chłodzenie z wysokością |
| `ores.zoneMaxMultiplier` | 2,0 | Bogactwo rud w Krańcu względem Przystani |
| `diamonds.nestChance` | 0; 0,05; 0,15; 0,25 | Szansa na gniazdo w iglicy pod wyspą, per strefa |

**Zamrażanie.** W `ServerAboutToStartEvent` (przed załadowaniem poziomów) mod czyta `<świat>/skyseas/params.json`. Gdy pliku nie ma, zapisuje bieżące parametry, wersję moda i `generatorVersion`. Gdy jest, używa zapisanych wartości, a różnice względem configu loguje jako ostrzeżenie. Opcja `allowParamChange = true` świadomie nadpisuje snapshot (szwy poza wygenerowanym obszarem). `generatorVersion` rośnie przy każdej zmianie kodu, która zmienia wynik generacji, a niezgodność też jest ostrzeganiem.

## Narzędzia deweloperskie i testy

Testowanie opiera się na trzech filarach: komendach, które pokazują układ bez latania po świecie, automatycznych testach reguł z dokumentu designowego oraz ręcznych testach z pełną paczką modów.

**Komendy** (poziom uprawnień 2)

| Komenda | Działanie |
| --- | --- |
| `/skyseas info` | W pozycji gracza: warstwa, strefa i współczynnik, wyspa (id, typ), Y dna, grubość skały, wartości klimatu, biom |
| `/skyseas info cache` | Stan cache układu: liczba regionów, trafienia, pamięć |
| `/skyseas locate <typ>` | Najbliższa wyspa danego typu, cenota, błękitna dziura, rów, wulkan lub formacja; z układu, bez generacji (od E3a: kontynent, wyspa oceaniczna, duża wyspa, atol, góra podwodna, wulkan, iglica morska) |
| `/skyseas map <promień> [skala]` | PNG z góry liczony z samego układu (typy wysp, strefy, czyste niebo, zacienienie, regiony dna, miejsca łamiące reguły widoczności) do `skyseas/maps/`; sekundy zamiast godzin latania |
| `/skyseas audit <promień>` | Reguły widoczności z designu (niebo, morze, dno: odsetek punktów, które je spełniają, i lista najgorszych miejsc) liczone z układu; na wygenerowanych chunkach: procent zacienienia morza, wyspy nad lądem w oceanie (ma być 0), grubość stropów, objętość jaskiń względem grubości skały, rudy per strefa, położenie struktur; wynik w CSV |
| `/skyseas bench <chunki> [here] [vanilla]` | Generuje N chunków w odległym obszarze (z `here`: wokół pozycji), mierzy czasy etapów (biomes, noise, surface, features), zapisuje CSV do porównań między commitami |

**Testy automatyczne**

- **JUnit (bez Minecrafta):** determinizm (ten sam seed daje identyczny region niezależnie od kolejności zapytań i liczby wątków); reguły (zero wysp nad wyspą oceaniczną z marginesem, raport zacienienia, nic z nieba poniżej Y 30, duże formy bez nakładania, rozkład wysokości wysp, kroki łańcuchów, gwarancje startowe, reguły widoczności, przerwy między kontynentami 400–800, brak oderwanych okruchów w 3D); granice kształtów (zakres Y deskryptora obejmuje całą bryłę); czas liczenia regionu.
- **GameTesty:** generują wycinek świata o znanym seedzie i sprawdzają: brak jaskiń w stropie poza wejściami, szyby kończą się w komorze, struktury podziemne leżą pod dnem, rudy wysp istnieją, po generacji kaskad nie zostają zaplanowane ticki płynów.

**Testy ręczne (po Twojej stronie):** lista seedów testowych; lot statkiem z Create Aeronautics (korytarze, wysokości stref); zanurzenie łodzią z Create Deep Seas (rowy, ciśnienie); start serwera z pełną paczką modów; połączenie klientem bez moda.

## Plan wdrożenia

Wdrożenie idzie w dziesięciu etapach; każdy kończy się działającym światem, testami i pomiarem z `/skyseas bench`, a następny zaczyna się dopiero po spełnieniu kryteriów poprzedniego.

1. **E0 — Szkielet.** Projekt z MDK, `@Mod`, rejestry, world preset `skyseas:sky_seas` z kopią vanillowych ustawień, zaląžk `/skyseas info`.
   - Akceptacja: serwer z `level-type=skyseas:sky_seas` startuje i generuje świat identyczny z vanillą; klient bez moda się łączy.
2. **E1 — Ocean i dno.** `seabed_height`, `seabed`, `rock_thickness`, maska jaskiń i skalowanie komór, aquifery.
   - Akceptacja: baza dna Y −5…−20 (z rzeźbą ok. +9…−34, kaniony najwyżej do −40), rowy do −40, brak dziur w dnie, strop 12 bloków; etap `noise` ≤ 1,2× vanilli.
   - Zrealizowane dodatkowo: `skyseas:depth` (parametr biomu `depth` z E4: 0 w wodzie), dolna granica floodedness aquiferów przy dnie (bez suchych kieszeni nad dnem), `continents` stałe (głęboki ocean) do E4.
3. **E2 — Układ i kontynenty.** `Layout`, cache, seed z szumu, poziom kontynentów, kształt kontynentu, `/skyseas map`.
   - Akceptacja: testy JUnit determinizmu; kontynent startowy na 0,0; spód ≥ 113; mapa PNG czytelna.
   - Zrealizowane: `continents` = 0,5 w zasięgu wyspy (obrys + 16) od spodu wyspy do sufitu świata, poza nim −0,6 (pełny klimat w E4); noodle tylko poniżej Y 63 (nie przewierca wysp); regiony terenu, charaktery kontynentów i sylwetka z elips (decyzje z przeglądów E2, opis w sekcji Kształty); dopuszczone pojedyncze słupy skalne odcięte zatoczką od cypla (≥ ok. 150 m²), bez okruchów; `/skyseas info cache`; `/skyseas bench <n> here [vanilla]` mierzy obszar wokół pozycji (np. nad kontynentem).
   - Odstępstwa: szum kształtów to klasyczny simplex 2D/3D z gradientami z tablic indeksowanymi hashem pozycyjnym zamiast OpenSimplex2S; parametry wysp jako `double[]` (precyzja współrzędnych > 2^24); router liczy `max(interpolated(sky), interpolated(oceanic))` zamiast `interpolated(max(...))`, co zostawia ocean bit w bit jak w E1.1; skale szumów kształtu kontynentu są nazwanymi stałymi w `ContinentShape`, liczby z designu w `WorldParams`.
4. **E3 — Pozostałe formy i reguły (przebudowa po teście E3a, w rundach).** Praca na gałęzi `e3-wip` z commitami po drodze; merge na `main` i tag `e3-done` dopiero po akceptacji całego etapu. Każda runda kończy się mapą, benchem i testem w grze, a przed testem lista tego, czego w świecie jeszcze nie ma. Szczegóły rund i kryteria: `docs/status-e3.md`.
   - Runda 0 (zrobiona, E3a): wyspy oceaniczne, szelfy, atole, góry podwodne, wulkany, iglice morskie, `/skyseas locate`.
   - Runda 1: kontynenty (sylwetki, złożone, wysokości i przechylenie, wyniesione, masywy, iglice pod spodem, krawędzie bez okruchów, przerwy 400–800).
   - Runda 2: wypełnienie nieba (małe i średnie wyspy, wieże, archipelagi, góry Alleluja, gwarancja archipelagu i klastra Alleluja, reguła widoczności nieba).
   - Runda 2b (decyzje `docs/decisions/2026-11-27-sky-heights-density.md`): wysokości od wody do 305, wyspy zatopione i niskie kontynenty, gęstsze niebo w rojach i większych klastrach, nakładanie i zlewanie małych wysp.
   - Runda 3 (z zakresem rundy 4, zrobiona 2026-11-27): prowincje dna (`SeabedProvinces`, `WorldParams.SeabedForms`) zamiast stref biomów dna i `seabed_details`; łańcuchy, cenoty, błękitne dziury (`skyseas:shafts`, `skyseas:shaft_near`, `skyseas:shaft_flood`), gwarantowana duża wyspa z cenotą, spawn, `/skyseas audit`. Szczegóły i odstępstwa: `docs/status-e3.md`, „Runda 3: wykonanie”.
   - Akceptacja etapu: audyt pokazuje 0 wysp nad lądem w oceanie, łańcuchy w limitach kroku, szyby kończą się w komorach, reguły widoczności spełnione; etap `noise` ≤ 1,3× vanilli.
   - Zrealizowane w rundzie 1:
     - sylwetki z katalogu; złożone 29% (kaldera nigdy nie jest złożona, więc pozostałe charaktery częściej);
     - wysokości i przechylenie; wyniesione 13,5% poza Przystanią; masywy 22%;
     - iglice pod spodem (średnio 5 na kontynent);
     - przerwy z pudełek obrysu (wyżej);
     - kolumna `sky_mountain` zastąpiona przez `sky_detail` (waga szumu 3D liczona w układzie);
     - nowe funkcje `sky_details` (iglice, mosty skalne per blok) i `sky_spire_floor` (flat_cache, szybkie odrzucanie);
     - krawędź klifu 16 i skorupa 16–28 zamiast 10–25;
     - szum krawędzi słabszy i tylko na ścianie klifu;
     - przejście klifu w stok przybrzeżny wolniejsze;
     - mosty skalne w `sky_details`, bo interpolacja rozbija płyty 6–10 bloków;
     - nowe cele `/skyseas locate` (sylwetki, `rock_bridge`, `elevated`, `massif`, `hanging_spire`); `/skyseas info` pokazuje sylwetkę, bazę, przechylenie, człony i iglice.
   - Zrealizowane w rundzie 2:
     - wspólny kształt `SkyFormShape` dla wież, małych i średnich wysp, wysp archipelagów i brył Alleluja: gwiaździsty obrys z szumem po kierunku (nie odrywa wysepek), płaski wierzch, spód zwężający się od krawędzi grubej na 16 bloków (bryły interpolowane) albo 5 (małe, per blok); wieże i bryły Alleluja to stożki ścięte zwężające się do połowy promienia; bez szumu 3D i bez pochylenia brył Alleluja;
     - małe wyspy (promień < 15) i mosty archipelagów liczone per blok w `sky_details`; pozostałe formy w `sky_islands`; `Layout.DETAIL_SPAN` = 96 (zakres nad podłogą detali);
     - poziom 3: siatka 192 bloków, szansa 0,86, jeden kandydat na komórkę w dowolnym miejscu; z dwóch nachodzących na siebie kandydatów sąsiednich komórek ustępuje ten o niższym priorytecie (rzut z komórki), więc wynik nie zależy od kolejności; 25% wież, z reszty połowa małych;
     - poziom 4: klastry na narożnikach siatki kontynentów (35% archipelag, 25% Alleluja, promień dysku 220–420) i dwa gwarantowane w komórce startowej, 650 bloków od spawnu na przeciwnych osiach (promień 230); narożne klastry ustępują gwarantowanym;
     - kandydaci klastra zależą tylko od ziarna klastra (korytarz 32 przez środek, odstęp 16 między obrysami); kontynenty i ląd w oceanie odrzucają pojedynczych kandydatów bez przesuwania pozostałych, więc sprawdzamy tylko formy dotykające regionu i końce jego mostów;
     - odstępstwo od kolejności poziomów: wyspy pojedyncze omijają dyski klastrów (zamiast klastrów czytających wyspy pojedyncze); wynik też nie zależy od kolejności;
     - mosty i łuki archipelagów: 0–3 na archipelag, między sąsiednimi wyspami z przerwą 12–80 i różnicą wierzchów ≤ 30; łuk (wzniesienie 8–25) przy przerwie ≥ 30 w połowie przypadków, pod limitem strefy;
     - iglice pod spodem: 3–8 pod średnimi wyspami, wieżami i dużymi wyspami archipelagów, 0–2 pod małymi, czubek ≥ Y 90; bryły Alleluja bez iglic;
     - limity stref 200/240/280/305 liczone w środku każdej formy; spód form ≥ Y 110 (wyspy ≥ 120);
     - czyste niebo: formy i mosty odrzucane w odległości 16 od lądu w oceanie i od kontynentów (+ szum nawisów); ląd w oceanie zbierany także poza regionem;
     - korytarze w grubych partiach brył na niebie (`sky/corridors`): vanillowe spaghetti 3D i noodle tam, gdzie bryła ma > 30 bloków, 12 bloków stropu i 6 spągu; bez komór; dotyczy też kontynentów (góry);
     - reguła widoczności nieba liczona po odległości poziomej (`Layout.skyVisible`), na mapie jako punkty co 50 bloków;
     - budżet zacienienia nie jest egzekwowany, tylko raportowany (liczby w `docs/status-e3.md`, decyzja 1.7);
     - `/skyseas locate archipelago|hallelujah|tower|sky_island|sky_bridge`; parametry form w `WorldParams.SkyForms` (rekord przekroczył limit parametrów konstruktora JVM).
   - Zrealizowane w rundzie 2b (`generatorVersion` 13):
     - limity stref zniesione; wierzch wysp z szumu pasma wysokości (fala 900) z rozrzutem; 10% wysp zatopionych (wierzch 64–75, głębokość przycięta, żeby spód został ≥ 36); wieże 160–305, bryły Alleluja od Y 70; iglice ≥ Y 70;
     - kontynenty: baza 100–200, wyniesione 200–240, niskie 70–90 (`continentLowShare` 0,17, wynik 9,5%); `continentMinBottomY` 30; zbocze wybrzeża tylko przy bazie > 123 (stała `COAST_DROP_FLOOR`, zachowuje dotychczasowe wybrzeża, a niskie kontynenty mają klify); wewnątrz obrysu gęstość co najmniej 2 zamiast 1 (zmiana bazy odsłoniła 1–2-blokowe okruchy na czubkach wybrzeży);
     - poziom 3: siatka 112, szansa z szumu gęstości (fala 1500) od 0,1 do 1,0; 15% wież; wszystkie wyspy pojedyncze per blok (`FLAG_DETAIL`), nakładają się na siebie, na wieże i do 30% promienia na kontynenty; wieże dalej bez nakładania (rzut priorytetu) i omijają dyski klastrów;
     - poziom 4: klastry narożne 45%/30%, promień 300–480; klastry na środkach boków siatki (30%, promień 200–260); rangi klastrów: duże formy klastra niższej rangi omijają dyski wyższych (gwarantowane > narożne > boczne); wyspy archipelagu < 40 per blok i nakładają się (środki ≥ 0,6 sumy promieni), duże formy z odstępem 16;
     - nowa kolumna `sky_detail_ceiling` (flat_cache): `sky_details` liczy bloki między podłogą a sufitem detali, bo nakładające się wyspy mogą leżeć na dowolnych wysokościach w jednej kolumnie;
     - noodle poniżej Y 63 tylko w gałęzi oceanu: `max(sky, min(oceanic, noodle<63))` (inaczej wycinał kawałki z zatopionych wysp);
     - testy: JUnit `largeSkyFormsDoNotOverlap`, `skyFormHeights` (zatopione 5–15%, rozrzut wysokości), udział niskich kontynentów 5–15%, bryły 3D od Y 24; GameTesty: okno od Y 29 (kawałki sięgające dna to dno albo formy oceaniczne), pomijane kawałki z samego lodu i śniegu (vanillowe góry lodowe), okno wyspy zatopionej, skan dna od powierzchni formy oceanicznej.
     - korekta po locie (`generatorVersion` 14): wyraźniejsze roje i otwarte przestrzenie (siatka 80, szansa 0,04–0,8 po krzywej t², fala 1100), ciaśniejsze archipelagi (środki ≥ 0,45 sumy promieni), 10% wież.
   - Zrealizowane w rundzie 0: dwa indeksy kubełkowe na region (niebo, ocean) i parametry o zmiennej długości; `ClimateProbe`; `skyseas:cave_top` (bez jaskiń w iglicach morskich); ochrona stropu obejmuje też wierzchni blok dna (GameTest złapał dziurę na stromym zboczu szelfu); `continents` = 0,5 nad lądem wysp oceanicznych od Y 62 (tymczasowo, do E4).
5. **E4 — Klimat i biomy 3D.** Wszystkie funkcje klimatu, chłodzenie z wysokością, klastry tematyczne, biomy jaskiniowe, ciepła rafa przy spawnie; poprawki błędów jaskiń pod dnem (sześciany). Bez własnych biomów.
   - Akceptacja: lądowe biomy na wyspach, oceaniczne w wodzie, deep dark tylko pod grubą skałą, rafy w warm ocean.
6. **E5 — Powierzchnia i featury.** Surface rules (w tym materiały regionów dna po prowincji: basalt, magma, tuff, goła skała), dekoracje regionów dna (kelp, kominy, glow lichen), rudy wysp i dna, odsłonięcia, gniazda diamentów, kaskady, spody, kominy, jeziorka; lodowe sufity z kieszeniami powietrza i góry lodowe z zanurzonymi korzeniami.
   - Akceptacja: audyt rud per strefa zgodny z configiem; brak zaplanowanych ticków płynów po generacji.
7. **E6 — Struktury.** Warunki Lithostitched, przesunięcia Y, tagi.
   - Akceptacja: audyt struktur: wioski w głębi wysp, twierdze, kopalnie i trial chambers pod dnem ze stropem.
8. **E7 — Wydajność i zamrożenie parametrów.** Profilowanie, dopracowanie cache, snapshot parametrów.
   - Akceptacja: wszystkie cele z tabeli Wydajność spełnione.
9. **E8 — Test z paczką modów.** Serwer z pełną paczką, TerraBlender, YUNG's, Create Aeronautics i Create Deep Seas.
   - Akceptacja: brak crashy i błędów worldgenu w logu; Twój przelot i zanurzenie bez uwag blokujących.
10. **E9 — Ograniczenie dystrybucji.** Licencja, odcisk paczki, narzędzie do podpisywania (sekcja niżej).
    - Akceptacja: serwer bez ważnej licencji zatrzymuje się przed wygenerowaniem pierwszego chunka; po aktualizacji paczki wystarczy nowa licencja, bez przebudowy moda.

## Ograniczenie dystrybucji

Najskuteczniejsza ochrona to w ogóle nie wydawać moda graczom: generator działa tylko na serwerze, więc jar nie trafia do paczki klienta. Na wypadek wycieku plików serwera mod przy starcie sprawdza licencję podpisaną Twoim kluczem prywatnym i związaną z seedem świata oraz odciskiem paczki modów.

**1. Tylko serwer.** Mod nie rejestruje pakietów sieciowych ani wpisów w rejestrach synchronizowanych z klientem, a `neoforge.mods.toml` nie wymaga go po stronie klienta. Test z etapu E0: klient bez moda łączy się z serwerem.

**2. Licencja podpisana Ed25519.** Plik `config/skyseas/license.json` (dopuszczalna lista licencji, np. osobna dla serwera testowego):

```json
{
  "serverId": "wojtek-aeronautics",
  "worldSeed": "-1234567890123",
  "modpackFingerprint": "sha256:9f2c...",
  "issuedAt": "2026-11-25",
  "signature": "base64..."
}
```

Podpis obejmuje wszystkie pola poza `signature`, w kanonicznej postaci JSON. Klucz publiczny jest wkompilowany w mod, prywatny zostaje tylko u Ciebie, poza repozytorium. Java 21 ma Ed25519 w standardowej bibliotece, więc bez dodatkowych zależności.

**3. Odcisk paczki (suma kontrolna).** SHA-256 z posortowanej listy `modId@wersja` wszystkich załadowanych modów (z `ModList`). Tryb `strict` dodatkowo hashuje pliki jar; domyślny tryb jest szybszy i nie zmienia się przy przepakowaniu tych samych wersji.

**4. Weryfikacja przy starcie.** W `ServerAboutToStartEvent` mod liczy odcisk, bierze seed z danych świata, sprawdza podpis i zgodność pól. Przy błędzie wypisuje czytelny komunikat (z wyliczonym odciskiem, żebyś mógł od razu podpisać nową licencję) i zatrzymuje serwer przed wygenerowaniem pierwszego chunka. Nigdy nie wraca po cichu do vanilli, bo zostawiłoby to vanillowe chunki w Twoim świecie.

**5. Środowisko deweloperskie.** Gdy `FMLEnvironment.production` jest fałszywe (uruchomienie z Gradle), weryfikacja jest pomijana.

**6. Narzędzie `tools/license-signer`.** CLI z poleceniami `keygen` (jednorazowo para kluczy), `fingerprint` (liczy odcisk dokładnie tak jak mod) i `sign` (tworzy `license.json`). Aktualizacja paczki wygląda tak: start serwera, odcisk z logu, `sign`, podmiana pliku, restart. Bez przebudowy moda.

**7. Formalności.** Licencja „All Rights Reserved” w `neoforge.mods.toml` i README, brak publikacji na CurseForge i Modrinth; opcjonalnie zaciemnienie pakietu `license` ProGuardem.

**Dlaczego podpis, a nie sama suma kontrolna w jarze:** sumę wbudowaną w moda każdy może przeliczyć i podmienić, a każda aktualizacja paczki wymagałaby przebudowy moda. Podpisu nie da się podrobić bez klucza prywatnego, a aktualizacja to tylko nowy plik.

**Granice tej ochrony:** kod Javy da się zdekompilować, a zdeterminowana osoba może usunąć sprawdzenie. Zabezpieczenie zatrzymuje kopiowanie i uruchamianie jara gdzie indziej, nie inżynierię wsteczną, dlatego najważniejszy jest punkt 1.

## Ryzyka i weryfikacja w kodzie Minecrafta

Największe ryzyko to zmiana generatora po starcie świata (szwy); reszta to punkty, które Claude Code ma sprawdzić w zdekompilowanym kodzie 1.21.1 (Parchment) przed implementacją danego etapu, zamiast zgadywać.

**Do weryfikacji**

- [ ] Jak każda struktura podziemna wybiera Y (twierdze: przesunięcie pod poziom morza; kopalnie zwykłe i badlands; trial chambers; starożytne miasta) i gdzie najtaniej wpiąć regułę „pod dnem ze stropem”. Etap E6.
- [ ] Czy rejestr warunków spawnu Lithostitched w wersji z paczki przyjmuje typy z innych modów; jeśli nie, mixin. Etap E6.
- [x] Czy TerraBlender wstrzykuje regiony i reguły powierzchni przy własnym kluczu `noise_settings`; jeśli nie, wariant B: nadpisanie `minecraft:overworld` zamiast osobnego presetu. Etap E4. Wynik (TerraBlender 4.1.0.8): tak; sprawdza typ wymiaru (tag overworld), `NoiseBasedChunkGenerator` i `instanceof MultiNoiseBiomeSource`, reguły powierzchni dopina do ustawień generatora.
- [ ] Zachowanie aquiferów i warunku `above_preliminary_surface` przy dwóch powierzchniach w kolumnie (wyspa nad dnem). Etap E2.
- [x] Progi klimatu w `OverworldBiomeBuilder` 1.21.1: oceany, wybrzeża, wyspy grzybowe, deep dark i `depth`. Etap E4. Wynik: zgodne z tabelą; wybrzeże −0,19…−0,11; biomy powierzchni `depth` 0 i 1, jaskiniowe 0,2–0,9, deep dark 1,1 przy erozji < −0,375 (`isDeepDarkRegion`: erozja < −0,225 i depth > 0,9).
- [ ] Czy klient bez moda łączy się z serwerem (żadnych synchronizowanych wpisów rejestrów). Etap E0.
- [x] Heightmapy nad pustymi kolumnami i vanillowe szukanie spawnu; spawn ustawiany na kontynencie startowym. Etap E3. Wynik: vanilla szuka po klimacie do 2048 bloków; spawn ustawimy w anulowanym zdarzeniu NeoForge `LevelEvent.CreateSpawnPosition` (bez mixina); rozrzut spawnu graczy korzysta z heightmapy; heightmapy nad pustymi kolumnami widzą wodę.
- [x] (nieaktualne: bez własnych biomów, decyzja 2026-11-27) Czy klient vanilla bez moda łączy się z serwerem, który ma biomy z datapacka moda (synchronizacja rejestru biomów jako danych). Etap E4, przed nakładką.
- [x] (nieaktualne: bez własnych biomów, decyzja 2026-11-27) Jak nakładka na źródło biomów (tylko warstwa oceanu) współżyje z TerraBlender i z `/locate biome`; czy da się ją zrobić bez mixina (własny `BiomeSource` opakowujący `multi_noise`). Etap E4.
- [x] (nieaktualne: bez własnych biomów, decyzja 2026-11-27) Który tag biomów daje bliższą mgłę wodną (bagna) i czy działa dla biomu z datapacka. Etap E4.
- [ ] Tagi biomów struktur oceanicznych (wraki, ruiny, monumenty) i spawnu mobów wodnych w 1.21.1. Etap E4.
- [ ] Identyfikatory rudy cynku i jej placed feature w Create 6. Etap E5.
- [ ] Jak Create Deep Seas liczy ciśnienie, do strojenia `seabed.trenchMinY`. Etap E8.

**Ryzyka**

| Ryzyko | Skutek | Zapobieganie |
| --- | --- | --- |
| Zmiana kodu generatora po starcie świata | Szwy na granicy starych i nowych chunków | Tag w git dla wersji świata, `generatorVersion`, pregeneracja 8000–10000 bloków, zmiany tylko świadomie |
| Małe formy znikają przez interpolację | Dziurawe łańcuchy, urwane szyby | Małe formy poza `interpolated` (`sky_details`, `shafts`) |
| Struktury modów nad pustką | Gorsze widoki | Akceptowane; najgorsze dopisywane do `#skyseas:requires_island_interior` |
| Inny mod podmienia ten sam generator (np. Terralith) | Konflikt lub nadpisanie terenu | Nie instalować; mod wykrywa to przy starcie i ostrzega w logu |
| Duże nawisy i wodospady | Koszt oświetlenia i płynów | Gotowe stany płynów, ograniczony szum 3D, pomiar w E7 |
| Aktualizacja modów w paczce | Licencja traci ważność | Komunikat z nowym odciskiem i podpisanie nowej licencji w kilka minut |

## Źródła

- [MDK-1.21.1-ModDevGradle — szablon projektu](https://github.com/neoforgemdks/MDK-1.21.1-ModDevGradle)
- [ModDevGradle — dokumentacja](https://docs.neoforged.net/toolchain/docs/plugins/mdg/)
- [Lithostitched — modyfikatory struktur i warunki spawnu](https://www.mintlify.com/Apollounknowndev/lithostitched/api/modifiers/structure-modifiers)
- [Lithostitched — modyfikatory funkcji gęstości](https://www.mintlify.com/Apollounknowndev/lithostitched/api/modifiers/density-modifiers)
- [Lithostitched — placement modifiers i warunki](https://www.mintlify.com/Apollounknowndev/lithostitched/api/features/placement-modifiers)
