# LanQuiz

Quiz a risposta multipla su Android, tablet e iPhone. Nasce dal portale quiz in
Flask (`app.py` + `templates/`) e ne conserva il formato dei file e le
scorciatoie: gli stessi `.txt` funzionano senza toccarli.

## Download

**Android e tablet**: scarica l'APK dalla pagina
[Releases](https://github.com/zeo93/LanQuiz/releases/latest).
L'app controlla da sola gli aggiornamenti all'avvio e propone il download della
nuova versione.

**iPhone, iPad e browser**: usa la web app su **<https://zeo93.github.io/LanQuiz/>** —
su iOS aprila in Safari e scegli *Condividi → Aggiungi alla schermata Home* per
installarla come app. Si aggiorna da sola a ogni apertura con rete e continua a
funzionare offline.

Le due versioni sono indipendenti: ognuna tiene i propri quiz importati e le
proprie statistiche sul dispositivo, senza account e senza server.

## Cosa fa

Rispetto al portale Flask di partenza:

| | Flask | LanQuiz |
|---|---|---|
| Presentazione | tutte le domande in una pagina lunga | una domanda alla volta, con mappa per saltare |
| Riscontro | sempre immediato | **Studio** (immediato) o **Esame** (solo alla consegna) |
| Sessione | 10 / 50 / personalizzato | anche 25, mescolamento disattivabile, timer libero |
| Dopo il quiz | percentuale | esito, ripasso domanda per domanda, condivisione |
| Errori | nessuna memoria | ogni banco ricorda le domande sbagliate: si rifanno solo quelle |
| Segnalibri | — | contrassegna una domanda e riprendila dopo |
| Interruzioni | quiz perso | il quiz interrotto si riprende dalla home |
| Statistiche | — | tentativi, medie, record per banco, storico, export CSV |
| Domande | una risposta esatta | anche più di una, con spiegazione facoltativa |
| Importazione | upload dal browser | file, testo incollato, indirizzo web, o file condiviso da un'altra app |
| Aspetto | tema scuro fisso | chiaro/scuro automatico, layout a due colonne su tablet |
| Accesso | login utente/password | nessuno: i dati non escono dal dispositivo |

La scorciatoia **Prova esame** fa quello che faceva prima: 50 domande e 90
minuti di tempo, e in più passa da sola in modalità esame.

Il login è sparito di proposito: serviva perché il portale era esposto su
internet, mentre qui i quiz e i risultati restano sul telefono.

## Formato dei file

Una domanda per riga, campi separati da punto e virgola — lo stesso di prima:

```
domanda;risposta esatta;risposta errata;risposta errata
```

Estensioni facoltative, tutte compatibili all'indietro:

- `*` davanti a una risposta la marca come corretta, così una domanda può
  averne più di una: `Quali sono pari?;1;*2;3;*4`
- un campo che inizia con `##` è la spiegazione mostrata dopo la risposta:
  `Domanda?;esatta;errata;##Perché lo dice il manuale`
- le righe che iniziano con `#` sono commenti
- separatore: punto e virgola, altrimenti tabulazione, altrimenti virgola —
  così funzionano anche i CSV esportati da un foglio di calcolo
- un file che inizia con `{` o `[` viene letto come JSON

Senza asterischi vale la regola di sempre: **la prima risposta è quella esatta**.
Domande e risposte vengono comunque mescolate a ogni sessione.

## Quiz preinstallati

Gli stessi banchi del portale, in `app/src/main/assets/banks/` per Android e in
`docs/banks/` per la web app (con `docs/banks.json` che ne è l'indice, perché
GitHub Pages non elenca le cartelle):

- Google Engineering — 6 blocchi da 50 domande più il file completo da 300
- Google Leader — idem
- `questions.txt` — il quiz di esempio

Per aggiungerne uno preinstallato basta copiarlo in tutte e due le cartelle e
rigenerare l'indice:

```bash
cd docs && python -c "import os,json; json.dump(sorted(os.listdir('banks')), open('banks.json','w'), indent=2)"
```

Il gruppo mostrato in home si ricava dal nome del file togliendo i suffissi di
numerazione: `Google_Leader_00_full` e `Google_Leader_03` finiscono entrambi in
«Google Leader». Un nome senza famiglia riconoscibile va in «Altri quiz».

## Build

Serve l'SDK Android e Gradle 8.13 o più recente. Il percorso dell'SDK sta in
`local.properties` (non è nel repo):

```bash
gradle assembleRelease
```

L'APK firmato finisce in `app/build/outputs/apk/release/`. I test del lettore
dei banchi girano sulla JVM, senza emulatore:

```bash
gradle testReleaseUnitTest
```

## Pubblicare una versione

```powershell
.\rilascia.ps1 1.1 "Cosa cambia in questa versione"
```

Lo script alza la versione in `app/build.gradle` e in `docs/app.js`, compila
l'APK firmato, committa, crea il tag e pubblica la Release con l'APK allegato.
Da lì in poi l'app installata propone l'aggiornamento da sola.

In alternativa c'è la GitHub Action `.github/workflows/release.yml`, che fa lo
stesso partendo da un tag `vX.Y`. Vuole tre secret nel repository:

| secret | contenuto |
|---|---|
| `KEYSTORE_BASE64` | `app/release.keystore` codificato in base64 |
| `KEYSTORE_PASSWORD` | password dello store |
| `KEY_PASSWORD` | password della chiave |

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\release.keystore")) | Set-Clipboard
```

## Chiave di firma

`app/release.keystore` **non è nel repo** (vedi `.gitignore`): su un repository
pubblico chiunque potrebbe usarlo per firmare un APK installabile sopra questa
app. Va conservato a parte: senza, non è possibile pubblicare aggiornamenti che
si installino sopra quello già presente.

Dati per la registrazione sulla
[Android Developer Console](https://android.google.com/developerconsole),
obbligatoria dal 2027 anche per le app installate manualmente:

- **Package name**: `com.marco.lanquiz`
- **Impronta SHA-256 della chiave di firma**:
  `D9:8E:71:B3:E4:0B:3A:23:F8:DF:0E:0F:1D:65:BB:4C:BC:0C:30:E8:7A:76:EE:68:90:B1:14:29:F4:1C:32:2A`

La [web app](https://zeo93.github.io/LanQuiz/) non è interessata da questi requisiti.

## Struttura

```
app/src/main/java/com/marco/lanquiz/
  Parser.java  Question.java  Bank.java  Banks.java   lettura dei banchi
  Session.java  Store.java                            motore del quiz e memoria
  MainActivity  QuizActivity  ResultActivity          schermate
  StatsActivity  SettingsActivity
  UpdateChecker.java                                  aggiornamenti da GitHub
app/src/test/java/…                                   test JVM del lettore
docs/                                                 web app (GitHub Pages)
```

Il lettore dei banchi esiste in due copie che devono restare d'accordo:
`Parser.java` per Android e le funzioni `parseBank`/`categoryOf` in
`docs/app.js`. Toccandone una, va toccata anche l'altra.
