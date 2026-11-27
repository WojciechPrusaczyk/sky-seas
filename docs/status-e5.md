# Status E5: powierzchnia i featury (2026-11-27)

Decyzje na start: `docs/decisions/2026-11-27-e5-start.md`.

## Runda 1: podstawy i porządek pod ziemią

- Carvery vanilli usunięte z `#minecraft:is_overworld` (podejrzenie: źródło sześcianów pod dnem).
- `spring_water` i `spring_lava` tylko pod dnem (`skyseas:layer_filter` = underground); census: 0 płynących bloków nad morzem.
- `skyseas:seabed_materials` (`RAW_GENERATION`, raz na chunk): pola wulkaniczne bazalt i blackstone, magma przy kraterach stożków prowincji i wulkanów z układu; skalne pustkowie i strome ściany goła skała; poniżej Y 40 poza ciepłą wodą płaty deepslate, tuffu i bazaltu.
- Roślinność dna vanilli przeniesiona na dno z układu (`skyseas:seabed`) oraz własne odpowiedniki kelpu, trawy morskiej i sea pickle (vanillowe featury same pytają heightmapę `OCEAN_FLOOR`, która pod wyspą trafia w wyspę). Pod kontynentem przy spawnie: 0 → ok. 3 tys. bloków kelpu i 8 tys. trawy morskiej.
- `/skyseas census <promień>`: liczby bloków w wygenerowanych chunkach nad i pod poziomem morza, bloki pod kolumnami bąbelków.

## Runda 2: rudy i dno

- Rudy wysp (`skyseas:island_relative_height` 4–60 pod wierzchem, `discard_chance_on_air_exposure` 0, `skyseas:zone_count` do 1,6–1,8× na plateau): miedź, żelazo, złoto, redstone; cynk z Create (`create:zinc_ore`) tylko z warunkiem `neoforge:mod_loaded`. Węgiel i żelazo górne daje już vanilla.
- Odsłonięcia rud na dnie (`skyseas:seabed`, `skyseas:depth_chance` 0,3 przy Y 40 → 1 przy Y −40, `skyseas:zone_count`): węgiel, miedź, żelazo od spawnu; złoto, redstone, lapis od Pogranicza; bloki surowego żelaza, miedzi i złota od Dziczy. Rudy zastępują tylko skałę, więc widać je na gołym dnie i ścianach.
- `skyseas:diamond_nests`: 3–8 rud diamentu w rdzeniu wiszących iglic; brak w Przystani, szansa na iglicę 4% → 50% na plateau.
- `skyseas:seabed_vent`: kominy (2–5 bloków) z soul sand lub magmą i gotową kolumną bąbelków do powierzchni; zimna woda, pola wulkaniczne w zimnej i umiarkowanej.
- Glow lichen na ścianach i sea pickle na dnie w głębiach (od ok. Y −5 / −10).

Census (r = 5 chunków):

| Miejsce | Nad morzem | Pod morzem |
| --- | --- | --- |
| Kontynent startowy (0, 0) | węgiel 10 tys., żelazo 4,7 tys., miedź 2,9 tys., redstone 0,8 tys., złoto 0,6 tys. | odsłonięcia; magma 0,6 tys. |
| Ocean 9000, 9000 | — | surowe bloki rud ok. 230, glow lichen 340, sea pickle 190 |

**Uwaga:** magma leżąca na dnie (pola wulkaniczne, vanillowe `underwater_magma`) jest oznaczana przez vanillę do aktualizacji po generacji, więc z czasem w załadowanym świecie dorabia kolumny bąbelków ciągnące w dół. Liczba kolumn w census zależy przez to od czasu działania serwera, nie od generacji.
