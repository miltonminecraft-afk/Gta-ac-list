# GTA AC Governor voor Android

Een compacte voortgangsapp voor het GTA V × Assetto Corsa-project. Bekijk lopende taken, eigenaren, bewezen controles, de volgende stap en het logboek van de governor.

De APK verschijnt bij [Releases](https://github.com/miltonminecraft-afk/Gta-ac-list/releases/latest). Android 8 of nieuwer. Er is geen account nodig.

## Voortgang

De governor publiceert `data/status.json` na zijn uurcontrole. De app leest deze openbare feed bij openen, op verzoek en iedere vijf minuten zolang hij op de voorgrond staat. Een nieuwe voortgangsstand vereist geen app-update. De vorige opgehaalde stand blijft offline beschikbaar; een mislukte update overschrijft hem niet. De app toont de datum van de stand en waarschuwt na twee uur. Er zijn geen achtergrondmeldingen of garanties dat een computer die uitstaat nog uurcontroles uitvoert.

Percentages zijn afgeronde controles gedeeld door de vastgelegde controles per taak. Ze meten geen verstreken tijd en vormen geen totaalpercentage van het oorspronkelijke project. 'Opgeleverd' vereist dat alle controles van het betreffende werkpakket af zijn.

## Privacy en bron

Alleen de openbare projectvoortgang en appbron staan hier. De export selecteert toegestane velden en weigert lokale paden, chat-ID's en credential-/e-mailpatronen. Uitvoerende gamebestanden, lokale bewijsbestanden en volledige chatgeschiedenis worden niet gepubliceerd. De app vraagt alleen internettoegang. De interface zit in de APK, neemt data als tekst over en kan geen externe scripts of webpagina's uitvoeren.

## Bouwen

Vereist: JDK 17, Gradle 8.13, Android SDK 35 en Build Tools 35.0.0.

```sh
gradle --no-daemon :app:assembleRelease :app:lintRelease
```

De GitHub-workflow bouwt en controleert de app. Hij levert een **ongesigneerde** release-APK als bouwartifact; dit is niet de installatiedownload. De uiteindelijke APK wordt met een afzonderlijk bewaarde persoonlijke sleutel ondertekend, gecontroleerd en via `dist/` gepubliceerd. Die sleutel komt nooit in Git of workflowartifacts. Gebruik bij volgende versies dezelfde sleutel en verhoog `versionCode`.

## Controles

`python -m unittest discover -s tests -p '*_test.py'` controleert de privacygrens van de export. `node tests/ui.cjs` controleert taken, filters, logboek, ongeldige updates, offline-status en smalle schermen met Playwright. De CI draait Android lint en een instrumentatieproef op Android 8. De APK en certificaatvingerafdruk hebben aanvullende verificatie bij de release.

Documentatie: [Android WebView met lokale inhoud](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content), [Android APK signing](https://developer.android.com/tools/apksigner), [Gradle/AGP-compatibiliteit](https://developer.android.com/build/releases/about-agp).
