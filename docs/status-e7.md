# Status E7: wydajność i zamrożenie parametrów

## Pomiary (seed 20261125, Ryzen 7 9800X3D)

Wszystkie cele z tabeli „Wydajność” w spec spełnione.

| Metryka | Cel | Wynik |
| --- | --- | --- |
| Czas etapu `noise` na chunk | ≤ 1,3× vanilli | 1,23× (13,31 vs 10,83 ms) |
| Przepustowość pregeneracji | ≥ 75% vanilli | ok. 290%: Chunky, promień 800, 10 201 chunków w 115 s (vanilla 331 s) |
| Alokacje w `compute()` | 0 | 0: w profilu JFR z pregeneracji Sky Seas to ok. 0,7% alokacji serwera, wszystko w featurach i placementach (API ze `Stream`) oraz w `mapAll` (raz na chunk, jak vanilla) |
| Pamięć cache układu | ≤ 64 MB | ok. 97 KB na region; pełny cache (512) ok. 50 MB |
| Liczenie regionu | ≤ 20 ms | jeden wątek, prawdziwy klimat: mediana 7,7 ms, p95 14,2, maks. 17,6 (`/skyseas bench regions`). Pod pełnym obciążeniem pregeneracji średnio 27 ms czasu ściennego (rywalizacja wątków) |
| TPS przy locie 4 graczy | ≥ 19,5 | średnio 20: `/skyseas flythrough 4 30 120`, średni tick 4,5–4,9 ms (vanilla 4,0); najgorsza sekunda 18,8–20,0 (vanilla 20,0), 1–2 ticki ponad 50 ms na 2400 |

Bench (`/skyseas bench 400 vanilla`, mediana, ms na chunk, `generatorVersion` 31):

| Etap | Sky Seas | Vanilla | Stosunek |
| --- | --- | --- | --- |
| biomes | 3,71 | 1,80 | 2,06× |
| noise | 13,31 | 10,83 | 1,23× |
| surface | 2,71 | 3,19 | 0,85× |
| razem | 19,72 | 15,81 | 1,25× |

Szybsza pregeneracja wynika głównie z mniejszej ilości pracy (ocean i powietrze: puste sekcje, mało featur), nie z szybszego kodu: na chunk teren i biomy liczą się wolniej niż w vanilli.

## Nowe narzędzia

- Chunky jako `localRuntime` (tylko serwer deweloperski, nie trafia do jara).
- `/skyseas flythrough <statki> <prędkość> <sekundy>`: statki lecą od pozycji wywołującego w równych kierunkach, każdy trzyma bilet chunków o promieniu view distance jak gracz; raport: średni i najdłuższy tick, ticki ponad 50 ms, TPS najgorszej sekundy. Zastępuje test z 4 graczami do E8.
- `/skyseas bench regions`: liczenie 100 regionów na jednym wątku po rozgrzewce.
- `/skyseas info cache`: średni i najdłuższy czas liczenia regionu.

## Zamrożenie parametrów

- `config/skyseas-common.toml` (COMMON): wszystkie parametry `WorldParams` pod nazwami pól rekordu, zagnieżdżone `[params.skyForms]` i `[params.seabedForms]`; budowany refleksją z rekordu, więc nowy parametr nie wymaga kodu. Wysokość świata poza plikiem (stała, CLAUDE.md).
- Przy starcie serwera (`ServerAboutToStartEvent`, przed poziomami): `<świat>/skyseas/params.json` z wersją moda, `generatorVersion` i parametrami. Gdy istnieje, świat go używa; różnica z configiem i inna `generatorVersion` to ostrzeżenia w logu. `allowParamChange = true` nadpisuje snapshot configiem. Błąd odczytu zatrzymuje start (świat nie zmieni się po cichu).
- `WorldParams.current()` zamiast `DEFAULTS` w kodzie gry; testy dalej używają `DEFAULTS`.
- Sprawdzone na serwerze: nowy świat zapisuje snapshot; po zmianie `skyMaxY` w configu świat używa snapshotu i ostrzega.

## Odstępstwa od spec

- Klucze configu to nazwy pól rekordu (`zoneRadii`, `skyForms.skyMaxY`), nie nazwy z tabeli w spec (`zones.radii`, `skyForms.maxY`): jedno źródło nazw, bez ręcznej mapy ok. 190 kluczy.
- Test TPS z 4 graczami zastąpiony symulacją `flythrough`; prawdziwy w E8.

## Bez zmian

Optymalizacje nie były potrzebne (wszystkie cele spełnione), więc wynik generacji się nie zmienił: `generatorVersion` 31.

## Następnie

Lista uwag z gry w `docs/todo.md` (moby wodne, rzeki, cherry grove, zimowe biomy, Aquamirae).
