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

## Runda 3: kaskady, jeziorka, spody, lód

- `skyseas:cascade` (krok `fluid_springs`): w połowie chunków z krawędzią kontynentu lub wyspy o promieniu ≥ 40 wodospad. Krawędź szukana po heightmapie (spadek ≥ 12 bloków, wierzch ≥ 20 nad morzem). Źródło stoi we wnęce w krawędzi, otwarte boki wnęki dostają murek ze skały spod niej. Woda płynąca i spadająca do morza z gotowych stanów, bez ticków. Census przy kontynencie 8500, 8785 (r = 8): 589 bloków płynącej wody nad morzem.
- Jeziorka: vanillowy `minecraft:lake` z wodą na wyspach (1 na 6 chunków, `skyseas:layer_filter` = sky). Start w rogu chunka, bo jezioro z wodą zamraża powierzchnię do 15 bloków od startu; przy losowym punkcie w chunku sięgało poza dozwolony obszar i wywalało serwer (dlatego vanilla ma już tylko jeziora lawy).
- Spody wysp (`skyseas:island_underside`: powietrze tuż pod spodem wyspy przy spodzie z układu): hanging roots, cave vines, glow lichen, pointed dripstone. Census przy spawnie: ok. 3,9 tys. korzeni, 7,5 tys. pnączy, 3,5 tys. glow lichen.
- `skyseas:frozen_sea` w zamarzniętych oceanach, raz na chunk: lodowy sufit (packed ice, 3 bloki) z kieszenią powietrza w środku (35%) albo korzeń góry lodowej: packed ice z rdzeniem z blue ice, 20–45 bloków pod wodę pod małą czapą (12%). Census przy −1024, −1024: 12,8 tys. bloków packed ice nad wodą i 26,6 tys. pod.
- `generatorVersion` 21.

## Poprawka: kanciaste jaskinie („sześciany”)

- Przyczyna: w `oceanic` jaskinie były obcięte od dołu przez `max(caves, 1 − cave_mask)` (wzór ze spec). Gdzie maska = 1, obcięcie wynosi dokładnie 0, więc cała jaskinia miała gęstość równą 0, czyli leżała na samym progu bryła/powietrze. Po interpolacji powietrze zostawało tylko tam, gdzie wszystkie narożniki komórki miały równe 0: jednoblokowe linie wzdłuż krawędzi komórek (co 4 bloki) i pełne prostopadłościany komórek (4×8×4). Odtworzone z seedem świata testowego w (524…532, −16…0, −476…−464), bez Noisium.
- Poprawka: `max(caves, 1 − 2·cave_mask)`, czyli −1 przy pełnej masce; jaskinie zachowują własny gradient. W tym samym miejscu po poprawce są zwykłe, zaokrąglone jaskinie. Spec zaktualizowana.
- Carvery nie miały z tym nic wspólnego (usunięte w rundzie 1 i tak zostaje).
- `generatorVersion` 22.

## Runda 4: uwagi z testu w grze

- Kaskady z wysp: 25% poprzedniej liczby (szansa na chunk z krawędzią 0,5 → 0,125).
- Źródełka wody (`spring_water`, wychodzą na zboczach wysp oceanicznych): 25 → 19 na chunk. Lawa bez zmian.
- Pnącza z glow berries (rosną i świecą, losowe ticki) tylko pod małymi wyspami (promień < 40, bez kontynentów) i pod formami Alleluja (`small_islands_only` w `skyseas:island_underside`). Korzenie, glow lichen i stalaktyty zostają wszędzie. Census przy spawnie (kontynent): 0 pnączy.
- Więcej dużych komór: próg jaskiń „cheese” 0,27 → 0,22. Udział powietrza w skale pod dnem w miejscu z testu (seed świata testowego, chunki 32…37 × −32…−27): przed poprawką kanciastych jaskiń 6,3%, po niej 14,2%, z nowym progiem 18,2%.
- `ridges` na niebie: znak vanilli, stała wielkość (PV „mid”), w górach kontynentów ku linii szczytów. Wcześniej grzbiety vanilli dawały na wyspach biom rzeki (doliny) i paski klas PV (np. tajga na przemian z windswept). Odstępstwo od spec (tam vanillowy szum wszędzie); spec poprawiona.
- `generatorVersion` 23.

## Runda 5: rzeki, lichen, krawędzie wysp

- Rzeki tylko na kontynentach na niebie (decyzja użytkownika), 1–3 na kontynent zależnie od wielkości (ok. 2,7 średnio w próbie 3 seedów):
  - układ (`ContinentShape.placeRivers`, czysta Java, test `RiverTest`): źródło w najwyższym z kilku punktów z dala od wybrzeża i gór, krok 8 bloków w dół terenu z lekkim ciągiem ku wybrzeżu, poziom wody nigdy nie rośnie, wcięcie najwyżej 14 bloków; bieg, który nie dojdzie do wybrzeża, odpada. Liczone tylko dla kontynentów w budowanym regionie: ok. 5–10 ms na region.
  - teren: `column(TOP)` wycina koryto (dno w kształcie U, brzegi o nachyleniu 0,6); kolumny poza prostokątem rzeki kosztują jedno porównanie. Decyzje klastrów używają `TERRAIN` (wierzch bez rzek), bo rzeki istnieją tylko w układach z danym kontynentem (determinizm).
  - `skyseas:river` (krok `lakes`): woda jako gotowe bloki; dziury w brzegu domurowane; na progach jeden tick płynu (vanilla raz dorabia mały wodospad); ujście za krawędzią to prosty wodospad.
- Kaskady (losowe i z ujść rzek): prosty strumień do morza. Półki z oczkami co 20–30 bloków (design, sekcja o kaskadach) były zrobione, ale użytkownik je odrzucił: wyglądały źle. Odstępstwo od designu.
- `/skyseas locate river|river_mouth`.
- Rzeki dłuższe (uwaga z gry: krótkie): źródło co najmniej 0,25 promienia od wybrzeża, słabszy ciąg ku wybrzeżu (0,25), biegi krótsze niż ok. 160 bloków odrzucane. W próbie 3 seedów: średnio 250 bloków (było 142), ok. 1,75 rzeki na kontynent. Dno pod wodą: dirt z płatami żwiru zamiast trawy.
- Jeziorka na kontynentach: 1 na 12 chunków (było 1 na 6).
- Glow lichen pod wyspami: 16 → 3 próby na chunk.
- Krawędzie wysp (`SkyColumn.density`, jeden szum 2D liczony tylko przy obrzeżu): zaokrąglona górna krawędź (promień 2–5) i podcięcie ściany w dół na ok. połowie wybrzeża (do 0,5 bloku na blok, do 12 bloków pod wierzchem, tylko przy kolumnach grubszych niż ok. 8–20 bloków). Szersze podcięcie (stałe minimum, przesunięty próg) odrywało kawałki od wąskich wież w `LayoutTest` i GameTeście, więc zostało wycofane. Tylko zabiera skałę, nic nie wychodzi poza obrys. Dolna krawędź też zaokrąglona (promień do połowy grubości), więc cienkie obrzeża małych i średnich wysp mają przekrój soczewki zamiast pionowej ścianki.
- `generatorVersion` 28.
