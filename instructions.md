# Progetto: App Android nativa per Homebox ("HomeboxDroid")

> File di avvio progetto. Chi legge questo documento (umano o una chat Claude
> vuota) deve poter capire scopo, contesto tecnico e primi passi senza altre
> fonti. Non contiene codice: e' la guida da consultare prima di scrivere codice.

Ultimo aggiornamento ricerca: 9 novembre 2026.

---

## 1. Obiettivo

Sviluppare un'applicazione **Android nativa** (non PWA, non wrapper WebView) che
si colleghi a un server **Homebox self-hosted** e offra una gestione completa
dell'inventario con un'interfaccia pensata per Android: semplice ma completa,
veloce, usabile con una mano, con scansione codici e gestione foto fluida.

Principi guida:
- Parita' funzionale con Homebox web: tutto cio' che fa il web si deve poter
  fare dall'app.
- Interfaccia **ripensata** per mobile, non trasposta dal web. Niente menu
  densi: navigazione a tab, ricerca in primo piano, azioni rapide.
- **Offline-first ragionevole**: consultazione fluida anche con rete lenta o
  assente; scrittura che si sincronizza quando il server torna raggiungibile.
- Rispetto della natura self-hosted: nessun servizio cloud di terzi, nessuna
  telemetria verso l'esterno, i dati restano tra telefono e server dell'utente.

Fuori scopo (per ora): client iOS, funzioni server-side, modifiche al backend
Homebox.

---

## 2. Contesto: cos'e' Homebox e quale versione puntare

Homebox e' un sistema di inventario per uso domestico/homelab, open source,
scritto in Go con UI web Vue. Gira in un singolo container Docker (SQLite o
Postgres). Licenza **AGPL-3.0**.

- Repository attivo e mantenuto: **sysadminsmedia/homebox** (il vecchio
  hay-kot/homebox e' deprecato; ignorarlo).
- Docs: homebox.software | Demo: demo.homebox.software | Discord:
  discord.homebox.software
- Immagine Docker: `ghcr.io/sysadminsmedia/homebox:latest`

### Versione da puntare (DECISIONE ARCHITETTURALE IMPORTANTE)

A novembre 2026 l'ultima stabile e' la serie **v0.26.x** (es. v0.26.2). La
v0.26.0 ha introdotto il cosiddetto **"entity merge"**, il cambiamento piu'
grosso mai fatto all'API:

- Gli endpoint separati `/api/v1/items/*` e `/api/v1/locations/*` sono stati
  **rimossi** e sostituiti da un unico `/api/v1/entities/*`. Item e location
  sono ora la stessa struttura "entity", distinta da un tipo di entita'
  definibile. La gerarchia si esprime con `parentId`.
- Sono state aggiunte le **API Key statiche** per integrazioni (vedi sezione
  auth): questo e' il modo migliore per autenticare un'app nativa.

**DECISIONE PRESA: l'app supporta esclusivamente Homebox v0.26.0+ (modello
entities).** Niente compatibilita' col vecchio modello items/locations: si usano
da subito le API nuove e si evitano migrazioni future. Resta solo da allineare i
modelli alla versione 0.26.x esatta dell'istanza, leggendo la spec (sezione 11).

Cronologia utile delle feature recenti (per capire cosa deve coprire l'app):
- v0.23.0: **Collections** (multi-collezione/tenant), rimozione supporto 32-bit.
- v0.25.0: **tag con relazioni padre/figlio e icone**, **quantita'
  frazionarie/decimali** (es. 1.2), impostazioni utente salvate nel DB,
  OpenTelemetry.
- v0.26.0: **entity merge**, **API Key statiche**, **reset password** (via
  SMTP), **export/import ZIP** per collezione, **scanner "AR"** (inquadri un QR
  e compare un riquadro con info), gestione **entity types**.

---

## 3. API del server: riferimento operativo

### 3.1 Regola d'oro: la spec OpenAPI dell'istanza e' la fonte di verita'

Prima di scrivere qualunque data layer, **scaricare e leggere la spec OpenAPI
dall'istanza reale dell'utente**, perche' l'esatto set di endpoint dipende dalla
versione installata. Percorsi:
- Swagger UI in esecuzione: `https://<istanza>/swagger/`
- Spec JSON nel repo:
  `https://raw.githubusercontent.com/sysadminsmedia/homebox/main/docs/en/api/openapi-3.0.json`
  (se il percorso cambia, cercarla sotto `docs/.../api/` nel repo; esiste anche
  una variante `openapi-2.0.json`)
- Esiste un SDK Python tipizzato generato dalla spec (pacchetto PyPI
  `homebox-client`): utile come **mappa di riferimento** di modelli ed endpoint
  anche lavorando in Kotlin.

Azione concreta per la chat che parte: chiedere l'URL dell'istanza e la
versione, poi recuperare la spec e generare da li' i modelli dati.

### 3.2 Base path e formato

- Tutte le chiamate sotto `/api`. Gli endpoint versione 1 sono `/api/v1/...`.
- Risposte JSON. Liste con paginazione/filtri via query param.

### 3.3 Autenticazione (due strade, scegliere API Key come primaria)

**Strada A - API Key statica (CONSIGLIATA per l'app):**
- Da v0.26.0 Homebox genera API Key statiche per developer/integrazioni.
- Ogni chiave eredita il livello di accesso dell'utente che la crea.
- Tutte le chiavi hanno prefisso `hb_` (aiuta a non committarle per sbaglio).
- Vantaggio enorme per un'app nativa: niente ciclo login/refresh del token da
  gestire; la chiave e' longeva. L'utente la genera una volta dalla UI web e la
  incolla (o la passa via QR) nell'app.
- Verificare sulla spec l'endpoint esatto per creare/listare/revocare chiavi
  (probabilmente sotto `/api/v1/users/...` o simile) nel caso si voglia gestirle
  anche dall'app.
- **Decisione presa**: l'API Key e' il metodo PRIMARIO dell'app. La UI di login
  (vedi sezione 8) chiede URL + API Key, con scansione QR, e offre un pulsante
  dedicato per passare al flusso email/password.

**Setup server-side per le API Key: `HBOX_AUTH_API_KEY_PEPPER`.**
Per usare le API Key l'admin deve aver configurato questa variabile. Dalla
v0.26 e' richiesta: senza, Homebox non parte. E' un "pepper" segreto usato per
hashare le chiavi. Si imposta come variabile d'ambiente nel `docker-compose.yml`
(sezione `environment:`), non nell'app:

```yaml
services:
  homebox:
    image: ghcr.io/sysadminsmedia/homebox:latest
    container_name: homebox
    restart: unless-stopped
    environment:
      - HBOX_AUTH_API_KEY_PEPPER=${HBOX_API_KEY_PEPPER}   # da file .env, non inline
      - HBOX_OPTIONS_ALLOW_REGISTRATION=false             # verificare nome esatto nella doc config
      - TZ=Europe/Rome
    volumes:
      - ./data:/data
    ports:
      - "3100:7745"   # host:container, la porta interna e' 7745
```

Regole pratiche sul pepper:
- Generarlo lungo e casuale, es. `openssl rand -base64 48`.
- Trattarlo come segreto: metterlo in un file `.env` referenziato dal compose,
  non scriverlo inline in un compose committato, mai in un repo pubblico.
- NON cambiarlo dopo: cambiarlo invalida tutte le API Key gia' emesse (sono
  hashate con lui), costringendo a rigenerarle.
- Dopo averlo impostato e riavviato il container, l'utente genera la chiave
  `hb_...` dalla UI web e la incolla (o la passa via QR) nell'app.

**Strada B - Login email/password (da supportare come alternativa):**
- `POST /api/v1/users/login` (no auth) -> restituisce un token Bearer a vita
  breve.
- `GET /api/v1/users/refresh` (Bearer) per rinnovare il token.
- `POST /api/v1/users/logout` (Bearer).
- In tutte le chiamate autenticate: header `Authorization: Bearer <token>`.
- L'app dovra' gestire scadenza e refresh trasparente (interceptor).

**Collections / multi-tenant:** le collezioni (dette anche tenant) possono
essere selezionate tramite un header dedicato lato Homebox (riscontrato come
`X-Tenant`). Da verificare sulla spec dell'istanza come si seleziona la
collezione attiva. L'app deve permettere di scegliere la collezione se l'utente
ne ha piu' di una.

### 3.4 Gruppi di endpoint (v0.26+, da confermare sempre sulla spec)

Base / stato (no auth):
- `GET /api/v1/status` - info e stato applicazione.
- `GET /api/v1/currency` (o `/currencies`) - valute supportate.

Utenti / auth: register, login, refresh, logout, self (lettura/modifica
profilo), change-password, reset password (nuovo in 0.26).

Entities (cuore dell'app, sostituiscono items+locations):
- `GET /api/v1/entities` - lista con ricerca, filtri, paginazione.
- `POST /api/v1/entities` - crea (campo `parentId` per la gerarchia).
- `GET /api/v1/entities/{id}` - dettaglio.
- `PUT /api/v1/entities/{id}` - update completo.
- `PATCH /api/v1/entities/{id}` - update parziale (es. quantita', parent, tag).
- `DELETE /api/v1/entities/{id}` - elimina.
- `POST /api/v1/entities/{id}/duplicate` - duplica.
- `GET /api/v1/entities/{id}/path` - percorso gerarchico completo.
- `GET /api/v1/entities/tree` - albero location/item (per la navigazione).

Entity types (nuovo): CRUD su `/api/v1/entity-types`. Permette anche di
convertire un item in location e viceversa.

Allegati: endpoint sotto l'entita', del tipo
`/api/v1/entities/{id}/attachments` e `/attachments/{attachment_id}` per
caricare/scaricare/eliminare foto, manuali, documenti. ATTENZIONE: storicamente
qui ci sono stati bug di autorizzazione e un XSS sugli upload (vedi sicurezza);
gestire con cura content-type e autorizzazione.

Manutenzione: log di manutenzione legati all'entita' (ora sotto entities).

Label/Tag: CRUD su `/api/v1/labels` (+ `/{id}`). Dalla 0.25 i tag hanno
gerarchia padre/figlio e icone: il data model deve prevederlo.

Gruppi: `/api/v1/groups` per info gruppo, inviti, e **statistiche**
(`/groups/statistics`, per label, per location, valore d'acquisto totale), utili
per una dashboard.

Notifiers: `/api/v1/notifiers` (CRUD + test) per le notifiche configurate
server-side. Nota sicurezza: in passato i notifier permettevano SSRF; non e'
codice dell'app ma da tenere presente.

Reporting: `GET /api/v1/reporting/bill-of-materials` - export CSV distinta.

Utility: `GET /api/v1/qrcode` - genera QR per un'entita'. Esiste anche una
ricerca prodotto da codice a barre (integrazione tipo upcitemdb/barcode lookup):
verificare il percorso esatto sulla spec, serve per il flusso "scansiona e
precompila".

Import/export: da v0.26 export/import di un'intera collezione in ZIP (incluse
foto, tag, entita'); utile ma secondario per l'app.

### 3.5 Note di sicurezza da non ignorare

- Homebox ha avuto diverse CVE risolte nelle versioni recenti (controllo
  autorizzazione su update/delete allegati < 0.20.1; XSS su upload allegati <
  0.24.0-rc.1; SSRF via notifier; spoofing X-Real-IP per il rate limit). Non
  sono bug dell'app, ma: (a) consigliare all'utente una versione aggiornata,
  (b) l'app non deve introdurre superfici nuove (validare input, non eseguire
  HTML da campi liberi, non fidarsi ciecamente degli URL negli allegati).
- Le API Key `hb_...` e i token sono **segreti**: vanno in Android Keystore /
  EncryptedSharedPreferences, mai in log, mai in backup chiaro, mai in URL.

---

## 4. Parita' funzionale: checklist di cosa l'app deve coprire

Must-have (MVP + completezza):
- **Build generica, nessun dato hardcoded**: l'APK non e' compilato per un
  dominio specifico. Al primo avvio, senza account/server/API configurati, parte
  l'onboarding che chiede i dati (vedi sezione 8).
- Connessione a istanza self-hosted: URL server, scelta auth (API Key o
  email/password), test connessione, gestione certificati (incluso self-signed
  su rete locale - vedi sezione 7).
- Browse/navigazione ad **albero** di location ed entita' (tree + path).
- Ricerca e **filtri** (per testo, tag/label, location, campi).
- Dettaglio entita' completo: campi standard (nome, descrizione, quantita'
  anche decimale, note), campi acquisto (data, prezzo, luogo), garanzia
  (scadenza), numero seriale, asset id, custom fields.
- **CRUD completo** su entita', location, tag/label, entity types.
- **Allegati**: scattare foto dalla fotocamera, caricare da galleria, caricare
  PDF/manuali, vedere/scaricare/eliminare allegati.
- **Scansione**: lettura QR/barcode per aprire un'entita' o per creare un item
  precompilato tramite barcode lookup. Generazione/stampa QR dell'entita'.
- **Manutenzione**: vedere e aggiungere log di manutenzione.
- **Collezioni**: selezionare la collezione attiva se l'utente ne ha piu' d'una.
- **Statistiche/dashboard**: numero oggetti, valore totale, per label/location.

Nice-to-have (fasi successive):
- Promemoria/notifiche locali per scadenze garanzia e manutenzioni (lato app,
  con WorkManager, indipendenti dai notifier server).
- Modalita' "AR"/scan continuo stile scanner Homebox.
- Export/import ZIP collezione.
- Widget home screen, quick-add da share sheet, scorciatoie.
- Modalita' offline di scrittura con coda di sync.

---

## 5. Stack tecnico Android consigliato

- **Linguaggio**: Kotlin.
- **UI**: Jetpack Compose + Material 3 (dynamic color opzionale). Niente XML
  layout salvo necessita'.
- **Navigazione**: Navigation Compose. Struttura a bottom navigation (es.
  Sfoglia, Cerca, Scansiona, Impostazioni) + schermate di dettaglio.
- **Networking**: Retrofit + OkHttp (interceptor per auth, logging, retry) con
  kotlinx.serialization o Moshi. In alternativa Ktor Client se si preferisce
  multiplatform-friendly. OkHttp aiuta per la gestione certificati self-signed.
- **Modelli**: generare i DTO dalla spec OpenAPI (es. openapi-generator) e poi
  mappare su modelli di dominio puliti.
- **Persistenza/offline**: Room per cache locale di entita', tree, tag;
  DataStore (Preferences) per impostazioni; Android Keystore +
  EncryptedSharedPreferences per segreti (API Key/token/URL).
- **Immagini**: Coil (Compose-native), con auth header per gli allegati.
- **DI**: Hilt.
- **Async**: Coroutines + Flow.
- **Scansione**: CameraX + ML Kit Barcode Scanning (QR + codici a barre,
  on-device, offline, nessun servizio cloud).
- **Sync/background**: WorkManager (sync periodica, coda scritture offline,
  promemoria garanzie).
- **Architettura**: MVVM + Repository. Repository come unica fonte di verita'
  che decide tra cache Room e rete. UDF (stato immutabile esposto via
  StateFlow).
- **Testing**: unit test su repository/mapper; MockWebServer per l'API;
  test Compose per le schermate chiave.
- **minSdk 26 (Android 8.0)**, targetSdk ultima disponibile. Copre comodamente
  il Galaxy S23 Ultra (device di test principale), i Galaxy degli ultimi ~5 anni
  e i telefoni economici, pur permettendo API moderne. Testare sia su schermo
  grande ad alta densita' (S23 Ultra) sia, idealmente, su un profilo piu'
  modesto per verificare che scali bene.

---

## 6. Architettura e strategia offline

- Layer: UI (Compose) -> ViewModel -> Repository -> (RemoteDataSource Retrofit |
  LocalDataSource Room).
- Lettura: sempre dalla cache Room, aggiornata in background dalla rete
  (stale-while-revalidate). L'app resta usabile offline in consultazione.
- Scrittura: tentativo online immediato; se offline, accodare l'operazione
  (tabella "pending ops" in Room) e riprovare con WorkManager quando torna la
  rete. Gestire i conflitti in modo semplice (last-write-wins con avviso), senza
  sync bidirezionale complessa all'inizio.
- Allegati: upload in background con WorkManager (file grandi, rete lenta).
- Attenzione: Homebox usa token a vita breve (strada B). Con le API Key (strada
  A) la sync in background e' molto piu' semplice perche' la credenziale non
  scade: motivo in piu' per preferire le API Key.

---

## 7. Requisiti non funzionali e vincoli self-hosted

- **Rete**: l'istanza puo' essere raggiungibile solo in LAN, via reverse proxy
  con dominio, o via VPN (es. il tipico setup homelab). L'app non deve assumere
  internet pubblico. Prevedere URL base configurabile con http/https e porta.
- **Reverse proxy + Authelia (setup reale dell'utente)**: l'utente usa Nginx
  Proxy Manager con Authelia (forward-auth, 2FA) davanti a Homebox. Punto
  chiave: **l'app si autentica a Homebox con la propria API Key, NON ad
  Authelia**. Authelia ha un modello a sessione/cookie e non sa validare le
  chiavi `hb_`: se restasse a protezione dell'intera app, le chiamate verrebbero
  intercettate e rimbalzate al portale di login. Soluzione corretta: nelle
  regole `access_control` di Authelia fare **bypass sui percorsi API** (es.
  resource `^/api/.*$`) e mantenere **policy `two_factor` sul resto** (la UI
  web). Cosi' l'app arriva diretta a Homebox, che la autentica con la sua API
  Key, mentre la pagina web resta dietro 2FA. Poiche' `/api` viene esposto "in
  chiaro" rispetto ad Authelia, va blindato lato Homebox: registrazione
  disabilitata, rate limiting attivo, auth Homebox obbligatoria (gli endpoint
  pubblici tipo `/status` e `/login` sono pochi e a basso rischio). Alternativa
  piu' pulita: un hostname separato per l'API (es. `homebox-api.dominio`) senza
  Authelia, tenendo Authelia solo sull'hostname della UI; entrambi puntano alla
  stessa istanza. Con NPM davanti, assicurarsi che Homebox riceva l'IP reale
  (X-Forwarded-For/Real-IP) per rate limiting e log corretti.
- **Certificati**: con NPM + dominio l'utente ha quasi certamente certificati
  validi (Let's Encrypt), quindi la gestione di TLS self-signed e' a priorita'
  bassa. Resta utile prevederla come opzione (opt-in, con avviso chiaro) per
  accesso diretto in LAN o CA interne. Non disabilitare mai la verifica TLS in
  modo globale e silenzioso.
- **Privacy**: zero telemetria di terzi, zero analytics cloud. Tutto resta
  utente<->server.
- **Segreti**: Keystore/EncryptedSharedPreferences; escludere dal backup
  automatico i segreti; niente segreti nei log.
- **Accessibilita' e i18n**: stringhe esternalizzate, pronto per italiano e
  inglese (Homebox stesso e' multilingua via Weblate).

### 7.1 Config Authelia (scelta: dominio unico `box.stevej.it`)

Istanza esposta su `https://box.stevej.it/`. Le API stanno sullo stesso dominio
sotto `/api/...`. Regola nelle `access_control.rules` di Authelia
(configuration.yml): **bypass su `/api`, prima** della regola two_factor sul
resto. L'ordine conta: le regole si valutano dall'alto e vince la prima che
combacia, quindi il bypass deve stare SOPRA.

```yaml
access_control:
  default_policy: deny
  rules:
    # 1) API raggiungibili dall'app senza Authelia (autentica Homebox con API Key)
    - domain: box.stevej.it
      resources:
        - '^/api/.*$'
      policy: bypass

    # 2) Tutto il resto (UI web) resta dietro 2FA
    - domain: box.stevej.it
      policy: two_factor
```

Note:
- Se preferisci essere ancora piu' stretto puoi usare `^/api(/.*)?$`. Non serve
  restringere ai singoli endpoint: l'app usa l'intera superficie `/api/v1`.
- Poiche' `/api` e' esposto in chiaro rispetto ad Authelia, blindarlo lato
  Homebox: registrazione disabilitata, rate limiting attivo (gia' nel compose
  della sezione 3.3). Gli unici endpoint pubblici per natura (`/status`,
  `/login`) sono pochi e a basso rischio.
- Verificare che NPM passi l'IP reale a Homebox (X-Forwarded-For/Real-IP) cosi'
  rate limiting e log restano corretti.

**Dominio unico vs API su host separato (valutazione sicurezza).**
Per un uso personale a utente singolo, **il dominio unico con bypass stretto e
ben ordinato e' sufficiente e piu' semplice** (un cert, un proxy host): va bene
cosi'. L'host separato (es. `api.box.stevej.it` senza Authelia, UI su
`box.stevej.it` con 2FA) non riduce di per se' l'esposizione delle API, che in
entrambi i casi restano protette solo da Homebox. Diventa piu' sicuro **solo se**
sfrutti la separazione per restringere ulteriormente l'host API (es. solo via
VPN/Tailscale, o con una access list dedicata) - ma questo complicherebbe la
raggiungibilita' dell'app da fuori casa. Verdetto: resta sul dominio unico; passa
all'host separato solo se in futuro vuoi mettere le API dietro VPN.

---

## 8. Direzione UX e stile

### Principio
Semplice ma completa: le azioni comuni (cerca, scansiona, aggiungi) a portata di
pollice; le funzioni avanzate accessibili ma non invadenti. L'interfaccia non
deve mai risultare "macchinosa" o "estremamente piena": dati chiari e precisi,
molto respiro, poco rumore.

### Stile visivo (richiesta dell'utente)
- Design moderno, **morbido e sfumato, non pesante**. NON riusare il CSS di
  NexOS impiegato in altri progetti; qui si parte puliti con Material 3.
- Superfici morbide: angoli arrotondati, ombre leggere, eventuali gradienti
  delicati, palette tenue. Niente interfacce dense o cariche.
- Material 3 (Compose), tema chiaro/scuro, dynamic color opzionale.

### Navigazione
- **Sidebar/drawer a scorrimento dal lato** come navigazione principale, invece
  di una bottom bar pesante. Si apre con swipe dal bordo.
- **Pulsante in alto per aprire il drawer che si nasconde allo scroll** (appare
  quando si scorre verso l'alto, scompare scorrendo verso il basso), per lasciare
  spazio ai contenuti.
- Breadcrumb per la navigazione nell'albero delle location.

### Creazione/modifica
- Per creare un oggetto/luogo: valutare **dialog in sovraimpressione** (modale)
  per inserimenti rapidi, oppure una **sezione/schermata dedicata** per form piu'
  lunghi. Scegliere in base alla quantita' di campi; l'importante e' che resti
  leggero e non affollato.

### Flussi chiave da curare
1. **Primo avvio (requisito esplicito)**: l'APK e' generico, NON compilato per un
   dominio specifico. Al primo avvio, non trovando ne' account ne' server ne' API
   configurate, l'app mostra l'onboarding: schermata che chiede URL server + API
   Key, con pulsante "Scansiona QR" per acquisire URL e chiave da un QR, e un
   pulsante dedicato "Accedi con email e password" per il flusso alternativo
   (URL + email + password). Poi test connessione -> scelta collezione.
2. **Gestione completa passo-passo** (priorita' di sviluppo, vedi sezione 9):
   login -> creazione del primo luogo/location -> creazione del primo oggetto ->
   e cosi' via, in modo che l'utente possa testare incrementalmente riempiendo da
   zero il database.
3. **Aggiunta rapida oggetto**: scansiona barcode -> lookup -> precompila ->
   foto -> salva, in pochi tap.
4. **"Dove si trova X"**: ricerca -> dettaglio -> percorso/location.
5. Browse ad albero delle location con breadcrumb.

---

## 9. Piano a milestone

- **M0 - Setup**: repo su **GitHub (utente Stevejohnny99)**, scaffolding
  progetto, GitHub Actions che a ogni build produce un **APK debug firmato e
  scaricabile** come artifact, lettura spec OpenAPI dell'istanza, minSdk 26 /
  targetSdk ultima.
- **M1 - Connessione e auth**: schermata connessione, API Key + login
  email/password, storage sicuro, test connessione, gestione TLS self-signed.
- **M2 - Lettura/browse + prime creazioni**: lista e dettaglio entita', albero
  location, ricerca e filtri, cache Room. NOTA: il DB parte vuoto, quindi la sola
  consultazione non basta a testare; portare avanti M2 e M3 insieme, dando
  priorita' alla creazione cosi' c'e' subito qualcosa da sfogliare (login ->
  primo luogo -> primo oggetto).
- **M3 - CRUD completo**: creare/modificare/eliminare entita', location, tag,
  entity types; quantita' decimali, custom fields, campi garanzia/acquisto.
- **M4 - Allegati e foto**: fotocamera, upload/download/elimina allegati, upload
  in background.
- **M5 - Scansione**: QR/barcode per aprire e per creare da lookup; generazione
  QR entita'.
- **M6 - Offline write + sync**: coda operazioni, WorkManager, gestione
  conflitti base.
- **M7 - Extra e rifinitura**: dashboard/statistiche, promemoria garanzie,
  collezioni multiple, polish UX, accessibilita', i18n.

Per ogni milestone: testare contro l'istanza reale dell'utente e, se possibile,
contro demo.homebox.software.

**Consegna delle build di test (richiesta esplicita dell'utente)**: a ogni
versione di prova, fornire direttamente il file `.apk` gia' compilato e pronto
da scaricare e installare sul telefono, senza passaggi intermedi (niente "compila
tu"). Usare un APK debug firmato con una chiave di debug stabile cosi' gli
aggiornamenti si installano sopra il precedente. L'utente deve solo abilitare
"installa da sorgenti sconosciute" la prima volta.

---

## 10. Rischi e insidie note

- **Drift dell'API tra versioni**: items/locations vs entities. Mitigazione:
  generare i modelli dalla spec dell'istanza, non da memoria; isolare l'API in
  un layer sostituibile.
- **Nomi "Homebox" gia' usati** su Play Store (es. marchi di self-storage, e app
  terze come "Manager for Homebox" com.homebox.eu, non affiliate): scegliere
  nome/package distinti per evitare confusione e collisioni.
- **AGPL-3.0 (non bloccante ora)**: l'app non sara' pubblicata su Play Store nel
  breve periodo, quindi la licenza non e' un problema immediato. Tenerne conto
  solo se in futuro la si distribuisse come APK fanmade: l'app client e' comunque
  un'opera separata, ma se si riusa codice del repo Homebox valgono gli obblighi
  copyleft.
- **Allegati e sicurezza**: non eseguire/renderizzare contenuti non fidati dagli
  allegati; validare content-type.
- **Token a vita breve**: se si usa login email/password, curare bene il refresh
  per non sloggare l'utente di continuo. Preferire API Key.
- **Certificati self-signed**: gestirli bene o l'app sara' inutilizzabile in
  molti homelab.

---

## 11. Decisioni confermate

Tutte le domande di avvio sono chiuse. Non ridiscutere questi punti.

- **Istanza**: `https://box.stevej.it/`. Immagine `ghcr.io/sysadminsmedia/homebox:latest`.
- **Versione target**: solo Homebox v0.26.0+ (modello entities). Nessuna
  retrocompatibilita' col vecchio modello items/locations.
- **Auth**: API Key come metodo primario; login con URL + API Key + scansione
  QR, e pulsante dedicato per il fallback email/password.
- **Build generica**: nessun dato hardcoded per il dominio; primo avvio =
  onboarding (sezione 8).
- **Esposizione**: NPM + Authelia (2FA), **dominio unico** `box.stevej.it` con
  bypass Authelia su `/api` e two_factor sul resto (config in sezione 7.1).
- **Collezioni**: si parte con una sola (DB vuoto, nessun dato pregresso), ma
  l'app deve supportare piu' collezioni fin da subito.
- **Device target**: minSdk 26; device di test principale Galaxy S23 Ultra;
  deve girare su Galaxy degli ultimi ~5 anni e su telefoni economici.
- **Repo e CI**: GitHub, utente **Stevejohnny99**, con GitHub Actions che
  produce l'APK scaricabile.
- **Licenza/distribuzione**: niente Play Store nel breve; AGPL non bloccante ora.
- **Build di test**: consegnare sempre l'`.apk` gia' compilato e pronto.
- **Priorita' di sviluppo**: gestione completa passo-passo partendo dal login,
  poi primo luogo, primo oggetto, ecc. (DB da riempire da zero con l'app).

### Pre-requisiti lato server da verificare una volta (non bloccano lo sviluppo app)
- `HBOX_AUTH_API_KEY_PEPPER` impostato nel compose e container riavviato
  (sezione 3.3).
- Regola di bypass Authelia su `/api` attiva (sezione 7.1).
- Prima API Key `hb_...` generata dalla UI web, da incollare/scansionare in app
  al primo test di M1.

---

## 12. Primo passo concreto per la chat che riceve questo file

1. Leggere questo documento per intero.
2. Dare per acquisite tutte le decisioni della sezione 11. Non servono altre
   conferme per partire.
3. Recuperare la spec OpenAPI dall'istanza `https://box.stevej.it` (`/swagger/`
   o il JSON nel repo) e verificare gli endpoint reali della :latest in uso.
4. Proporre lo scheletro di progetto (moduli, package, dipendenze) coerente con
   lo stack della sezione 5, e un piano dettagliato per M0-M1.
5. Iniziare a scrivere codice partendo da connessione e auth (M1), poi subito
   creazione luogo/oggetto (M2-M3 insieme). A ogni build di prova consegnare
   l'`.apk` compilato.

---

## 13. Riferimenti

- Repo attivo: https://github.com/sysadminsmedia/homebox
- Docs: https://homebox.software/
- Demo: https://demo.homebox.software/
- Discord: https://discord.homebox.software/
- Release v0.26.0 (entity merge, API Key): changelog nel repo, tag v0.26.0
- Guida migrazione entity merge: docs Homebox, sezione "entity-merge-upgrade"
- SDK Python di riferimento (mappa modelli/endpoint): PyPI `homebox-client`
- Spec OpenAPI nel repo: `docs/.../api/openapi-3.0.json`
