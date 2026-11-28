# Status E6: struktury

Etap zaakceptowany przez użytkownika 2026-11-28 („dla mnie ok”); błędów będzie szukał na koniec, w E8.

## Zrobione

- Lithostitched 1.7.7 jako zależność wymagana (kompilacja `compileOnly`, w dev `localRuntime`, w `neoforge.mods.toml` od 1.7.7). Wersja najstarsza spotkana w paczkach docelowych; nowsze (1.8.x) do sprawdzenia w E8.
- Własne warunki spawnu w rejestrze `lithostitched:placement_condition_type` (rejestr jest otwarty dla innych modów, mixin niepotrzebny):
  - `skyseas:island_interior` (`min_edge`): start na wyspie na niebie co najmniej tyle bloków od obrysu i nie pod jej spodem;
  - `skyseas:rock_above` (`min`): start co najmniej tyle bloków pod dnem (z wyspami oceanicznymi), liczone z `seabed_height`.
- Modyfikatory Lithostitched i tagi:
  - `#minecraft:village`: `island_interior` 32;
  - `#skyseas:requires_island_interior` (outposty, świątynie, chatka wiedźmy, igloo, rezydencje, trail ruins, lądowe ruined portals): 24;
  - `#skyseas:requires_rock_above` (starożytne miasta): 12.
- Twierdze i kopalnie (zwykłe i badlands): mixin (zgoda użytkownika 2026-11-28) w miejscu, gdzie vanilla przesuwa strukturę pod poziom morza. Każdy element ma zostać 12 bloków pod dnem nad własnym obrysem. Kopalnia, która się nie mieści, nie powstaje; twierdza zostaje najniżej, jak vanilla pozwala (musi istnieć dla oka Endera). Kopalnie badlands nie stoją już w powietrzu nad oceanem.
- Trial chambers: start Y −46…−40 zamiast −40…−20 (nadpisany JSON vanilli). Mają ok. 70 bloków wysokości, więc pod zwykłym dnem (35–45 bloków miejsca) wystają ponad dno. Decyzja użytkownika: niżej i wolno wystawać, liczba jak w vanilli.
- Starożytne miasta: głęboko pod lądem w oceanie (głębokość biomu ≥ 0,9, czyli ≥ 90 bloków skały) erozja −0,6, więc zawsze deep dark. Miasta (kotwica na Y −27) powstają pod wyspami oceanicznymi; wcześniej nie powstawały wcale. Decyzja użytkownika.
- `/skyseas audit structures <promień>`: etap startów struktur (vanilla `createStructures`, warunki Lithostitched i mixiny) na odłączonych chunkach, bez terenu. Promień 12 000 w 5 minut. Na element: strop nad podziemnymi, odległość od krawędzi środka pierwszego elementu u naziemnych; CSV w `skyseas/audit/`.
- `generatorVersion` 31.

## Audyt (seed 20261125, promień 12 000)

| Struktura | Liczba | Reguła | Niespełnia | Wystaje do wody |
| --- | --- | --- | --- | --- |
| wioski (5 typów) | 92 | krawędź ≥ 32 | 0 | — |
| inne naziemne | 214 | krawędź ≥ 24 | 7 | — |
| kopalnie | 5165 | strop ≥ 12 | 0 | 0 |
| kopalnie badlands | 412 | strop ≥ 12 | 0 | 0 |
| twierdze | 41 | strop ≥ 12 | 19 | 8 (najgorsza o 22 bloki) |
| starożytne miasta | 29 | strop ≥ 12 | 2 | 2 (o 9 bloków) |
| trial chambers | 1905 | — (wolno wystawać) | 1776 | 1597 |

- Naziemne „niespełnia”: audyt mierzy środek pierwszego elementu, warunek pozycję startu; różnica kilku–kilkunastu bloków.
- Kopalnie: ok. 60% liczby z vanilli (reszta się nie mieści pod dnem).

## Znane problemy (do E8)

- Twierdze nad rowami w dnie: 40 bloków wysokości, pod rowem nie ma miejsca. Możliwa poprawka: spłycenie dna wokół pozycji pierścieni twierdz w układzie.
- Starożytne miasta wystające kawałkiem poza zbocze wyspy oceanicznej.
- Wioski sięgające do Y 62: prawdopodobnie domy przy krawędzi wyspy rzutowane na wodę pod nią. Audyt mierzy tylko środek; do sprawdzenia w grze.
- Spec przewiduje tag `#skyseas:below_seabed`; niepotrzebny, bo mixin działa na typach struktur (twierdza, kopalnia), więc obejmuje też modowe struktury tych typów.
- Sprawdzenie płaskości terenu dla wiosek (spec) pominięte: Lithostitched 1.7.7 nie ma takiego warunku, a jigsaw z dopasowaniem terenu radzi sobie sam.

## Pomiar

Bench (`/skyseas bench 400 vanilla`, seed 20261125, mediana, ms na chunk, `generatorVersion` 31):

| Etap | Sky Seas | Vanilla | Stosunek |
| --- | --- | --- | --- |
| biomes | 4,06 | 1,90 | 2,13× |
| noise | 14,45 | 12,04 | 1,20× |
| surface | 2,75 | 3,25 | 0,85× |
| razem | 21,26 | 17,18 | 1,24× |

`noise` w celu (≤ 1,3×). JUnit: wszystkie testy przechodzą (nowy `deepDarkOnlyDeepUnderOceanLand`). GameTesty: 8/8.
