# Generator świata: latające wyspy nad głębokim oceanem — dokument designowy

Nov 25, 2026 · @Wojciech Prusaczyk

## Wizja i filary

Mod generuje świat do wspólnej gry na serwerze z Create Aeronautics: latające wyspy nad bardzo głębokim oceanem, w wysokościach vanilli (Y −64…320), bez nowych biomów i bez psucia struktur vanilli i modów.

**Fantazja gracza:** niebo to dom i warsztat, ocean to skarbiec i zagrożenie. Gracz zaczyna w chmurach i buduje pierwszy statek z tego, co daje niebo, a po najcenniejsze rzeczy schodzi w głębinę.

**Filary:**

- **Trzy osie progresji.** Wysokość (udźwig statków spada im wyżej), głębokość (ciśnienie w Create Deep Seas) i odległość od spawnu (strefy). Środek świata jest wygodny, a najlepsze nagrody leżą na skrajach.
- **Krajobraz przede wszystkim.** Malownicze formacje wyłącznie z vanillowych bloków, czytelne z daleka i z pokładu statku.
- **Vanilla jako fundament.** Generator rozszerza vanillowy zamiast go zastępować: biomy, struktury i jaskinie działają jak w vanilli.
- **Dwie drogi do celu.** Do każdego ważnego miejsca prowadzi droga wolna i bezpieczna (np. kopanie w dół z wyspy) oraz szybka i ryzykowna (nurkowanie).

## Mechaniki Minecrafta, które kształtują design

Pięć vanillowych mechanik wyznacza twarde reguły generatora.

| Mechanika | Skutek dla gry | Decyzja |
| --- | --- | --- |
| Światło nieba liczy się kolumnami; pod wyspę wpada tylko z boku, na ok. 15 bloków | Pod dużą wyspą jest wieczna noc, moby spawnią się w dzień | Czyste niebo nad lądami w oceanie, limit zacienienia morza |
| Rudy zależą od wysokości Y, nie od rodzaju skały | Vanillowe rozkłady mogą trafić w złą warstwę | Na wyspy dodajemy miedź, cynk oraz mniejsze złoża złota i redstone |
| Upadek do wody nie zadaje obrażeń, z dowolnej wysokości | Ocean działa jak siatka bezpieczeństwa | Pod wyspami głównie woda, drogi powrotne: łańcuchy małych wysp i kaskady wodospadów |
| Vanillowe chmury wiszą na stałym Y 192 | Zasłaniałyby widok z terenu mieszkalnego | Teren mieszkalny na Y 135–175; pas chmur przecinają tylko szczyty (shadery i tak mają własne chmury) |
| Biomy są trójwymiarowe | Jedna kolumna może mieć ocean na dole i las na górze | Struktury i moby same trafiają do właściwej warstwy |

## Układ pionowy świata

Świat zostaje w wysokościach vanilli (Y −64…320) z poziomem morza 63; ocean pogłębiamy, obniżając dno, a nie poziom wody, bo część modów i struktur zakłada vanillową wartość.

| Strefa | Y | Rola |
| --- | --- | --- |
| Najwyższe wyspy | do ok. 305 | Dla najmocniejszych statków; ok. 15 bloków zapasu pod sufitem |
| Góry na kontynentach | 175–250 | Tylko szczyty przecinają pas chmur (192) |
| Powierzchnia kontynentów | 135–175 | Teren do mieszkania i budowy |
| Spód kontynentów | od 113 | 50 bloków powietrza nad wodą |
| Archipelagi | 120–240 | Na różnych wysokościach |
| Góry Alleluja | 110–305 | Dolne bryły dostępne od razu, szczyty dla mocnych statków |
| Poziom morza | 63 | Bez zmian względem vanilli |
| Rafy i płycizny | 35–60 | Ciepłe morza, wejścia do jaskiń |
| Dno oceanu | −5 do −20 | 70–85 bloków wody, 2–3 razy głębiej niż vanillowy deep ocean |
| Rowy | do ok. −40 | Ok. 100 bloków wody |
| Deepslate z jaskiniami | od dna do −64 | 25–60 bloków skały pod głębokim dnem, ponad 120 pod wyspami i płyciznami |

&#91;embedded content: przekrój pionowy świata · Y −64…320\]

Teren mieszkalny leży poniżej pasa chmur, pod wyspami jest głównie woda, a największe jaskinie siedzą pod wyspą oceaniczną i płycizną.

## Lądy na niebie

Niebo tworzą cztery formy: kontynenty jako baza, archipelagi i góry Alleluja do eksploracji oraz łańcuchy małych wysp jako droga powrotna z morza. Wszystkie biorą powierzchnię z reguł biomu, więc działają z biomami vanilli i modów.

| Forma | Skala | Wysokość Y | Rola w grze | Charakter |
| --- | --- | --- | --- | --- |
| Kontynent | 800–2000 bloków | teren 135–175, góry do 250, spód od 113 | Start, baza, wioski | Cienka skorupa (10–25 bloków kamienia pod płaskim terenem); kamień w górach, iglicach, łukach, mesach, urwiskach i nawisach; spore płaskie połacie pod budowę; rzeki i jeziora kończące się wodospadami; płytki spód z kilkoma skalnymi korzeniami |
| Archipelag | wyspy 15–150 bloków, klastry po 10–40 | 120–240 | Eksploracja, nawigacja, trasy dla pilotów | Jeden dominujący biom na klaster, różne wysokości, łuki skalne, naturalne mosty, zwisające pnącza |
| Góry Alleluja | bryły 20–50 szerokości i 40–120 wysokości, klastry po 15–40 | 110–305 | Slalom dla pilotów, dużo kamienia przy małym cieniu | Wysokie, wąskie bryły z zielonymi wierzchołkami, pnącza na dziesiątki bloków, wodospady spadające z bryły na bryłę |
| Łańcuchy powrotne | wyspy 10–40 bloków, po kilka do kilkunastu w łańcuchu | od kilku bloków nad wodą do krawędzi kontynentu | Droga powrotna, zanim gracze mają statek | Normalne małe wyspy z trawą i drzewami, wyglądające jak część archipelagu, a nie rumowisko; rozstawione nieregularnie, na różnych wysokościach. Na każdej jest dość ziemi, żeby dosypać słup albo most do następnej. Najniższa wisi kilka bloków nad wodą |

**Kaskady wodospadów.** Część rzek z kontynentów spływa do morza kaskadami, z półkami i oczkami wodnymi co ok. 20–30 bloków. Gracz może nimi wpłynąć z powrotem w górę i po drodze złapać oddech, bo na długim, nieprzerwanym wodospadzie skończyłoby mu się powietrze.

## Lądy oceaniczne

Wysp oceanicznych jest dużo, ale większość jest mała; zawsze leżą pod czystym niebem, a wielkość wyspy decyduje o wielkości jaskiń pod nią.

| Klasa | Udział | Rozmiar | Co jest pod spodem |
| --- | --- | --- | --- |
| Skałki i wysepki | ok. 70% | 5–30 bloków | Najwyżej mała grota |
| Wyspy średnie | ok. 25% | 30–150 bloków | Średnie jaskinie |
| Duże wyspy | ok. 5% | 150–400 bloków | Megakomory, cenoty, miejsce na twierdze i starożytne miasta |

- **Budowa:** kamień na wierzchu, deepslate głębiej. Kopanie w dół z wyspy prowadzi do głębinowych surowców bez nurkowania.
- **Płycizny:** wyspy siedzą na szelfach na Y 35–60, z rafami w ciepłych morzach. Pod płyciznami są największe jaskinie (patrz: Jaskinie i podziemia).
- **Inne formy:** atole z laguną, skalne iglice wystające nad wodę, podwodne góry sięgające prawie powierzchni.
- **Duże wyspy jako cele wypraw:** jest ich mało, więc stanowią punkty orientacyjne i centra podziemnej eksploracji.

## Oceany

Każdy vanillowy biom wodny dostaje własny krajobraz z vanillowych bloków; dno leży na Y −5 do −20 (70–85 bloków wody), a rowy schodzą do ok. Y −40.

| Biom | Krajobraz | Rola w grze |
| --- | --- | --- |
| Warm ocean | Płycizny, rafy koralowe, atole z laguną, piaszczyste łachy, jasna woda | Pierwsze nurkowania i pierwsze łodzie |
| Lukewarm / deep lukewarm | Lasy kelpu na zboczach, łąki trawy morskiej, podwodne łuki, skalne iglice wystające nad wodę | Średnia głębokość, ruiny |
| Ocean / deep ocean | Rozległe równiny z wydmami, podwodne góry, kaniony | Wraki, dalekie przeprawy |
| Cold / deep cold | Podwodne wulkany z basaltu, tuffu, magmy i obsydianu; kominy z kolumnami bąbelków | Soul sand wynosi w górę i daje oddech (punkt odpoczynku nurka); magma wciąga w dół (pułapka) |
| Frozen / deep frozen | Góry lodowe z głęboko zanurzonymi korzeniami, lodowe sufity z kieszeniami powietrza | Ciemno i niebezpiecznie |
| Rowy (w głębokich biomach) | Najgłębsze miejsca; odsłonięte żyły rud na ścianach, glow lichen jako jedyne światło, sculk na dnie nad starożytnymi miastami | Test kadłubów łodzi z Create Deep Seas |

Miejscami dno to goła skała (deepslate, tuff, basalt) zamiast piasku, żeby rudy były widoczne z łodzi.

## Reguły kompozycji krajobrazu

Dziewięć reguł ma pierwszeństwo przed losowością generatora.

1. **Czyste niebo nad lądami w oceanie.** Żadna wyspa nie rzuca cienia na ląd przy poziomie morza; margines ok. 16 bloków, bo światło z boku sięga ok. 15.
2. **Limit zacienienia.** W danym regionie wyspy zasłaniają najwyżej ok. 30% powierzchni morza.
3. **Wyspy nie wiszą jedna nad drugą.** Im wyżej, tym mniejsze i przesunięte w bok względem niższych.
4. **Bufor 50 bloków** między wodą a spodem kontynentu.
5. **Korytarze lotnicze.** Szerokie, bezpieczne trasy między kontynentami przez archipelagi oraz rzadkie wąskie kaniony dla ambitnych pilotów.
6. **Spody wysp jako widok.** Nawisy, dripstone, zwisające korzenie i glow lichen, bo z morza i z pokładu ogląda się je najczęściej.
7. **Punkty orientacyjne.** Szczyty nad chmurami, wodospady i wyraźne sylwetki widoczne z daleka.
8. **Naturalne krawędzie.** Krawędzie są nieregularne, z nawisami i urwiskami; żadnych prostych cięć, rytmicznych schodów ani rumowisk psujących widok. Przy części kontynentów do wody schodzi łańcuch małych wysp: odległość do następnej wyspy jest mniejsza niż zapas ziemi na bieżącej.
9. **Teren mieszkalny poza pasem chmur.** Powierzchnie kontynentów na Y 135–175; pas chmur (192) przecinają tylko szczyty i wysokie bryły.

## Surowce i rudy

Vanillowe zakresy Y prawie same dzielą rudy tak, jak chcemy; na wyspy dodajemy miedź (w vanilli kończy się na Y 112, tuż pod spodem wysp), cynk z Create oraz mniejsze złoża złota i redstone, żeby te surowce dało się wydobyć także w powietrzu.

| Ruda | Gdzie | Uwagi |
| --- | --- | --- |
| Węgiel | Wyspy | Vanilla |
| Żelazo | Wyspy (górne złoże) i dno | Vanilla |
| Miedź | Wyspy i dno | Na wyspy przeniesiona |
| Cynk (Create) | Wyspy i dno | Na wyspy przeniesiony |
| Andezyt | Wyspy, dużo | Create zużywa go masowo |
| Szmaragdy | Góry na kontynentach | Vanilla |
| Złoto | Dno; mniejsze złoża na wyspach, najwięcej w badlands | Vanilla ma dodatkowe złoże w badlands |
| Redstone | Dno; mniejsze złoża na wyspach, m.in. w rdzeniach gór kontynentów | Miękka blokada: nikt nie utknie, ale dno się opłaca |
| Lapis | Dno | Vanilla |
| Diamenty | Wyłącznie pod oceanem + rzadkie gniazda w skalnych korzeniach pod latającymi wyspami | Gniazda tylko statkiem, od strefy Pogranicze |

Na wyspach część rud wychodzi na ściany urwisk, gór i brył Alleluja, więc żelazo, złoto, redstone i cynk widać z pokładu, a nie tylko w jaskiniach.

**Widoczność pod wodą (pod Create Deep Seas):**

- Rudy podmieniają tylko kamień, więc piasek je chowa. Dlatego miejscami dno jest gołą skałą.
- Na ścianach rowów, zboczach wulkanów i podwodnych klifach są odsłonięte żyły i bloki surowej rudy.
- Glow lichen, sea pickle i magma doświetlają rudy dla łodzi.
- Bogactwo rośnie z głębokością i z odległością od spawnu.

## Jaskinie i podziemia

Im grubsza skała nad bedrockiem, tym większe jaskinie: największe komory leżą pod wyspami oceanicznymi i płyciznami, gdzie skała ma ponad 120 bloków, a pod głębokim dnem (25–60 bloków) są tylko małe groty.

- **System vanillowy.** Pod dnem działają vanillowe komory, korytarze spaghetti i noodle oraz aquifery.
- **Skala.** Pod dużą wyspą komory sięgają setek bloków i są piętrowe, połączone tunelami. Pod głębokim dnem jaskinie są małe, często zalane, z wylotami w ścianach rowów.
- **Cenoty na wyspach.** Naturalne zapadliska z jeziorkiem na dnie, prowadzące prosto do wielkiej komory. Zejście pod ziemię bez nurkowania.
- **Błękitne dziury na płyciznach.** Pionowe szyby w rafie, prowadzące do zalanych komór. Pierwsze zadanie dla nurka albo małej łodzi.
- **Sieć.** Jaskinie pod wyspą łączą się z tymi pod płycizną, więc można zejść lądem i wypłynąć do morza.
- **Woda.** Pod wyspami komory są głównie suche, z podziemnymi jeziorami; pod płyciznami częściowo zalane; na dnie największych, jak w vanilli, jest lawa.
- **Gruby strop.** Pod powierzchnią wyspy i pod dnem płycizny zostaje minimum ok. 10–15 bloków skały. Przebić go mogą tylko zaprojektowane wejścia.
- **Wyspy na niebie.** Jaskinie tylko w grubych partiach (góry, bryły Alleluja), bez wielkich komór, które przebijałyby spody cienkich kontynentów.

&#91;embedded content: przekrój wyspy oceanicznej · Y 80…−64\]

Do tej samej sieci jaskiń prowadzą dwie drogi: cenota z lądu i błękitna dziura z płycizny. Pod głębokim dnem zostają tylko małe groty, za to rudy leżą na wierzchu.

## Struktury

Struktury vanilli i modów zostają: trójwymiarowe biomy same kierują je do właściwej warstwy, a struktury podziemne trafiają pod dno oceanu.

| Struktura | Warstwa |
| --- | --- |
| Wioski, outposty, świątynie, igloo, rezydencje | Kontynenty i większe wyspy, na płaskich terenach |
| Monumenty, wraki, ruiny, zakopane skarby | Ocean i wyspy oceaniczne |
| Twierdze, kopalnie (również te z badlands, które w vanilli generują się wysoko), trial chambers | Pod dnem, najchętniej pod dużymi wyspami i płyciznami |
| Starożytne miasta | Pod dnem, tylko pod grubym stropem |

Struktury podziemne nie mogą wystawać do wody; wymagany jest strop nad nimi. Modowe struktury lekko wystające poza krawędź wyspy są akceptowane. Nad oceanem na wysokości morza leży biom oceaniczny, więc wioska nie powstanie nad wodą.

## Biomy i klimat

Nie dodajemy własnych biomów: generator rozkłada biomy vanilli i modów w trzech wymiarach, według jednej mapy klimatu dla nieba i oceanu.

- **Biomy 3D.** W jednej kolumnie leży biom oceaniczny na dole i lądowy na wyspie. Ryby, drowned i struktury oceaniczne działają poprawnie pod wyspą.
- **Spójny region.** Niebo i ocean korzystają z tej samej mapy klimatu, więc nad zamarzniętym oceanem wiszą śnieżne wyspy.
- **Chłodzenie z wysokością.** Wyspy nad chmurami częściej są śnieżne.
- **Wyspy oceaniczne według temperatury wody.** Ciepła woda daje dżunglę i namorzyny, lodowata śnieżne plaże.
- **Archipelagi tematyczne.** Każdy klaster ma jeden dominujący biom.
- **Odległość nie zmienia biomów.** Każdy biom może wystąpić blisko spawnu.

## Strefy odległości od spawnu

Jeden współczynnik rośnie płynnie od spawnu do ok. 8000 bloków i tam się zatrzymuje; granice stref są poszarpane szumem, więc w grze nie widać okręgów, tylko świat stopniowo dziczeje.

| Strefa | Odległość (bloki) | Najwyższe wyspy | Najgłębsza woda | Rudy na dnie | Duże wyspy oceaniczne | Gniazda diamentów |
| --- | --- | --- | --- | --- | --- | --- |
| Przystań | 0–1500 | do ok. Y 200 | ok. 65 bloków, bez rowów | Podstawowe, płytkie odsłonięcia | Jedna gwarantowana, z cenotą | Brak |
| Pogranicze | 1500–4000 | do ok. Y 240 | ok. 80 bloków, pierwsze rowy | Bogatsze żyły na ścianach i klifach | Rzadkie | Bardzo rzadkie |
| Dzicz | 4000–8000 | do ok. Y 280 | ok. 95 bloków, głębokie rowy | Duże odsłonięte żyły, bloki surowej rudy | Częstsze, z megakomorami | Rzadkie |
| Kraniec | ponad 8000 | do ok. Y 305 | ok. 105 bloków | Najbogatsze | Najczęstsze | Najczęstsze w świecie |

- **Odległość zmienia:** skrajne wysokości i głębokości, bogactwo rud, częstość dużych wysp oceanicznych i gniazd diamentów. Dalsze wyprawy wymagają lepszych statków i kadłubów, ale bardziej się opłacają.
- **Odległość nie zmienia:** biomów, klimatu, mobów ani częstości vanillowych struktur.
- **Plateau:** po ok. 8000 bloków parametry są stałe, więc nieskończony świat nie przechodzi w absurd.
- Liczby są punktem wyjścia do dostrojenia po locie testowym i pierwszym zanurzeniu łodzią. Podobny mechanizm dla rud stosuje mod [Distant Ores](https://www.curseforge.com/minecraft/mc-mods/distant-ores).

## Start i skala serwera

Serwer ma zwykle 4 graczy, w szczycie do 10; wszyscy startują razem na jednym kontynencie, a spawn powstaje ręcznie po wygenerowaniu świata.

- **Kontynent startowy** na 0,0, ok. 1000–1500 bloków, ze sporym płaskim terenem pod spawn.
- **Gwarancje do ok. 1000 bloków od spawnu:** archipelag, klaster gór Alleluja, ciepła rafa na pierwsze nurkowania, wyspa oceaniczna z zejściem do deepslate oraz jedna duża wyspa z cenotą.
- **Świat nieskończony**, z losowym przeplotem regionów dalej od spawnu.
- **Skala w czasie lotu, nie w blokach.** Na przykład skok między wyspami archipelagu to 1–2 minuty, a lot z kontynentu na kontynent 10–15 minut. Odległości ustalimy po locie testowym.

## Łuk progresji gracza

Każdy etap korzysta z poprzedniego: surowce z nieba budują sprzęt, który otwiera ocean, a ocean daje to, czego potrzeba do lotów wyżej i dalej.

1. **Start na kontynencie.** Drewno, kamień, andezyt, węgiel, żelazo, miedź i cynk wystarczają na podstawy Create.
2. **Drogi powrotne i pierwsze przejścia.** Upadek kończy się w wodzie, a łańcuchy małych wysp i kaskady wodospadów pozwalają wrócić na górę.
3. **Pierwszy statek.** Archipelagi i dolne bryły gór Alleluja.
4. **Płycizny.** Sprzęt do nurkowania z Create (z miedzi), ciepłe rafy, błękitne dziury; alternatywnie zejście cenotą z wyspy oceanicznej.
5. **Łódź z Create Deep Seas.** Głębokie dno, odsłonięte rudy, diamenty, podwodne wulkany.
6. **Twierdza pod dnem.** Droga do Endu.
7. **Mocniejsze statki i kadłuby.** Szczyty gór Alleluja, rowy, gniazda diamentów w korzeniach wysp i dalsze strefy.

## Kompatybilność z vanillą i modami

Generator jest rozszerzeniem vanillowego (własne funkcje gęstości i źródło biomów), a nie silnikiem pisanym od zera; dzięki temu struktury, featury i pregeneracja działają normalnie.

| Mod lub element | Status | Uwagi |
| --- | --- | --- |
| Struktury vanilli i modów | Wymagane | Trójwymiarowe biomy kierują je do właściwej warstwy; struktury o stałym Y przenosimy pod dno |
| [Create Deep Seas](https://www.curseforge.com/minecraft/mc-mods/create-deep-seas) | Docelowy | Łodzie podwodne z ciśnieniem; implozja kadłuba wyznacza oś głębokości |
| Mody biomów przez TerraBlender | Powinny działać | Ich biomy trafiają na wyspy przez wspólną siatkę klimatu |
| Terralith | Konflikt | Podmienia ten sam generator; jego rolę przejmują nasze formacje |
| [YUNG's Better Ocean Monuments](https://modrinth.com/mod/yungs-better-ocean-monuments) | Pasuje | Ma wersję 1.21.1 NeoForge i opcję dopasowania głębokości oceanu i offsetu monumentu ([źródło](https://blog.curseforge.com/best-minecraft-ocean-mods/)) |
| [Alex's Caves](https://www.curseforge.com/minecraft/mc-mods/alexs-caves-unofficial-port) | Odradzany | Na 1.21.1 tylko nieoficjalne porty; wpina się w źródło biomów i potrzebuje dużo więcej miejsca pod ziemią |
| Aquamirae | Niedostępny | Nie znalazłem wersji na 1.21.1 |
| Pregeneracja i wydajność | Wymagane | Priorytet implementacji: szybka, deterministyczna generacja |
| Shadery | Bez wpływu | Mają własne chmury; generator od nich nie zależy |

## Parametry do strojenia i wymagania implementacji

Nieznane liczby nie blokują implementacji: generator wystawia je w konfiguracji, a ich dobór staje się elementem progresji (mocniejszy kadłub albo statek daje lepszą nagrodę).

- **Głębokość dna i rowów.** Dopasowana do progów ciśnienia w Create Deep Seas.
- **Maksymalne wysokości wysp w strefach.** Dopasowane do udźwigu statków z Create Aeronautics.
- **Szerokość korytarzy i przejść.** Pod typowy rozmiar statku.
- **Odległości między wyspami i kontynentami.** Przeliczone z prędkości statku na czasy lotu.
- **Promienie stref i progi bogactwa rud.** Do dostrojenia po pierwszych sesjach.

**Wymagania implementacji:**

- **Wydajność generacji to priorytet.** Świat będzie pregenerowany, a gracze latają daleko, więc generator musi być szybki.
- **Distant Horizons poza zakresem.** Nie wpływa na projekt generatora.

## Źródła

- [Create Deep Seas — CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-deep-seas)
- [YUNG's Better Ocean Monuments — Modrinth](https://modrinth.com/mod/yungs-better-ocean-monuments)
- [Best Minecraft ocean mods — CurseForge Blog](https://blog.curseforge.com/best-minecraft-ocean-mods/)
- [Alex's Caves (Unofficial Port) — CurseForge](https://www.curseforge.com/minecraft/mc-mods/alexs-caves-unofficial-port)
- [Distant Ores — CurseForge](https://www.curseforge.com/minecraft/mc-mods/distant-ores)
- [Minecraft Java Edition 1.18 — informacje o wydaniu (chmury na Y 192)](https://feedback.minecraft.net/hc/en-us/articles/4415128577293-Minecraft-Java-Edition-1-18)
