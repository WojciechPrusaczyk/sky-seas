# Sky Seas — generator świata (NeoForge 1.21.1)

Serwerowy mod do Minecrafta 1.21.1 (NeoForge), który generuje świat latających wysp nad głębokim oceanem dla prywatnego serwera z Create Aeronautics i Create Deep Seas. Robocza nazwa: Sky Seas, mod id: `skyseas`.

## Dokumenty — przeczytaj przed pracą

- `docs/design.md` — CO ma powstać (game design). Źródło prawdy dla wyglądu świata i rozgrywki.
- `docs/spec.md` — JAK to zbudować (architektura, etapy E0–E9, wydajność, testy, ograniczenie dystrybucji). Źródło prawdy dla implementacji.
- Gdy dokumenty sobie przeczą albo coś jest niejasne: zatrzymaj się i zapytaj. Nie zgaduj.
- Schematy z wersji online nie przeszły do Markdowna; tekst opisuje wszystko, co potrzebne.

## Sposób pracy

- Pracujemy etapami E0–E9 z `docs/spec.md` (sekcja „Plan wdrożenia”), po kolei. Kolejny etap zaczynamy dopiero, gdy poprzedni spełnia kryteria akceptacji.
- Początek etapu: przeczytaj odpowiednie sekcje spec, sprawdź w kodzie Minecrafta/NeoForge punkty z „Do weryfikacji” przypisane do tego etapu, przedstaw krótki plan i poczekaj na moją akceptację.
- W trakcie etapu pracujemy na gałęzi `e<N>-wip` z commitami po drodze; merge na `main` dopiero po mojej akceptacji całego etapu. Etap może być podzielony na rundy; każda kończy się mapą, benchem i moim testem w grze, a przed testem wypisz, czego w świecie jeszcze nie ma.
- Koniec etapu: `./gradlew test`, GameTesty (jeśli istnieją), pomiar `/skyseas bench` (od E1), krótki raport: co zrobione, wyniki pomiarów, odstępstwa od spec. Potem commit i tag `e<N>-done`.
- Nie zgaduj API Minecrafta ani NeoForge. Sprawdzaj w zdekompilowanym kodzie (Parchment) lub w dokumentacji NeoForge.
- Małe, logiczne commity.

## Zasady nadrzędne (skrót z spec)

1. **Wydajność przede wszystkim.** Zero alokacji w `compute()` i `fillArray`; bez streamów, lambd, boxingu i iteratorów kolekcji w gorących ścieżkach; cache 2D (`cache_2d`, `flat_cache`), `interpolated` dla dużych form, szybkie wyjścia po zakresie Y.
2. **Determinizm.** Ten sam seed i parametry dają te same chunki, niezależnie od liczby wątków i kolejności generacji. Żadnego globalnego stanu zależnego od kolejności.
3. **Rozszerzamy vanillę.** `NoiseBasedChunkGenerator` + `multi_noise` z presetem `minecraft:overworld`. Nie piszemy generatora od zera.
4. **Tylko serwer.** Żadnych bloków, itemów, encji, pakietów sieciowych ani wpisów w rejestrach, które wymagają moda po stronie klienta. Żadnych własnych biomów (decyzja 2026-11-27): regiony dna to formy moda, nie biomy.
5. **Testowalność.** Pakiety `pl.skyseas.layout` i `pl.skyseas.shape` nie importują klas Minecrafta i mają testy JUnit.
6. **Mixiny w ostateczności.** Każdy mixin uzasadnij w komentarzu i zapytaj przed dodaniem.

## Polecenia

- `./gradlew build` — jar w `build/libs/`
- `./gradlew test` — testy JUnit
- `./gradlew runServer` — serwer deweloperski w `run/` (`online-mode=false`, `level-type=skyseas:sky_seas`)
- `./gradlew runClient` — klient deweloperski (konto offline)
- `./gradlew runGameTestServer` — GameTesty

## Konwencje

- Java 21, pakiet bazowy `pl.skyseas`.
- Kod, nazwy, komentarze i commity po angielsku; rozmowa ze mną po polsku.
- Commity z prefiksem etapu, np. `E1: add seabed height density function`.

## Nigdy

- Nie commituj kluczy prywatnych licencji. `tools/license-signer/keys/`, `*.key` i `*.pem` trafiają do `.gitignore` już w E0.
- Nie dodawaj zdalnego repozytorium i nie pushuj bez mojej zgody. Repozytorium jest prywatne.
- Nie zmieniaj wysokości świata (Y −64…320) ani poziomu morza (63).
- Nie zmieniaj wyniku generacji bez podbicia `generatorVersion`.
- Nie dodawaj zależności bez pytania. Dozwolone od startu: NeoForge, Lithostitched, opcjonalnie Create; do testów JUnit 5.

## Środowisko

- openSUSE Tumbleweed, zsh, systemowy JDK 21; AMD Ryzen 7 9800X3D, ok. 32 GB RAM.
- Gradle: `org.gradle.jvmargs=-Xmx4G` w `gradle.properties`; serwer deweloperski z `-Xmx8G` (pregeneracja i benchmarki).
- IntelliJ IDEA może działać z Flatpaka (sandbox). Polecenia Gradle uruchamiaj w terminalu systemu i nie zakładaj, że IDE widzi narzędzia systemowe.
