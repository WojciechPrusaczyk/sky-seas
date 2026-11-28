# Do poprawy (uwagi z gry)

Lista uwag użytkownika do zrobienia po pomiarach E7 (2026-11-28). Kolejność według ważności.

1. **Moby wodne się nie respią** (najważniejsze). Pod wodą nie ma ryb, kałamarnic ani niczego innego, również z dodatkowego moda na moby wodne. Do sprawdzenia:
   - spawn mobów wodnych w 3D biomach (jaki biom widzi `NaturalSpawner` w wodzie);
   - kategorie `water_ambient` i `water_creature` oraz ich limity;
   - wysokość, na której vanilla szuka miejsca (losowe Y do heightmapy?);
   - tagi biomów spawnu z wpisu E4 „Do weryfikacji”.
   - Zrobione (`WaterMobs`): w świecie Sky Seas moby z kategorii `water_ambient` i `water_creature` (też z modów) mogą się spawnować na każdej głębokości w wodzie z wodą nad sobą; vanilla pozwala tylko w 13 blokach pod poziomem morza, a nasz ocean ma ok. 80.
   - Do zrobienia: woda tuż nad dnem w wąskich kanionach dostaje biom lądowy z sąsiedniej skały (biomy są w komórkach 4×4×4), więc tam spawnu i roślin oceanu nie ma.
2. **Rzeki:**
   - robią zawijasy i nienaturalnie łączą się same ze sobą;
   - kamień przy brzegach (domurowane dziury) tworzy dziwne ściany, a przy zawijasach wygląda jeszcze gorzej: usunąć ten kamień;
   - na zakręcie przy zmianie wysokości terenu dzieją się dziwne rzeczy;
   - nie zawsze jedna nitka: czasem może się rozdzielić (rozwidlenie).
3. **Cherry grove** generuje się zawsze bardzo mały: ma być nieco większy.
4. **Za dużo zimowych biomów**, ogólnie i na wyższych partiach wysp: zmniejszyć szansę.
5. **Aquamirae:** jego biom pojawia się bardzo często i jest zawsze bardzo duży.

Znane problemy z E6 (twierdze nad rowami, wioski przy krawędzi, miasta wystające ze zbocza) są w `docs/status-e6.md`.
