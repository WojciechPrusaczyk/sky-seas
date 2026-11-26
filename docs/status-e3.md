# Sky Seas: raport stanu i plan poprawek (w trakcie E3)

Stan na 2026-11-26, po decyzjach z `docs/decisions/2026-11-26-e3-rework.md`. Decyzje są już wprowadzone do `docs/design.md`, `docs/spec.md` i `CLAUDE.md`, które znowu są źródłem prawdy. Ten plik opisuje, gdzie jesteśmy, czym implementacja różni się od spec, jak decyzje odpowiadają na zarzuty z testu i jak wygląda plan E3 w rundach.

## 1. Gdzie jesteśmy

| Etap | Stan | Tag |
| --- | --- | --- |
| E0: szkielet, preset świata | zamknięty | `e0-done` |
| E1: ocean i dno (+ E1.1: wieloskalowa rzeźba dna) | zamknięty | `e1-done` |
| E2: układ, kontynenty, `/skyseas map` | zamknięty po czterech rundach poprawek w grze | `e2-done` (lokalnie; push zablokował filtr uprawnień, do wypchnięcia ręcznie) |
| E3 runda 0 (dawne E3a): wyspy oceaniczne, szelfy, atole, podwodne góry, wulkany, iglice morskie | zrobiona, na gałęzi `e3-wip` (commit `5fa7482`), oceniona w grze negatywnie | – |
| E3 rundy 1–4 (plan w sekcji 5) | czekają na akceptację planu | – |
| E4–E9 | nie rozpoczęte | – |

Proces od teraz: praca na gałęzi `e3-wip` z commitami po drodze; merge na `main`, tag `e3-done` i push dopiero po akceptacji całego etapu.

**Ostatnie pomiary** (`/skyseas bench 256`, względem vanilli na tym samym seedzie):

| Obszar | biomes | noise | surface |
| --- | --- | --- | --- |
| Kontynent startowy (0,0) | 1,08× | 0,90× | 1,17× |
| Daleki ocean | 1,21× | 0,96× | 0,55× |

Cel ze spec: noise ≤ 1,3×. Jest duży zapas na dodatkowe formy.

**Testy:**
- JUnit 29/29, w tym determinizm przy 8 wątkach, czyste niebo nad lądem w oceanie, klasy wysp, brak okruchów kontynentów i budżet regionu < 20 ms.
- GameTesty 5/5.

## 2. Odstępstwa od spec i designu (wszystkie zrealizowane zmiany)

### Uzgodnione z Tobą w trakcie

- **Kontynenty:**
  - mniejsze: średnica ok. 460–1380 bloków (spec: promień 400–1000), startowy ok. 700 (spec: 1000–1500);
  - siatka 1800 bloków (spec: 3000–4000).
- **Kształt kontynentu:**
  - sylwetka z 3–6 nachodzących na siebie elips z zaszumionym obrysem, lekki warp, fraktalne wybrzeże;
  - dopuszczone pojedyncze słupy skalne odcięte zatoczką (ok. 150 m²).
- **Charaktery kontynentów:**

  | Charakter | Udział | Opis |
  | --- | --- | --- |
  | nizinny | 20% | |
  | mieszany | 30% | w tym startowy |
  | wyżynny | 15% | |
  | górski | 20% | pasmo wzdłuż kręgosłupa |
  | kaldera | 15% | pierścień gór z przełęczami i kotliną |

- **Regiony terenu:**
  - równiny (ok. 49%), pagórki, wyżyny z uskokami, mesy, rzadkie skupiska iglic, tarasowe góry;
  - łagodna mikrorzeźba (±1,5 bloku);
  - szum 3D tylko na krawędziach i wysokich ścianach.
- **Spód kontynentu:** delikatne zwisy z szumu 2D (do ok. 6 bloków).
- **Limit zacienienia 30%:** dotyczy klastrów i łańcuchów, liczony w oknie wokół klastra, z cieniem kontynentów. Kontynentów nie przycinamy.
- **Limity wysokości stref (200/240/280/305):** obowiązują archipelagi i góry Alleluja. Kontynenty mają góry do 250 także w Przystani.

### Techniczne (zapisane w spec przy E1/E2)

- **Szum kształtów:** klasyczny simplex 2D/3D z gradientami indeksowanymi hashem pozycyjnym, zamiast OpenSimplex2S.
- **Parametry wysp:** trzymane jako `double[]` (spec: `float[]`), ze względu na precyzję współrzędnych.
- **Router:** liczy `max(interpolated(sky), interpolated(oceanic))` zamiast `interpolated(max(...))`, co zostawia ocean bit w bit.
- **`continents`:** 0,5 w zasięgu wyspy na niebie (obrys + 16), od spodu wyspy do sufitu świata; poza tym −0,6. To tymczasowe, do E4.
- **Noodle:** tylko poniżej Y 63, żeby nie przewiercał wysp.
- **Nowe komendy:** `/skyseas info cache` i `/skyseas bench <n> here [vanilla]`.

### Nowe w E3a (niezacommitowane)

- **Układ:**
  - dwa indeksy kubełkowe na region: wyspy na niebie i formy oceaniczne;
  - parametry wysp mają zmienną długość (`paramOffset`).
- **Wyspy oceaniczne:**
  - siatka 300 bloków, szansa 0,8 modulowana szumem gęstości (0,25–1,5×), co daje morza pełne wysp i puste;
  - klasy 70/25/5 z designu; duże wyspy dopiero od Pogranicza, ich udział rośnie do 8% na plateau stref;
  - kopuła nad Y 63, plaża, szelf Y 35–60 z nieregularnym obrysem, zbocze 45–90 bloków (krótsze dla skałek).
- **Atole:** w ciepłej wodzie, 50% średnich i dużych wysp; niski pierścień z przesmykami, laguna Y 52–59.
- **Formy dna:**

  | Forma | Woda | Wielkość |
  | --- | --- | --- |
  | góry podwodne | umiarkowana | szczyt Y 40–58, promień 60–150 |
  | wulkany ze stożkiem i kraterem | zimna | krawędź Y 15–50 |
  | grupy 1–4 iglic | letnia | 8–35 bloków nad wodę |

- **Klimat w układzie:**
  - formy zależne od klimatu czytają temperaturę w swoim środku przez `ClimateProbe`, więc forma powstaje cała albo wcale;
  - `LayoutRootFunction` dostała pole `temperature`, bez mixina.
- **Jaskinie:**
  - nowa funkcja `skyseas:cave_top`, która wyłącza jaskinie w iglicach morskich (inaczej je drążą);
  - ochrona stropu obejmuje teraz także wierzchni blok dna. GameTest złapał dziurę na stromym zboczu szelfu, przy którym jaskinie interpolowane z sąsiedniej komórki sięgały powierzchni.
- **Biom nad wyspami oceanicznymi:** `continents` = 0,5 od Y 62, więc biom jest lądowy, ale wybierany szumem vanilli. Tymczasowo, do E4.
- **Komendy:**
  - `/skyseas locate <continent|ocean_island|large_island|atoll|seamount|volcano|sea_spire>` szuka po układzie, bez generacji;
  - `/skyseas info` pokazuje formę oceaniczną;
  - mapa rysuje wyspy oceaniczne i płycizny z prawdziwej wysokości dna.
- **Przesunięcia w planie:** podwodne łuki przeniesione z E3a do E3c, bo to bryły 3D liczone per blok, jak łańcuchy.
- **Wersja:** `generatorVersion` = 9.

### Weryfikacja z „Do weryfikacji” dla E3 (wynik)

- **Szukanie spawnu w vanilli:** przeszukuje klimat do 2048 bloków, potem szuka pełnego bloku w heightmapie. U nas trafiłby na losową wyspę.
- **Rozwiązanie bez mixina:** NeoForge ma zdarzenie `LevelEvent.CreateSpawnPosition`, które da się anulować; ustawimy spawn na płaskowyżu startowym (E3d). Rozrzut spawnu graczy korzysta z heightmapy, więc trafia na wierzch wyspy.
- **Heightmapy nad pustymi kolumnami** widzą wodę; nic nie trzeba zmieniać.

## 3. Twoja ocena po teście w grze (E3a)

1. Wszystko wygląda bardzo surowo.
2. Wyspy i wyspy latające są za daleko od siebie. Między obiektami jest za dużo przestrzeni, więc jest pusto.
3. „Wysepki obok latających wysp” nie wyglądają jak wyspy, tylko jak latające bloki. Do doprecyzowania, czy chodzi o:
   - oderwane okruchy przy krawędzi kontynentu (szum nawisów);
   - iglice morskie;
   - skałki 5–30 bloków na wodzie.
4. Nie znalazłeś gór Alleluja.
5. Dno oceanu:
   - płaskie i puste, mimo że samo dno wygląda OK;
   - brakuje struktur, podbiomów (np. wulkanicznych) i formacji skalnych;
   - formacje powinny czasem sięgać do połowy wysokości oceanu.
   - Twoja sugestia: lepiej dodać własne biomy dna niż nic.
6. Brakuje małych i średnich wysp latających, więc niebo jest praktycznie puste.
7. Duże wyspy (kontynenty) są praktycznie na tej samej wysokości.
8. Twoje stanowisko: trzeba to dopracować przed strukturami. Jeśli E4 lub E5 to poprawią, można je wdrożyć najpierw i potem wrócić.

## 4. Jak decyzje odpowiadają na zarzuty

| Zarzut (sekcja 3) | Przyczyna | Decyzja (plik decyzji) | Runda |
| --- | --- | --- | --- |
| 1. Surowo | Brak form, a nie dekoracji | Formy najpierw (E3), potem biomy (E4) i dekoracje (E5) | 1–3 |
| 2. Za duże odstępy, pusto | Siatka 1800 bloków i brak form między kontynentami | Przerwy między kontynentami 400–800 (1.1); 10–20 małych i średnich wysp oraz 2–4 wieże na region (1.2, 1.3); wysp oceanicznych 2× więcej (2.1); reguły widoczności nieba, morza i dna jako mierzalny cel (3) | 1, 2, 3 |
| 3. Wysepki jak latające bloki | Najpewniej szum nawisów dokładający masę poza obrysem, czego test 2D nie widzi | Szum 3D przy krawędziach tylko wycina (1.6); test sprawdza spójność brył w 3D; współrzędne miejsca podasz osobno | 1 |
| 4. Brak gór Alleluja | E3b nie był zaczęty | Archipelagi i Alleluja z gwarancją jednego klastra każdego typu w promieniu ok. 1000 od spawnu (1.4) | 2 |
| 5. Dno płaskie i puste, brak podbiomów i formacji | Rzeźba ±10 w dużej skali, rzadkie formy związane z temperaturą | Cztery biomy dna z datapacka (2.2); formacje monumentalne, w tym słupy do połowy głębokości (2.3); formy dna częściej i luźniej związane z temperaturą (2.1); `seabed_details` dla form 3D | 3 (strefy i kształty), E4 (pliki biomów), E5 (materiały) |
| 6. Brak małych i średnich wysp, puste niebo | Formy nie istniały | Małe i średnie wyspy, w połowie jako satelity kontynentów (1.3); wyspy-wieże (1.2) | 2 |
| 7. Kontynenty na jednej wysokości | Baza 135–175, płaska bryła | Baza 125–185 z przechyleniem 20–40, startowy nisko, 15% wyniesionych 190–220 od Pogranicza, człony kontynentów złożonych z różnicą 20–60, masywy do 300 (1.1) | 1 |
| 8. Dopracować przed strukturami | – | Przerywamy E3 i przebudowujemy go w rundach przed E4–E9 (5) | wszystkie |

Dodatkowo z decyzji: sylwetki kontynentów z katalogu (wydłużone, półksiężyce, haki, rozgałęzione) zamiast okręgów; spody bez kilu, z 3–8 zwisającymi iglicami.

## 5. Plan E3 w rundach

Każda runda kończy się JUnit, GameTestami, mapą, benchem (`noise` ≤ 1,3× vanilli) i Twoim testem w grze. Przed testem dostajesz listę tego, czego jeszcze nie ma. Zmiana wyniku generacji podbija `generatorVersion` (świat testowy trzeba usunąć).

### Runda 1: kontynenty

**Zakres:**
- **Sylwetki z katalogu:** wydłużona 1:2–1:4, półksiężyc lub podkowa z zatoką, hak, rozgałęziona, nieregularna. Budowane z elips rozmieszczonych według sylwetki; obecny mechanizm obrysu (detal wybrzeża, spadek pola ok. 1 na blok) zostaje.
- **Kontynenty złożone (ok. 30%):** 2–4 człony z bazami różniącymi się o 20–60 bloków. Między nimi urwisko albo stok; człony łączy przesmyk albo naturalny most skalny.
- **Wysokości:**
  - baza 125–185 z przechyleniem bryły o 20–40 bloków;
  - startowy z bazą 140–150;
  - od Pogranicza ok. 15% wyniesionych (baza 190–220);
  - ok. 20% z masywem 270–300, częściej dalej od spawnu.
- **Iglice pod spodem:** 3–8 zwisających iglic długości 10–40 bloków zamiast stożkowych korzeni. Iglice są cienkie, więc trafią do nowej funkcji `sky_details` (per blok, odrzucanie po obrysie i zakresie Y).
- **Krawędzie bez okruchów:** szum 3D tylko wycina. Nowy test sprawdza spójność brył w 3D, bo dzisiejszy test 2D okruchów nie widział.
- **Rozstaw:** komórka siatki dobrana pod przerwy 400–800 bloków.
- Powierzchnia (regiony terenu, charaktery, mikrorzeźba) bez zmian.

**Kryteria akceptacji:**
- **JUnit:**
  - determinizm;
  - przerwy między sąsiednimi kontynentami (krawędź–krawędź): co najmniej 80% w 400–800, żadna poniżej 250;
  - udział złożonych 25–35%;
  - wyniesione 10–20% poza Przystanią i zero w Przystani;
  - startowy z bazą 140–150 i płaskowyżem;
  - wierzch i spód w zakresach (spód ≥ 113, szczyty ≤ 300);
  - brak oderwanych brył w 3D (próbkowanie gęstości wokół krawędzi i iglic);
  - budżet regionu < 20 ms.
- **GameTesty:** 5/5 plus nowy test spójności 3D na wygenerowanych chunkach kontynentu.
- **Mapa:** sylwetki rozpoznawalne. Kolory wierzchu pokazują przechylenie i różnice wysokości członów.
- **Bench:** `noise` ≤ 1,3× nad kontynentem i nad oceanem.
- **Twój test w grze:** w tym miejsce z „latającymi blokami”, jeśli podasz współrzędne.

**Czego po rundzie 1 jeszcze nie będzie:**
- małych i średnich wysp, wież, archipelagów, gór Alleluja ani łańcuchów (niebo między kontynentami nadal puste, choć przerwy będą krótsze);
- gęstszych wysp oceanicznych, formacji dna i stref biomów dna (ocean jak w rundzie 0);
- cenot, błękitnych dziur i automatycznego spawnu (dalej `/tp`);
- spójnych biomów (E4): biom wybiera szum vanilli, także „plains” na górach;
- materiałów, rud, rzek, wodospadów i dekoracji spodów (E5); struktur (E6).

### Runda 2: wypełnienie nieba

**Zakres:**
- **Małe i średnie wyspy (10–30 i 30–80 bloków):** 10–20 na region; ok. połowa jako satelity 100–400 bloków od krawędzi kontynentu, na różnych wysokościach, reszta rozsiana.
- **Wyspy-wieże:** szerokość 15–40, wysokość 50–150, 2–4 na region, także poza klastrami.
- **Archipelagi:** klastry 10–40 wysp 15–150 bloków na Y 120–240; łuki i mosty; korytarz przelotu ≥ 32.
- **Góry Alleluja:** klastry 15–40 brył szerokich na 20–50 i wysokich na 40–120 bloków, na Y 110–305.
- **Gwarancja:** jeden archipelag i jeden klaster Alleluja w promieniu ok. 1000 od spawnu.
- **Reguły:**
  - czyste niebo;
  - wyspy nie wiszą jedna nad drugą;
  - limity wysokości stref (200/240/280/305) dla nowych form;
  - budżet zacienienia z oknem wokół komórki. Jeśli koliduje z gęstością, zgłaszam liczby i proponuję korektę zamiast po cichu usuwać formy.
- **Jaskinie w grubych partiach wysp na niebie:** tylko korytarze (decyzja z E2).
- **Mapa:** warstwa reguły widoczności nieba (punkty, które jej nie spełniają).

**Kryteria akceptacji:**
- **JUnit:**
  - determinizm;
  - reguła widoczności nieba (1 wyspa w 250, 3 w 500 z Y 150) na siatce punktów co 50 bloków w kwadracie 16×16 km; cel 100%, do akceptacji ≥ 98% z listą wyjątków;
  - zero wysp na niebie nad lądem w oceanie;
  - zero wysp wiszących nad inną wyspą;
  - limity stref;
  - zacienienie ≤ 30% albo raport z liczbami;
  - gwarancje przy spawnie;
  - budżet regionu < 20 ms.
- **GameTesty:** wierzch i spód nowych form w zakresach, brak oderwanych okruchów w 3D.
- **Bench:** `noise` ≤ 1,3× w obszarze z klastrem i w otwartym niebie.
- **Twój test w grze:** `/skyseas locate archipelago`, `hallelujah`, `tower`.

**Czego po rundzie 2 jeszcze nie będzie:**
- łańcuchów powrotnych;
- gęstszego oceanu, formacji dna, stref i biomów dna;
- cenot, błękitnych dziur, gwarancji startowych na oceanie i automatycznego spawnu;
- jednego dominującego biomu na klaster (E4);
- pnączy, wodospadów z bryły na bryłę i dekoracji spodów (E5).

### Runda 3: dno

**Zakres:**
- **Wyspy oceaniczne:** ok. 2× więcej, z większym udziałem średnich.
- **Formy dna luźniej związane z temperaturą:** wulkany w zimnej i umiarkowanej wodzie (gęściej w zimnej), góry podwodne w każdej poza najcieplejszą, iglice morskie częściej.
- **Strefy biomów dna w układzie** (skala 300–800, temperatura przechyla szanse):
  - pola wulkaniczne;
  - las kelpu;
  - skalne pustkowie;
  - głębia w rowach.
  
  Każda strefa ma własny zestaw form.
- **Formacje monumentalne:**
  - 2D w `seabed_height`: słupy i kolumny 20–60 w grupach (część do Y 25–30), skarpy 15–40 dzielące dno na tarasy, grzbiety, płaskowyże 20–40 nad dnem, grupy stożków i pola kolumn bazaltowych, skalne rafy na płyciznach, rzeźba w średniej skali (co 50–150 bloków);
  - 3D w nowej funkcji `seabed_details`: łuki 20–60, nawisy kanionów, podcięte słupy, pola głazów.
- **Narzędzia:** `/skyseas info` i mapa pokazują strefę biomu dna; `/skyseas locate` dostaje strefy i formacje.

**Kryteria akceptacji:**
- **JUnit:** determinizm; reguła widoczności morza (coś w 250) i dna (forma w 150, monumentalna > 30 bloków w 500) z tym samym progiem co niebo; formacje do Y 25–30 istnieją; czyste niebo nad nowymi formami wystającymi z wody.
- **GameTesty:**
  - strop dna bez dziur (także na stromych formach);
  - formy z `seabed_details` przyczepione do dna (brak swobodnie wiszących brył);
  - jaskinie pod grubą skałą większe.
- **Bench:** `noise` ≤ 1,3×, z osobnym pomiarem kosztu `seabed_details`.
- **Twój test w grze:** nurkowanie w każdej strefie.

**Czego po rundzie 3 jeszcze nie będzie:**
- nazw, koloru wody, mgły i mobów biomów dna (pliki biomów i nakładka w E4); strefy będą widoczne tylko w kształtach, w `/skyseas info` i na mapie;
- basaltu, magmy, tuffu, kelpu, kominów i glow lichen (E5): pola wulkaniczne będą kamiennymi stożkami;
- łańcuchów, cenot, błękitnych dziur i automatycznego spawnu.

### Runda 4: domknięcie E3

**Zakres:**
- **Łańcuchy powrotne:** 5–15 wysp, krok do 12, przerwa do 6, zapas ziemi, najniższa kilka bloków nad wodą.
- **Szyby:** cenoty (1–2 na dużej wyspie oceanicznej) i błękitne dziury, każde z wymuszoną komorą.
- **Gwarancje startowe na oceanie:**
  - duża wyspa z cenotą;
  - ciepła rafa (lokalny bias temperatury zawsze, poza sondą `ClimateProbe`);
  - łańcuch z kontynentu startowego.
- **Spawn:** na płaskowyżu startowym przez `LevelEvent.CreateSpawnPosition`.
- **`/skyseas audit <promień>`:** reguły widoczności, zacienienie, wyspy nad lądem w oceanie, stropy i szyby; wynik w CSV.

**Kryteria akceptacji** (akceptacja E3 ze spec):
- audyt: 0 wysp nad lądem w oceanie, zacienienie ≤ 30% (albo uzgodniona korekta), łańcuchy w limitach kroku, szyby kończą się w komorach, reguły widoczności spełnione;
- GameTesty: szyby kończą się w komorze, strop bez jaskiń poza wejściami, spawn na kontynencie startowym;
- bench `noise` ≤ 1,3×;
- Twój test w grze.
- Po akceptacji: merge `e3-wip` → `main`, tag `e3-done`, push.

**Czego po E3 jeszcze nie będzie:** spójnych biomów i biomów dna (E4); materiałów, rud, kaskad, rzek, dekoracji (E5); struktur (E6).

## 6. Otwarte punkty

1. **Próg reguł widoczności:** proponuję 100% jako cel i ≥ 98% punktów do akceptacji, z listą wyjątków na mapie. Do Twojej decyzji.
2. **Współrzędne „latających bloków”:** podasz osobno. Runda 1 i tak wprowadza zasadę „szum tylko wycina” i test 3D.
3. **Limit zacienienia 30%:** liczby z rundy 2 pokażą, czy koliduje z gęstością nieba. Jeśli tak, przyjdę z propozycją korekty.

## 7. Bieżące parametry (`WorldParams.DEFAULTS`, wybór)

| Grupa | Wartości |
| --- | --- |
| Świat | Y −64…320, morze 63 (bez zmian) |
| Dno | baza −5…−20 wg strefy; rzeźba ±12 (typowo ±8–10); wydmy ±2; kaniony 8–14; rowy do −40 |
| Kontynenty | siatka 1800; promień 230–690; startowy 350 (płaskowyż 150); teren 135–175; szczyty do 250; skorupa 10–25; spód ≥ 113 |
| Wyspy oceaniczne | siatka 300; szansa 0,8 × gęstość 0,25–1,5; promienie 2,5/15/75/200; szczyt nad wodą 1–8 / 4–18 / 10–40; szelf Y 35–60, szerokość 6–20 / 20–60 / 45–130; zbocze 45–90 |
| Atole | szansa 0,5 w ciepłej wodzie (temperatura ≥ 0,55); laguna Y 52–59 |
| Góry podwodne | siatka 900, szansa 0,5, promień 60–150, szczyt Y 40–58 (woda umiarkowana) |
| Wulkany | siatka 800, szansa 0,7, promień 70–140, krawędź Y 15–50, krater 8–18 (woda zimna) |
| Iglice morskie | siatka 450, szansa 0,6, 1–4 w grupie, promień 3–8, 8–35 nad wodą (woda letnia) |
| Czyste niebo | margines 16 + 12 (szum nawisów) |
| `generatorVersion` | 9 |
