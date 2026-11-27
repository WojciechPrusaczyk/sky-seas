# Decyzja 2026-11-27: bez własnych biomów dna

**Kontekst.** Design i spec zakładały cztery własne biomy dna z datapacka (pola wulkaniczne, las kelpu, skalne pustkowie, głębia) i nakładkę na źródło biomów w warstwie oceanu (E4). Przy weryfikacji okazało się, że opakowanie `multi_noise` wyłącza TerraBlendera, a podklasa wymaga access transformera.

**Decyzja (użytkownik).** Mod nie dodaje żadnych biomów. Opisy „biomów dna” w designie opisywały charakter regionów, a nie biomy. Regiony dna to formy moda, tak jak wyspy i wulkany: prowincje dna z E3 (`SeabedProvinces`: wzgórza i pasma, wąwozy, pola stożków, tarasy, skaliste grzbiety z filarami, rzadkie równiny). Biomy wszędzie zostają vanillowe (i modów przez TerraBlender).

**Skutki.**
- Odpadają: pliki biomów, tagi, nakładka na źródło biomów, access transformer; punkty „Do weryfikacji” o synchronizacji biomów z datapacka, nakładce i tagu mgły nie są już potrzebne.
- Brak osobnego koloru wody, mgły i mobów regionów dna; woda i moby wynikają z vanillowego biomu oceanicznego (temperatura).
- Charakter regionów (basalt i magma w polach stożków, kelp, glow lichen w wąwozach, goła skała) daje E5 przez reguły powierzchni i featury wybierane po prowincji, nie po biomie.
- E4 zostaje: klimat 3D (continents, erozja, chłodzenie z wysokością, klastry tematyczne, ciepła rafa przy spawnie) oraz poprawki błędów pod ziemią.
