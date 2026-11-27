# Status E4: klimat i biomy 3D (2026-11-27)

## Decyzje w trakcie etapu

- **Bez własnych biomów** (`docs/decisions/2026-11-27-no-seabed-biomes.md`): regiony dna to prowincje z E3, nie biomy. Odpadły pliki biomów, tagi, nakładka na źródło biomów i access transformer.
- **Sześciany pod ziemią odłożone** (decyzja użytkownika). Sonda w GameTeście (przekroje, wodne ściany, zamknięte kieszenie) nie znalazła ich na etapie szumu w 8 miejscach do 7000 bloków; najpewniej powstają w carverach lub featurach. Wracamy, gdy będzie miejsce (współrzędne albo screen).
- **TerraBlender:** testowa paczka „SkySeas modpack” go nie ma, więc test w grze odpada. Zgodność sprawdzona w kodzie (4.1.0.8): zostajemy przy vanillowym `MultiNoiseBiomeSource`, nasz klucz `noise_settings` jest obsługiwany.

## Wykonanie

**Router** (`ClimateFunction`, logika w `layout/Climate`, testy JUnit `ClimateTest`):

| Warstwa | `continents` | Pozostałe |
| --- | --- | --- |
| Niebo w zasięgu wyspy (obrys + 16, od najniższego spodu wysp w zasięgu) | Kontynent 0,5; inne formy 0,15; łańcuchy 0,0 | Chłodzenie 0,1 na 25 bloków od Y 200; klastry: temperatura, wilgotność i erozja w 80% ku punktowi klastra; Alleluja ku erozji −0,85; góry kontynentów ku −0,85 według wysokości gór |
| Ląd oceaniczny | Plaża −0,15 (do 4 bloków nad morzem), wyżej −0,05; grzybowe −1,1, gdzie vanilla < −1,05 | Ten sam klimat co woda |
| Woda i powietrze nad morzem | Głęboki ocean −0,7 przy wodzie > 30 bloków, płycej −0,3 | Ciepła woda (≥ 0,7) przy gwarantowanej wyspie, poszarpany brzeg |
| Pod skałą (> 8 bloków) | max(vanilla, 0,1) | `depth` = (powierzchnia − y) / 100, także w wyspach |

- Punkt klimatu klastra to klimat jego środka (sonda klimatu w układzie), więc niebo zgadza się z oceanem pod spodem.
- Nowe kolumny 2D (`flat_cache`): `skyseas:sky_climate_*`, `skyseas:warm_bias`.
- `/skyseas info`: wartości klimatu, zasięg i punkt klastra, ciepła woda. `/skyseas map`: dodatkowo `biomes-*.png` z legendą `.txt`.
- `generatorVersion` 18.

## Pomiary

- JUnit: wszystkie zielone. GameTesty 8/8; nowy `biomesFollowLayers` (2 seedy, 8×8 km):
  - na wyspach 0 biomów oceanicznych; najczęściej las, równiny, sawanna, śnieżne, dżungla, łąki, pustynia;
  - w otwartej wodzie 0 biomów lądowych;
  - deep dark tylko pod ≥ 80 blokami skały (we wnętrzu grubych kontynentów), pod dnem nigdy;
  - przy gwarantowanej wyspie 100% warm ocean;
  - klastry: dominujący biom 61–68% próbek.
- Bench (`/skyseas bench 400 vanilla`, mediana, ms na chunk):

  | Etap | Sky Seas | Vanilla | Stosunek |
  | --- | --- | --- | --- |
  | biomes | 3,71 | 1,82 | 2,04× |
  | noise | 14,64 | 12,08 | 1,21× |
  | surface | 2,84 | 3,28 | 0,87× |
  | razem | 21,19 | 17,17 | 1,23× |

  Bench liczy teraz biomy spoza chunka (zoom biomów przy krawędzi) raz, jak w prawdziwym `WorldGenRegion`, gdzie leżą gotowe w sąsiadach. Wcześniej liczył je przy każdym zapytaniu bez cache, co zawyżało etap surface (2,59× przed poprawką). Etap biomów: ok. +1,9 ms na chunk względem vanilli, tyle samo co po E3 (1,98×).

## Odstępstwa od spec

- Pod skałą `max(vanilla, 0,1)` zamiast stałego 0,1–0,3: inaczej jaskinie naciekowe (vanilla 0,8+) nie pojawiłyby się nigdzie.
- Pod skałą cieńszą niż ok. 90 bloków erozja podniesiona do ≥ −0,3: z lądowym `continents` pod skałą vanilla wybierała deep dark już ok. 60 bloków pod dnem.
- Biom rzeki na wyspach (ok. 7% próbek), bo `ridges` zostało vanillowe zgodnie ze spec; do oceny w grze.
- Klastry mają dominujący biom w ok. 2/3 próbek, nie w całości: resztę dają chłodzenie z wysokością i `ridges`.
