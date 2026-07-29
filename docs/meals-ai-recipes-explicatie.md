# Ruta „Meals" — Generator de rețete cu AI (Ollama)

> Document de prezentare. Explică de la zero ce s-a implementat în ultima sesiune,
> ce este Ollama, cum funcționează în spate și cum se leagă frontend ↔ backend ↔ AI.

---

## 1. Pe scurt: ce face funcționalitatea

Ecranul **Meals** din aplicație nu mai e un placeholder. Acum este un **generator de
rețete personalizate cu inteligență artificială**. Ideea, în limbaj simplu:

1. Aplicația știe ce alimente loghezi cel mai des (din istoricul tău de mese).
2. Alegi pentru ce masă vrei o rețetă (mic dejun / prânz / cină / gustare).
3. Apeși „Generează rețetă".
4. Backend-ul alege **un singur „ingredient-vedetă"** dintre alimentele tale frecvente
   (vezi secțiunea 4b — de ce un singur ingredient și nu toată lista) și îl trimite unui
   model AI care rulează **local pe calculator**, împreună cu obiectivul tău nutrițional.
   Modelul „inventează" o rețetă concretă în jurul acelui ingredient: titlu, descriere,
   ingrediente cu cantități, pași de preparare și macros (kcal, proteine, carbohidrați,
   grăsimi).
5. Poți salva rețeta ca favorită.

**Diferența față de un ChatGPT obișnuit:** rețeta e construită din *datele tale reale*
(ce mănânci tu de fapt) și ține cont de *scopul tău* (slăbire / menținere / masă).

---

## 2. Ce este Ollama (explicat de la zero)

### Modelul mare de limbaj (LLM)
Un **LLM** (Large Language Model) este același tip de AI ca ChatGPT: primește text și
produce text. „Înțelege" limbajul pentru că a fost antrenat pe cantități uriașe de text.
Noi îi cerem: *„uite ce mănâncă userul, scrie-i o rețetă"* → el răspunde cu rețeta.

### Problema: nu vrem să depindem de un serviciu plătit din cloud
ChatGPT (OpenAI) este un serviciu online: trimiți datele pe internet, plătești per cerere,
ai nevoie de cont/API key, iar datele utilizatorului ies din aplicație. Pentru acest
proiect asta e incomod (cost, confidențialitate, dependență de internet).

### Soluția: Ollama
**Ollama** este un program care îți permite să rulezi modele AI **local, pe propriul
calculator**, gratuit. Practic e „un ChatGPT instalat la tine pe laptop".

- Îl instalezi o singură dată.
- Descarci un model (noi folosim **`gemma3:4b`** — un model open-source de la Google,
  „4b" = 4 miliarde de parametri, suficient de mic să ruleze pe un laptop normal).
- Ollama pornește un **server local** pe adresa `http://localhost:11434`.
- Orice program de pe calculator poate trimite cereri la acel server și primește
  răspunsuri AI — fără internet, fără cont, fără costuri.

Analogie: Ollama e ca un mic restaurant deschis chiar în calculatorul tău. Backend-ul
nostru e „clientul" care comandă o rețetă, iar bucătarul (modelul `gemma3:4b`) o gătește.

### Cum pornește (operațional)
Pe calculatorul unde rulează backend-ul trebuie să existe:
```bash
ollama serve          # pornește serverul local pe :11434
ollama pull gemma3:4b # descarcă modelul (o singură dată)
```
Dacă Ollama nu rulează, backend-ul răspunde elegant cu eroarea „Serviciul AI nu e
disponibil" (HTTP 503) în loc să crape.

---

## 3. Arhitectura: cele 3 piese și cum comunică

```
┌─────────────────┐      HTTP       ┌──────────────────┐      HTTP      ┌──────────────┐
│  Android (Kotlin)│  ────────────▶  │  Backend FastAPI │  ───────────▶  │    Ollama    │
│   MealsScreen    │  POST /recipes/ │   (Python)       │  chat()        │  gemma3:4b   │
│   RecipeViewModel│   generate      │  routers/recipes │  format=json   │ (model local)│
└─────────────────┘  ◀────────────  └──────────────────┘  ◀───────────  └──────────────┘
        ▲              rețetă JSON           │   ▲           rețetă text
        │                                    │   │
        │                              ┌─────▼───┴─────┐
        │                              │  PostgreSQL    │
        └──── afișează rețeta ─────────│ food_logs +    │
                                       │ saved_recipes  │
                                       └────────────────┘
```

**Fluxul complet, pas cu pas:**

1. **Frontend** (telefon): userul alege masa și apasă „Generează". `RecipeViewModel`
   trimite `POST /recipes/generate` cu `{"meal_type": "BREAKFAST"}`.
2. **Backend** verifică token-ul Firebase (cine ești), apoi:
   - se uită în baza de date `food_logs` și calculează **top alimentele** tale;
   - alege **un singur ingredient-ancoră** dintre ele (aleator, ponderat după frecvență);
   - calculează **obiectivul nutrițional** din profilul tău;
   - construiește un **prompt** (instrucțiuni în text pentru AI) în jurul ancorei;
   - trimite promptul la **Ollama**.
3. **Ollama** rulează modelul și întoarce un text JSON cu rețeta.
4. **Backend** validează că JSON-ul are forma corectă și îl trimite înapoi la telefon.
5. **Frontend** afișează rețeta frumos (carduri cu ingrediente, pași, macros).

Important: telefonul **nu** vorbește niciodată direct cu Ollama. Backend-ul e
intermediarul (proxy). La fel ca la Open Food Facts — telefonul cere backend-ului,
backend-ul cere serviciului extern.

---

## 4. Backend — fișier cu fișier

Tot codul nou trăiește în câteva fișiere mici, fiecare cu o responsabilitate clară.

### `services/ollama_client.py` — „telefonul" către AI
Clasa `OllamaClient` știe să sune serverul Ollama.

- Citește din variabile de mediu *unde* e Ollama (`OLLAMA_BASE_URL`, default
  `localhost:11434`) și *ce model* (`OLLAMA_MODEL`, default `gemma3:4b`). Asta înseamnă
  că poți schimba modelul fără să modifici codul.
- Metoda `generate_json(messages)` apelează `chat()` cu **`format="json"`**. Acest
  parametru îi spune modelului: *„răspunde-mi obligatoriu cu un obiect JSON valid, nu
  cu text liber"*. E crucial — vrem date structurate, nu un paragraf.
- `temperature=0.7` = cât de „creativ"/variat e modelul. 0 = mereu același răspuns,
  1 = foarte variat. 0.7 e un echilibru bun pentru rețete diverse dar coerente.
- Dacă Ollama nu răspunde (nu e pornit, timeout de 120s), aruncă o eroare proprie
  `OllamaUnavailable` pe care backend-ul o traduce în 503.

> **JSON** = formatul standard prin care programele schimbă date structurate. Arată ca
> `{"titlu": "Omletă", "kcal": 300}`. E ușor de citit atât de om cât și de mașină.

### `services/recipes.py` — „creierul" funcționalității
Conține logica de business. Trei părți:

**a) `get_top_foods(...)` — ce mănânci cel mai des**
O interogare SQL pe tabela `food_logs`:
- ia mâncărurile logate în **ultimele 60 de zile**;
- le grupează după nume și le ordonează după **câte ori** le-ai logat (frecvență),
  apoi după **total grame**;
- păstrează **top 10**;
- pentru fiecare, ia macros-urile din cel mai recent log al acelui aliment.

Rezultat: o listă cu „alimentele tale semnătură" + valorile lor nutriționale.

**b) `build_recipe_prompt(...)` — scrie instrucțiunile pentru AI**
Aici se construiește textul trimis modelului. Sunt **două mesaje**:

- **System prompt** (`_SYSTEM_PROMPT`) — „personalitatea" și regulile fixe ale AI-ului:
  *„Ești un bucătar profesionist. Construiește o rețetă clasică, recognoscibilă, în jurul
  ingredientului-vedetă. Completează cu ingrediente obișnuite de cămară. Scrie în română.
  Cantitățile să fie concrete. Descrierea: maxim 12 cuvinte, factuală. Răspunde DOAR cu
  JSON în forma exactă {…}."* Aici i se dă și **șablonul JSON** pe care trebuie să-l completeze.
- **User prompt** — datele concrete de data asta: ce masă (`mic dejun`), care e
  obiectivul (`ex: DEFICIT, 1800 kcal/zi, P 130g, C 180g, G 50g`) și **un singur
  ingredient-vedetă** (ex: `Ou întreg`).

> De ce două mesaje? E convenția standard la LLM-uri: „system" = reguli permanente,
> „user" = cererea curentă. Modelul tratează regulile din system ca fiind prioritare.

> **De ce un singur ingredient și nu toată lista? (decizie importantă de design)**
> Prima versiune trimitea modelului **toată lista** de top alimente și îi cerea să le
> folosească. Problema: un model mic (`gemma3:4b`) le înghesuie naiv pe toate într-un
> singur fel de mâncare → combinații absurde precum „omletă cu banană și piept de pui".
> Soluția nu a fost un prompt mai bun, ci o schimbare **structurală**: backend-ul alege
> **un singur ingredient-ancoră** și îi arată modelului doar pe acela. Astfel modelul
> nici nu „vede" celelalte alimente, deci nu le mai poate amesteca, și completează singur
> ingrediente complementare care merg natural → rețete clasice, coerente. Vezi secțiunea 7
> pentru detalii despre acest experiment (e un punct bun de discuție la prezentare).

**c) `generate_recipe(...)` — orchestrarea + plasă de siguranță**
Leagă totul:
1. Ia top foods. Dacă ai **mai puțin de 3** alimente distincte → aruncă `InsufficientData`
   (n-are din ce compune o rețetă) → backend-ul răspunde 422.
2. Ia profilul și calculează goal-urile (refolosește `calculate_goals` din nutriție).
3. **Alege ingredientul-ancoră:** dintre top alimente, alege unul singur prin
   `random.choices` **ponderat după frecvență** (alimentele mai des logate au șanse mai
   mari, dar nu sunt mereu alese). Alegerea aleatoare dă **varietate** — regenerezi și
   primești o rețetă în jurul altui ingredient, nu mereu aceeași.
4. Construiește promptul cu acel ingredient.
5. Trimite la Ollama și încearcă să parseze răspunsul ca JSON valid.
6. **Retry de 3 ori:** modelele mici uneori scapă un JSON stricat. Fiecare reîncercare
   „regenerează" răspunsul, și de obicei a doua oară iese curat. Doar dacă eșuează toate
   3 încercările aruncă `RecipeParseError` → 502.

### `schemas.py` — forma datelor (contractul)
`RecipeDto` definește exact cum trebuie să arate o rețetă: `title`, `description`,
`ingredients` (listă de {nume, cantitate}), `steps` (listă de text), `servings`,
`kcal_per_serving`, `protein_g`, `carbs_g`, `fat_g`.

**Detaliu fin rezolvat în sesiune:** modelele mici returnează adesea macros ca numere
zecimale (`protein_g: 12.5`), dar telefonul (Kotlin) așteaptă numere întregi. Un
**validator** (`_round_floats_to_int`) rotunjește automat float-urile la int înainte de
validare, ca să nu respingem o rețetă altfel perfect bună.

### `routers/recipes.py` — cele 4 endpoint-uri HTTP
| Metodă | Rută | Ce face |
|---|---|---|
| `POST` | `/recipes/generate` | Generează o rețetă nouă cu AI (422 / 503 / 502 la erori) |
| `POST` | `/recipes` | Salvează o rețetă ca favorită |
| `GET` | `/recipes` | Listează rețetele salvate ale userului |
| `DELETE`| `/recipes/{id}` | Șterge o favorită (verifică că e a ta → altfel 403) |

Toate sunt protejate de Firebase: fără token valid → 401.

### `models.py` + migrația `b3c1f2a45d67` — stocarea favoritelor
Tabela nouă **`saved_recipes`**: `id`, `uid` (cine), `meal_type`, `title`, `recipe`
(rețeta întreagă stocată ca **JSON** într-o coloană), `created_at`. Legată de `profiles`
cu `ON DELETE CASCADE` (dacă se șterge userul, dispar și rețetele lui). Migrația Alembic
creează tabela în PostgreSQL.

---

## 5. Frontend — fișier cu fișier

### `model/RecipeDto.kt` — oglinda schemei backend
Aceleași câmpuri ca în Pydantic, dar în Kotlin. `@SerializedName` mapează numele de pe
„sârmă" (`kcal_per_serving`, snake_case) la numele Kotlin (`kcalPerServing`, camelCase).
Așa frontend și backend „vorbesc aceeași limbă".

### `api/RecipeApi.kt` — interfața Retrofit
Declară cele 4 apeluri HTTP în stil Kotlin (`generateRecipe`, `saveRecipe`, `getRecipes`,
`deleteRecipe`). Retrofit transformă automat aceste funcții în cereri HTTP reale.

### `viewmodel/RecipeViewModel.kt` — starea ecranului
Folosește un **state machine** (`RecipeUiState`) cu 4 stări: `Idle` (inițial),
`Loading` (se generează), `Result` (avem rețeta), `Error` (a picat ceva). Ecranul se
redesenează automat în funcție de stare.

Funcții cheie:
- `setMealType(...)` — reține ce masă a ales userul.
- `generate()` — apelează backend-ul; pe eroare **traduce codul HTTP în mesaj românesc**
  (422 → „Loghează mai multe alimente", 503 → „Serviciul AI nu e disponibil", etc.).
- `saveCurrent()` / `delete()` / `loadSaved()` — gestionează favoritele.

### `ui/MealsScreen.kt` — ce vede userul
Selector de masă (mic dejun/prânz/cină/gustare) → buton „Generează rețetă" → în funcție
de stare arată spinner / cardul rețetei (ingrediente, pași, macros) / mesaj de eroare cu
buton „Reîncearcă". Plus lista de favorite salvate.

### Activarea rutei
În `MainScaffold.kt`, ruta `meals` a fost adăugată în `enabledRoutes` ca să fie tappable
din bara de jos. (Înainte era dezactivată.)

---

## 6. Tratarea erorilor — gândit pentru demo

Sistemul e proiectat să **nu crape niciodată urât**. Fiecare lucru care poate merge prost
are un mesaj clar:

| Situație | Cod HTTP | Ce vede userul |
|---|---|---|
| Ai logat < 3 alimente | 422 | „Loghează mai multe alimente ca să generăm rețete" |
| Ollama nu e pornit / timeout | 503 | „Serviciul AI nu e disponibil, încearcă din nou" |
| AI a dat JSON stricat (după 3 retry) | 502 | „Răspuns AI invalid, încearcă din nou" |
| Fără internet la telefon | — | „Verifică conexiunea la internet" |

Asta e important: **degradare grațioasă** = aplicația rămâne
utilizabilă și explică problema, în loc să arate un ecran alb.

---

## 7. Decizii de design relevante

- **AI local vs. cloud:** alegerea Ollama în loc de OpenAI arată conștientizarea
  costurilor, a confidențialității datelor și a independenței de servicii plătite.
- **Personalizare reală:** rețeta vine din *datele utilizatorului* (food_logs) + *scopul*
  (TDEE/goals), nu un răspuns generic. Integrare între module (logging → AI).
- **Date structurate, nu text:** folosirea `format="json"` + validare Pydantic transformă
  un AI „care vorbește" într-o componentă de software pe care te poți baza.
- **Robustețe:** retry pe parse, rotunjire float→int, traducere coduri de eroare,
  fallback când serviciul e jos — toate arată maturitate inginerească.
- **Arhitectură curată:** separare clară client AI / logică / endpoint-uri / schemă,
  testabilă (clientul Ollama e „fake-uit" în teste, fără să ai nevoie de AI real).
- **Înțelegerea limitelor modelelor mici (prompt engineering aplicat):** vezi mai jos —
  e poate cel mai interesant punct de discuție tehnică.

### Studiu de caz: cum am obținut rețete realiste dintr-un model mic

Acesta e un fir narativ bun pentru prezentare, pentru că arată raționament ingineresc, nu
doar „am chemat un AI".

**Problema observată:** la mic dejun, modelul genera „Omletă cu banană și piept de pui" —
o combinație pe care niciun om n-ar mânca-o. Cauza: îi dădeam **toată lista** de alimente
frecvente și el le amesteca mecanic pe toate.

**Ce am încercat și ce am învățat:**

1. **Prompt-uri mai detaliate cu reguli** („folosește doar ce se potrivește") — *au eșuat*.
   Modelul mic ignoră instrucțiunile abstracte și tot ancorează pe primele alimente din listă.
2. **Reguli negative explicite** („NU pune banană în omletă") — *au înrăutățit lucrurile*.
   Pe modelele mici, a numi o combinație greșită o **primează**: a ajuns să genereze omletă
   cu banană în 6 din 6 cazuri. Lecție: regulile negative pot avea efect invers.
3. **Schimbarea unui model mai mare** (`qwen2.5:7b`, care urmează instrucțiunile mai bine) —
   *compromis prost*: respecta regulile, dar avea **română stricată** („brânză de pecel",
   „cibul minciu"), inacceptabil pentru un demo în română. Concluzie: pentru output în
   română, `gemma3:4b` e mai bun chiar dacă e mai mic.
4. **Soluția câștigătoare — structurală + reguli pozitive:**
   - **Un singur ingredient-ancoră** (modelul nu mai vede celelalte alimente → nu le poate
     amesteca).
   - **Reguli pozitive, nu negative:** în loc de „nu pune banană în omletă", i-am spus
     „dacă ingredientul e un fruct, fă un preparat dulce: clătite, fulgi de ovăz, smoothie".
     Astfel modelul e ghidat *spre* ce e bine, fără să-i amintim de combinația greșită.
   - **Descrieri concise:** în loc de o listă de cuvinte interzise (pe care le ignora),
     i-am dat un format clar + exemplu („maxim 12 cuvinte, factual"), ca să scape de
     descrierile pompoase gen „o omletă delicioasă și perfectă".

**Rezultat:** banană → clătite/fulgi de ovăz, ou → omletă cu brânză și spanac, pui → piept
la grătar cu salată. Coerent și recognoscibil.

> Ideea de bază a discuției: **uneori soluția nu e un prompt mai inteligent, ci o schimbare
> de arhitectură** (ce date îi dai modelului). Și: **regulile pozitive bat regulile negative**
> pe modelele mici.

---

## 8. Cum dai demo (checklist)

1. Pornește PostgreSQL: `brew services start postgresql@16`.
2. Pornește Ollama: `ollama serve` și asigură-te că ai modelul: `ollama pull gemma3:4b`.
3. Pornește backend-ul: din `backend/` → `uvicorn main:app --reload`.
4. În aplicație: loghează **cel puțin 3 alimente** diferite (altfel primești 422).
5. Mergi la tab-ul **Meals** → alege masa → **Generează rețetă**.
6. (Opțional) Salvează rețeta ca favorită și arată că persistă.

**Plan B dacă pică Ollama în timpul prezentării:** arată mesajul elegant de eroare (503)
ca dovadă de robustețe — apoi repornește `ollama serve` și reîncearcă.

---

## 9. Glosar rapid

- **LLM** — model AI care procesează și generează text (ca ChatGPT).
- **Ollama** — program ce rulează LLM-uri local, gratuit, pe `localhost:11434`.
- **gemma3:4b** — modelul open-source folosit (4 miliarde parametri, de la Google).
- **Prompt** — textul-instrucțiune trimis AI-ului (system = reguli, user = cererea).
- **JSON** — format standard de date structurate (`{"cheie": "valoare"}`).
- **Endpoint** — o „adresă" HTTP a backend-ului care face o acțiune.
- **Proxy** — intermediar; telefonul cere backend-ului, backend-ul cere AI-ului.
- **Pydantic / DTO** — definesc forma exactă a datelor și o validează.
- **Token Firebase** — dovada de identitate atașată fiecărei cereri.
- **Degradare grațioasă** — când ceva pică, aplicația explică în loc să crape.
```
