/* LanQuiz web — la stessa app Android in versione PWA.
   Tutto gira nel browser: i banchi preinstallati arrivano da banks/, quelli
   importati e i risultati stanno in localStorage. Nessun server.

   Il formato dei quiz, il calcolo dell'id delle domande e il file di backup
   sono identici a quelli dell'app Android: un backup si travasa nei due sensi. */
"use strict";

const VERSION = "1.2";
const REPO = "zeo93/LanQuiz";
const STORE_KEY = "lanquiz";

const view = document.getElementById("view");
const headerTitle = document.getElementById("header-title");
const headerTimer = document.getElementById("header-timer");
const headerActions = document.getElementById("header-actions");
const btnBack = document.getElementById("btn-back");

// ------------------------------------------------------------------- utili

const $ = (sel, el) => (el || document).querySelector(sel);
const $$ = (sel, el) => Array.from((el || document).querySelectorAll(sel));

const esc = (s) => String(s ?? "").replace(/[&<>"']/g,
  (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

const GIORNO = 24 * 60 * 60 * 1000;

function clock(seconds) {
  const s = Math.max(0, Math.round(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const pad = (n) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(s % 60)}` : `${pad(m)}:${pad(s % 60)}`;
}

function duration(seconds) {
  const s = Math.max(0, Math.round(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (h > 0) return `${h} h ${String(m).padStart(2, "0")} min`;
  if (m > 0) return `${m} min ${String(s % 60).padStart(2, "0")} s`;
  return `${s} s`;
}

function whenText(ts) {
  return new Date(ts).toLocaleString("it-IT",
    { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

/** "oggi", "domani", "fra 5 giorni", oppure la data se è lontana. */
function whenShort(ts) {
  const giorni = Math.max(0, Math.round((ts - Date.now()) / GIORNO));
  if (giorni <= 0) return "oggi";
  if (giorni === 1) return "domani";
  if (giorni <= 14) return `fra ${giorni} giorni`;
  return new Date(ts).toLocaleDateString("it-IT", { day: "numeric", month: "short" });
}

/* Icone disegnate, non glifi: scalano e si ricolorano col testo. */
const ICONE = {
  statistiche: '<path d="M4 19V9M10 19V5M16 19v-7M4 19h16"/>',
  aggiungi: '<path d="M12 5v14M5 12h14"/>',
  impostazioni: '<circle cx="12" cy="12" r="3.2"/><path d="M12 3v2.2M12 18.8V21M21 12h-2.2M5.2 12H3M18.4 5.6l-1.6 1.6M7.2 16.8l-1.6 1.6M18.4 18.4l-1.6-1.6M7.2 7.2 5.6 5.6"/>',
  indietro: '<path d="M19 12H5M11 18l-6-6 6-6"/>',
  bandiera: '<path d="M5 21V4M5 4h11l-1.6 3.5L16 11H5"/>',
  mappa: '<rect x="4" y="4" width="6.5" height="6.5" rx="1.4"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.4"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.4"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.4"/>',
  altro: '<circle cx="12" cy="5.5" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="12" cy="18.5" r="1.6"/>',
};

const icona = (nome, misura) =>
  `<svg width="${misura || 20}" height="${misura || 20}" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" stroke-width="1.8" stroke-linecap="round"
        stroke-linejoin="round" aria-hidden="true">${ICONE[nome]}</svg>`;

/**
 * Anello di avanzamento. L'SVG è disegnato su una griglia fissa di 100 e steso
 * al 100% del contenitore: così la misura la decide il CSS, e l'anello si
 * adatta allo schermo senza che il disegno vada rifatto a ogni ridimensionamento.
 */
function anello(pct, variante, colore, etichetta) {
  const spessore = variante === "mini" ? 11 : 9;
  const r = 50 - spessore / 2 - 1;
  const circ = 2 * Math.PI * r;
  const offset = circ * (1 - Math.max(0, Math.min(100, pct)) / 100);
  return `<div class="ring ${variante}">
    <svg viewBox="0 0 100 100" width="100%" height="100%" aria-hidden="true">
      <circle cx="50" cy="50" r="${r.toFixed(1)}" fill="none" stroke="var(--surface2)"
              stroke-width="${spessore}"></circle>
      ${pct > 0 ? `<circle cx="50" cy="50" r="${r.toFixed(1)}" fill="none" stroke="${colore}"
              stroke-width="${spessore}" stroke-linecap="round"
              stroke-dasharray="${circ.toFixed(1)}" stroke-dashoffset="${offset.toFixed(1)}"
              transform="rotate(-90 50 50)"></circle>` : ""}
    </svg>
    <div class="dentro"><div>
      <div class="val">${pct}%</div>
      ${etichetta ? `<div class="cap">${esc(etichetta)}</div>` : ""}
    </div></div>
  </div>`;
}

function shuffle(list) {
  for (let i = list.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [list[i], list[j]] = [list[j], list[i]];
  }
  return list;
}

let toastTimer = null;
function toast(message) {
  const old = $(".toast");
  if (old) old.remove();
  const el = document.createElement("div");
  el.className = "toast";
  el.textContent = message;
  document.body.appendChild(el);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.remove(), 3800);
}

// ---------------------------------------------------------------- memoria

const DEFAULTS = {
  mode: "studio", count: 0, timer: 0, shuffleQ: true, shuffleA: true,
  passPct: 70, theme: "sistema", autoNext: true,
};

function emptyStore() {
  return {
    settings: Object.assign({}, DEFAULTS),
    history: [],
    srs: {},      // banco -> domanda -> {box, due, last, ok, ko}
    flags: {},    // banco -> [domanda]
    notes: {},    // banco -> domanda -> testo
    tags: {},     // banco -> domanda -> [argomenti]
    hidden: [],
    resume: null,
    user: {},     // banco importato -> contenuto del file
    migrated: {}, // banchi già convertiti dagli id della 1.0
    wrong: {},    // resto della 1.0, letto solo dalla conversione
  };
}

function loadStore() {
  let data = {};
  try {
    data = JSON.parse(localStorage.getItem(STORE_KEY) || "{}");
  } catch (e) {
    data = {};
  }
  const s = emptyStore();
  s.settings = Object.assign({}, DEFAULTS, data.settings || {});
  s.history = data.history || [];
  s.srs = data.srs || {};
  s.flags = data.flags || {};
  s.notes = data.notes || {};
  s.tags = data.tags || {};
  s.hidden = data.hidden || [];
  s.resume = data.resume || null;
  s.user = data.user || {};
  s.migrated = data.migrated || {};
  s.wrong = data.wrong || {};
  return s;
}

let store = loadStore();

function save() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify(store));
  } catch (e) {
    toast("Spazio esaurito: elimina qualche quiz importato.");
  }
}

const setOf = (map, bankId) => new Set(map[bankId] || []);

function writeSet(map, bankId, set) {
  if (set.size === 0) delete map[bankId];
  else map[bankId] = Array.from(set);
}

function applyTheme() {
  const t = store.settings.theme;
  if (t === "sistema") document.documentElement.removeAttribute("data-theme");
  else document.documentElement.setAttribute("data-theme", t);
}

// ----------------------------------------------------------------- lettore

/* Stesse regole del lettore Android (Parser.java):
   domanda;risposta esatta;errata;…  ·  * marca una corretta  ·  ## spiegazione
   ·  @argomento  ·  # commento  ·  separatore ; poi tab poi virgola  ·  JSON. */

const FNV_OFFSET = 0xcbf29ce484222325n;
const FNV_PRIME = 0x100000001b3n;
const MASK64 = 0xffffffffffffffffn;
const utf8 = new TextEncoder();

/** FNV-1a a 64 bit: identico a Question.fnv1a64 in Java, byte per byte. */
function fnv1a64(s) {
  let h = FNV_OFFSET;
  for (const b of utf8.encode(s)) {
    h = ((h ^ BigInt(b)) * FNV_PRIME) & MASK64;
  }
  return h.toString(16).padStart(16, "0");
}

const normalizeText = (text) => text.trim().replace(/\s+/g, " ").toLowerCase();

const qid = (text) => fnv1a64(normalizeText(text));

/** L'id usato fino alla 1.0: serve solo a recuperare i dati già salvati. */
function qidLegacy(text) {
  const norm = normalizeText(text);
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  for (let i = 0; i < norm.length; i++) {
    h1 = Math.imul(h1 ^ norm.charCodeAt(i), 0x01000193) >>> 0;
    h2 = Math.imul(h2 + norm.charCodeAt(i), 0x85ebca6b) >>> 0;
  }
  return h1.toString(16).padStart(8, "0") + h2.toString(16).padStart(8, "0");
}

function normalizeTag(tag) {
  return String(tag || "").trim().toLowerCase()
    .replace(/[^\p{L}\p{N}+#._-]+/gu, "-")
    .replace(/(^-+|-+$)/g, "");
}

function makeQuestion(text, answers, correct, explanation, tags) {
  return {
    text, answers, correct,
    explanation: explanation || "",
    tags: tags || [],
    id: qid(text),
    legacy: qidLegacy(text),
    multi: correct.length > 1,
  };
}

function parseLine(line) {
  const sep = line.includes(";") ? ";" : (line.includes("\t") ? "\t" : ",");
  const parts = line.split(sep);
  if (parts.length < 3) return null;
  const text = parts[0].trim();
  if (!text) return null;

  const answers = [];
  const correct = [];
  const tags = [];
  let explanation = "";
  for (let i = 1; i < parts.length; i++) {
    let f = parts[i].trim();
    if (!f) continue;
    if (f.startsWith("##")) {
      explanation = f.slice(2).trim();
      continue;
    }
    if (f.startsWith("@")) {
      // solo la virgola separa: cosi "cloud storage" resta un argomento solo
      for (const raw of f.slice(1).split(/[,;@]+/)) {
        const tag = normalizeTag(raw);
        if (tag && !tags.includes(tag)) tags.push(tag);
      }
      continue;
    }
    let isCorrect = false;
    if (f.startsWith("*")) {
      isCorrect = true;
      f = f.slice(1).trim();
    } else if (f.startsWith("\\*")) {
      f = f.slice(1);
    }
    if (!f) continue;
    if (isCorrect) correct.push(answers.length);
    answers.push(f);
  }
  if (answers.length < 2) return null;
  if (correct.length === 0) correct.push(0);
  return makeQuestion(text, answers, correct, explanation, tags);
}

function parseBank(content) {
  if (!content) return [];
  if (content.charCodeAt(0) === 0xfeff) content = content.slice(1);
  const trimmed = content.trim();
  if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
    const fromJson = parseJsonBank(trimmed);
    if (fromJson.length) return fromJson;
  }
  const out = [];
  for (const raw of content.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const q = parseLine(line);
    if (q) out.push(q);
  }
  return out;
}

function parseJsonBank(content) {
  try {
    const parsed = JSON.parse(content);
    const arr = Array.isArray(parsed) ? parsed : parsed.questions;
    if (!Array.isArray(arr)) return [];
    return arr
      .map((o) => {
        const answers = o.a || o.answers || [];
        let correct = o.correct || [];
        if (!correct.length && answers.length) correct = [0];
        return makeQuestion(o.q || o.text || "", answers, correct, o.explanation,
          (o.tags || []).map(normalizeTag).filter(Boolean));
      })
      .filter((q) => q.text.trim() && q.answers.length >= 2);
  } catch (e) {
    return [];
  }
}

// ------------------------------------------------------------------ banchi

const cache = new Map();   // id -> domande già lette
let bundledIds = [];

const stemOf = (id) => id.replace(/\.[A-Za-z0-9]+$/, "");
const titleOf = (id) => stemOf(id).replace(/_/g, " ").trim();

/* Stessa regola di Bank.categoryOf: si tolgono i suffissi di numerazione. */
function categoryOf(id) {
  const parts = stemOf(id).split(/[_\-\s]+/);
  let end = parts.length;
  while (end > 0) {
    const p = parts[end - 1].toLowerCase();
    if (/^\d+[a-z]?$/.test(p) || p === "full" || p === "part" || /^v\d+$/.test(p)) end--;
    else break;
  }
  return end < 2 ? "Altri quiz" : parts.slice(0, end).join(" ");
}

async function bankText(id) {
  if (store.user[id] !== undefined) return store.user[id];
  const res = await fetch("banks/" + encodeURIComponent(id));
  if (!res.ok) throw new Error("HTTP " + res.status);
  return res.text();
}

async function bankQuestions(id) {
  if (!cache.has(id)) {
    const questions = parseBank(await bankText(id));
    migrateBank(id, questions);
    cache.set(id, questions);
  }
  return cache.get(id);
}

/**
 * Porta i dati della 1.0 nel nuovo formato: gli id delle domande sono cambiati
 * (ora coincidono con quelli dell'app Android), quindi si ripassa dal testo.
 */
function migrateBank(bankId, questions) {
  if (store.migrated[bankId]) return;
  const mappa = new Map(questions.map((q) => [q.legacy, q.id]));

  const vecchieSbagliate = store.wrong[bankId] || [];
  if (vecchieSbagliate.length) {
    const cards = store.srs[bankId] || (store.srs[bankId] = {});
    const adesso = Date.now();
    for (const vecchio of vecchieSbagliate) {
      const nuovo = mappa.get(vecchio);
      if (nuovo && !cards[nuovo]) {
        cards[nuovo] = { box: 0, due: adesso, last: adesso, ok: 0, ko: 1 };
      }
    }
  }
  if (store.flags[bankId]) {
    store.flags[bankId] = store.flags[bankId].map((v) => mappa.get(v) || v);
  }
  delete store.wrong[bankId];
  store.migrated[bankId] = true;
  save();
}

async function allBanks() {
  const ids = bundledIds.filter((id) => !store.hidden.includes(id))
    .concat(Object.keys(store.user));
  const banks = [];
  for (const id of ids) {
    let questions = [];
    try {
      questions = await bankQuestions(id);
    } catch (e) {
      continue; // banco non raggiungibile (offline e mai aperto): lo si salta
    }
    // l'ora si legge dopo bankQuestions: la conversione dalla 1.0 scrive
    // scadenze a "adesso", e leggendola prima risulterebbero tutte future
    const adesso = Date.now();
    const attempts = store.history.filter((h) => h.bank === id);
    const cards = store.srs[id] || {};
    const tags = new Set();
    let due = 0;
    let unseen = 0;
    let nextDue = 0;
    let assodate = 0;   // dalla scatola 3 in su: non tornano prima di una settimana
    for (const q of questions) {
      for (const t of tagsOf(id, q)) tags.add(t);
      const card = cards[q.id];
      if (!card) unseen++;
      else if (card.due <= adesso) due++;
      else if (!nextDue || card.due < nextDue) nextDue = card.due;
      if (card && card.box >= 3) assodate++;
    }
    banks.push({
      id,
      title: titleOf(id),
      category: categoryOf(id),
      bundled: store.user[id] === undefined,
      count: questions.length,
      best: attempts.length ? Math.max(...attempts.map((h) => pct(h))) : -1,
      due,
      unseen,
      assodate,
      pctAssodate: questions.length ? Math.round((assodate / questions.length) * 100) : 0,
      nextDue: due ? 0 : nextDue,
      flags: setOf(store.flags, id).size,
      tags: Array.from(tags).sort(),
    });
  }
  banks.sort((a, b) => a.category.localeCompare(b.category, "it")
    || a.title.localeCompare(b.title, "it"));
  return banks;
}

function groupByCategory(banks) {
  const map = new Map();
  for (const b of banks) {
    if (!map.has(b.category)) map.set(b.category, []);
    map.get(b.category).push(b);
  }
  if (map.has("Altri quiz")) {
    const others = map.get("Altri quiz");
    map.delete("Altri quiz");
    map.set("Altri quiz", others);
  }
  return map;
}

function saveUserBank(name, content) {
  if (!parseBank(content).length) throw new Error("nessuna domanda valida nel file");
  let id = (name || "quiz").trim().replace(/[\\/:*?"<>|]+/g, "_");
  if (!/\.(txt|csv|json)$/i.test(id)) id += ".txt";
  let unique = id;
  let n = 2;
  while (store.user[unique] !== undefined || bundledIds.includes(unique)) {
    unique = stemOf(id) + "_" + n + ".txt";
    n++;
  }
  store.user[unique] = content;
  store.migrated[unique] = true;
  cache.delete(unique);
  save();
  return unique;
}

function forgetBank(id) {
  for (const map of [store.srs, store.flags, store.notes, store.tags, store.migrated]) {
    delete map[id];
  }
  store.history = store.history.filter((h) => h.bank !== id);
  if (store.resume && store.resume.bankId === id) store.resume = null;
}

// --------------------------------------------------------- note e argomenti

const noteOf = (bankId, id) => (store.notes[bankId] || {})[id] || "";

function setNote(bankId, id, text) {
  const map = store.notes[bankId] || (store.notes[bankId] = {});
  if (text && text.trim()) map[id] = text.trim();
  else delete map[id];
  if (!Object.keys(map).length) delete store.notes[bankId];
  save();
}

const userTagsOf = (bankId, id) => (store.tags[bankId] || {})[id] || [];

function setUserTags(bankId, id, tags) {
  const map = store.tags[bankId] || (store.tags[bankId] = {});
  if (tags && tags.length) map[id] = tags;
  else delete map[id];
  if (!Object.keys(map).length) delete store.tags[bankId];
  save();
}

/** Gli argomenti che valgono per una domanda: quelli del file più i tuoi. */
function tagsOf(bankId, q) {
  const out = q.tags.slice();
  for (const t of userTagsOf(bankId, q.id)) {
    if (!out.includes(t)) out.push(t);
  }
  return out;
}

function parseTagInput(raw) {
  const out = [];
  for (const piece of String(raw || "").split(/[,;@]+/)) {
    const tag = normalizeTag(piece);
    if (tag && !out.includes(tag)) out.push(tag);
  }
  return out;
}

// -------------------------------------------------------- ripasso (Leitner)

/* Quanti giorni prima che una domanda torni a farsi vedere: chi sbaglia
   ricomincia dalla scatola 0, chi risponde bene sale e sparisce più a lungo. */
const GIORNI_PER_SCATOLA = [0, 1, 3, 7, 16, 35];
const SCATOLA_MAX = GIORNI_PER_SCATOLA.length - 1;

const cardOf = (bankId, id) => (store.srs[bankId] || {})[id];

function grade(bankId, items) {
  const adesso = Date.now();
  const cards = store.srs[bankId] || (store.srs[bankId] = {});
  for (const it of items) {
    const card = cards[it.q.id]
      || (cards[it.q.id] = { box: 0, due: 0, last: 0, ok: 0, ko: 0 });
    if (itemRight(it)) {
      card.box = Math.min(SCATOLA_MAX, card.box + 1);
      card.ok++;
      card.due = adesso + GIORNI_PER_SCATOLA[card.box] * GIORNO;
    } else {
      card.box = 0;
      card.ko++;
      card.due = adesso;
    }
    card.last = adesso;
  }
}

// ---------------------------------------------------------------- sessione

const pct = (r) => (r.total > 0 ? Math.round((r.correct / r.total) * 100) : 0);

function filterQuestions(bankId, questions, filter, tag) {
  const cards = store.srs[bankId] || {};
  const adesso = Date.now();
  switch (filter) {
    case "ripasso":
      return questions.filter((q) => cards[q.id] && cards[q.id].due <= adesso)
        .sort((a, b) => cards[a.id].due - cards[b.id].due);
    case "nuove":
      return questions.filter((q) => !cards[q.id]);
    case "contrassegnate": {
      const ids = setOf(store.flags, bankId);
      return questions.filter((q) => ids.has(q.id));
    }
    case "argomento":
      return questions.filter((q) => tagsOf(bankId, q).includes(tag));
    default:
      return questions.slice();
  }
}

function buildSession(bank, questions, filter, tag) {
  const s = store.settings;
  let pool = filterQuestions(bank.id, questions, filter, tag);
  // il ripasso segue le scadenze: rimescolarlo vanificherebbe l'ordine
  if (s.shuffleQ && filter !== "ripasso") shuffle(pool);
  if (s.count > 0 && s.count < pool.length) pool = pool.slice(0, s.count);

  const flagged = setOf(store.flags, bank.id);
  return {
    bankId: bank.id,
    bankTitle: bank.title,
    mode: s.mode,
    index: 0,
    timerSeconds: s.timer * 60,
    secondsLeft: s.timer * 60,
    elapsed: 0,
    timedOut: false,
    items: pool.map((q) => ({
      q,
      order: s.shuffleA ? shuffle(q.answers.map((_, i) => i)) : q.answers.map((_, i) => i),
      selected: [],
      flagged: flagged.has(q.id),
      revealed: false,
    })),
  };
}

const isExam = (s) => s.mode === "esame";
const itemAnswered = (it) => it.selected.length > 0;

function itemRight(it) {
  return itemAnswered(it)
    && it.selected.length === it.q.correct.length
    && it.q.correct.every((c) => it.selected.includes(c));
}

const answeredCount = (s) => s.items.filter(itemAnswered).length;
const correctCount = (s) => s.items.filter(itemRight).length;
const sessionPct = (s) =>
  (s.items.length ? Math.round((correctCount(s) / s.items.length) * 100) : 0);

function recordResult(s) {
  store.history.push({
    bank: s.bankId, title: s.bankTitle, ts: Date.now(),
    correct: correctCount(s), total: s.items.length, mode: s.mode, seconds: s.elapsed,
  });
  while (store.history.length > 500) store.history.shift();
  grade(s.bankId, s.items);
  store.resume = null;
  save();
}

// ------------------------------------------------------------ navigazione

let current = { name: "home" };
let session = null;
let ticker = null;

function setHeader(title, actions, showBack) {
  headerTitle.textContent = title;
  headerActions.innerHTML = actions || "";
  btnBack.hidden = !showBack;
  headerTimer.hidden = true;
}

function go(route, push) {
  stopTicker();
  current = route;
  if (push !== false) history.pushState(route, "", "");
  window.scrollTo(0, 0);
  render();
}

const back = () => history.back();

btnBack.addEventListener("click", back);

/** Uscendo dal quiz (indietro, chiusura scheda) la sessione resta riprendibile. */
function parkSession() {
  if (session && current.name === "quiz" && session.items.some(itemAnswered)) {
    store.resume = session;
    save();
  }
}

window.addEventListener("pagehide", parkSession);

window.addEventListener("popstate", (e) => {
  stopTicker();
  parkSession();
  current = e.state || { name: "home" };
  render();
});

function render() {
  switch (current.name) {
    case "quiz": return renderQuiz();
    case "result": return renderResult();
    case "stats": return renderStats();
    case "settings": return renderSettings();
    case "import": return renderImport();
    default: return renderHome();
  }
}

// ------------------------------------------------------------------- home

let homeQuery = "";

async function renderHome() {
  setHeader("LanQuiz",
    `<button class="icon-btn accent" id="a-stats" title="Statistiche">${icona("statistiche")}</button>
     <button class="icon-btn accent" id="a-import" title="Importa un quiz">${icona("aggiungi")}</button>
     <button class="icon-btn accent" id="a-settings" title="Impostazioni">${icona("impostazioni")}</button>`,
    false);

  const s = store.settings;
  const chip = (group, value, label, on) =>
    `<button class="chip" data-group="${group}" data-value="${value}" aria-pressed="${on}">${label}</button>`;

  const countValue = [0, 10, 25, 50].includes(s.count) ? s.count : "custom";
  const timerValue = [0, 15, 30, 90].includes(s.timer) ? s.timer : "custom";

  view.innerHTML = `
    <div id="oggi"></div>

    ${store.resume ? `
      <div class="card soft">
        <b>Quiz interrotto</b>
        <div class="small" style="color:inherit">
          ${esc(store.resume.bankTitle)} — domanda ${store.resume.index + 1} di ${store.resume.items.length}
        </div>
        <div class="row mt">
          <button class="btn" id="do-resume">Riprendi</button>
          <button class="btn text" id="do-discard">Scarta</button>
        </div>
      </div>` : ""}

    <label class="field">Come vuoi esercitarti</label>
    <div class="chips">
      <div class="segmented primaria">
        ${chip("mode", "studio", "Studio", s.mode === "studio")}
        ${chip("mode", "esame", "Esame", s.mode === "esame")}
      </div>
      <div class="segmented">
        ${chip("count", 0, "Tutte", countValue === 0)}
        ${chip("count", 10, "10", countValue === 10)}
        ${chip("count", 25, "25", countValue === 25)}
        ${chip("count", 50, "Prova esame", countValue === 50)}
        ${chip("count", "custom", countValue === "custom" ? s.count : "Altro", countValue === "custom")}
      </div>
      <div class="segmented">
        ${chip("timer", 0, "No timer", timerValue === 0)}
        ${chip("timer", 15, "15′", timerValue === 15)}
        ${chip("timer", 30, "30′", timerValue === 30)}
        ${chip("timer", 90, "90′", timerValue === 90)}
        ${chip("timer", "custom", timerValue === "custom" ? s.timer + "′" : "Altro", timerValue === "custom")}
      </div>
    </div>
    <div class="chips" style="margin-top:10px">
      <span class="small">${s.mode === "esame"
        ? "Nessun riscontro fino alla consegna."
        : "Risposta svelata subito, con spiegazione."}</span>
      <span class="grow"></span>
      ${chip("sq", "toggle", "Mescola le domande", s.shuffleQ)}
      ${chip("sa", "toggle", "Mescola le risposte", s.shuffleA)}
    </div>

    <label class="field">I tuoi quiz</label>
    <input type="text" id="q" placeholder="Cerca un quiz…" value="${esc(homeQuery)}">

    <div id="banks"><div class="small" style="padding:20px 0">Carico i quiz…</div></div>
  `;

  $("#a-stats").onclick = () => go({ name: "stats" });
  $("#a-import").onclick = () => go({ name: "import" });
  $("#a-settings").onclick = () => go({ name: "settings" });

  if (store.resume) {
    $("#do-resume").onclick = () => {
      session = store.resume;
      store.resume = null;
      save();
      go({ name: "quiz" });
    };
    $("#do-discard").onclick = () => {
      store.resume = null;
      save();
      renderHome();
    };
  }

  const search = $("#q");
  search.oninput = () => {
    homeQuery = search.value.trim();
    paintBanks(bancheCaricate);
  };

  $$(".chip", view).forEach((c) => {
    c.onclick = () => onSettingChip(c.dataset.group, c.dataset.value);
  });

  caricaHome();
}

/** I banchi si leggono una volta sola: la scheda di oggi e l'elenco li condividono. */
let bancheCaricate = [];

async function caricaHome() {
  bancheCaricate = await allBanks();
  paintOggi(bancheCaricate);
  paintBanks(bancheCaricate);
}

function onSettingChip(group, value) {
  const s = store.settings;
  if (group === "mode") {
    s.mode = value;
  } else if (group === "count") {
    if (value === "custom") {
      const n = parseInt(prompt("Quante domande?", s.count || 20), 10);
      if (!Number.isFinite(n) || n < 1) return;
      s.count = n;
    } else if (Number(value) === 50) {
      // "Prova esame": stessa scorciatoia dell'app originale
      s.count = 50;
      s.timer = 90;
      s.mode = "esame";
    } else {
      s.count = Number(value);
    }
  } else if (group === "timer") {
    if (value === "custom") {
      const n = parseInt(prompt("Minuti a disposizione (0 = nessun timer)", s.timer || 60), 10);
      if (!Number.isFinite(n) || n < 0) return;
      s.timer = n;
    } else {
      s.timer = Number(value);
    }
  } else if (group === "sq") {
    s.shuffleQ = !s.shuffleQ;
  } else if (group === "sa") {
    s.shuffleA = !s.shuffleA;
  }
  save();
  renderHome();
}

/** La scheda in cima: quante domande scadono oggi e quanto materiale è assodato. */
function paintOggi(banks) {
  const box = $("#oggi");
  if (!box) return;

  const dovute = banks.reduce((n, b) => n + b.due, 0);
  const domande = banks.reduce((n, b) => n + b.count, 0);
  const assodate = banks.reduce((n, b) => n + b.assodate, 0);
  const banchiDovuti = banks.filter((b) => b.due).length;
  const pct = domande ? Math.round((assodate / domande) * 100) : 0;

  if (!domande) {
    box.innerHTML = "";
    return;
  }

  const prossimo = banks.filter((b) => b.nextDue).map((b) => b.nextDue).sort()[0];
  const affrontate = banks.reduce((n, b) => n + (b.count - b.unseen), 0);

  // tre stati diversi: non hai ancora cominciato, hai qualcosa in scadenza,
  // oppure sei in pari e il ripasso torna più avanti
  let etichetta;
  let titolo;
  let sotto;
  let calmo = true;
  if (!affrontate) {
    etichetta = "DA COMINCIARE";
    titolo = `${domande} domande pronte`;
    sotto = "Fai un primo quiz: da lì in poi LanQuiz sa cosa riproporti e quando.";
  } else if (dovute) {
    etichetta = "IN SCADENZA OGGI";
    titolo = dovute + (dovute === 1 ? " domanda" : " domande");
    sotto = `Su ${banchiDovuti} ${banchiDovuti === 1 ? "banco" : "banchi"}. Si parte dalle più in ritardo.`;
    calmo = false;
  } else {
    etichetta = "RIPASSO IN PARI";
    titolo = "Niente da ripassare";
    sotto = prossimo
      ? `Il prossimo ripasso torna ${whenShort(prossimo)}.`
      : "Hai assodato tutto quello che hai affrontato finora.";
  }

  box.innerHTML = `
    <div class="hero">
      <div class="testo">
        <span class="tag ${calmo ? "calmo" : ""}"><i></i> ${etichetta}</span>
        <div class="numerone">${esc(titolo)}</div>
        <div class="small">${esc(sotto)}</div>
        <div class="row mt">
          ${dovute ? `<button class="btn" id="oggi-vai">Ripassa adesso</button>` : ""}
          <button class="btn ${dovute ? "ghost" : ""}" id="oggi-esame">Prova d'esame</button>
        </div>
      </div>
      ${anello(pct, "grande", "var(--accent)", "assodato")}
    </div>
  `;

  const vai = $("#oggi-vai");
  if (vai) vai.onclick = () => avviaRipassoGenerale(banks);
  $("#oggi-esame").onclick = () => {
    store.settings.count = 50;
    store.settings.timer = 90;
    store.settings.mode = "esame";
    save();
    renderHome();
    toast("Impostata la prova d'esame: 50 domande in 90 minuti. Scegli il banco.");
  };
}

/** Ripassa il banco che ha più domande in scadenza: un tocco, si parte. */
async function avviaRipassoGenerale(banks) {
  const dovuti = banks.filter((b) => b.due).sort((a, b) => b.due - a.due);
  if (!dovuti.length) return;
  startQuiz(dovuti[0], "ripasso");
}

function paintBanks(tutti) {
  const box = $("#banks");
  if (!box) return;
  const banks = tutti.filter((b) => {
    const q = homeQuery.toLowerCase();
    return !q || b.title.toLowerCase().includes(q) || b.category.toLowerCase().includes(q);
  });

  if (!banks.length) {
    box.innerHTML = `<div class="small" style="padding:20px 0">${homeQuery
      ? `Nessun quiz corrisponde a «${esc(homeQuery)}».`
      : "Nessun quiz. Importane uno dal pulsante + in alto."}</div>`;
    return;
  }

  let html = "";
  for (const [category, list] of groupByCategory(banks)) {
    html += `<h2 class="section">${esc(category)}</h2><div class="bank-grid">`;
    for (const b of list) {
      const bits = [b.count === 1 ? "1 domanda" : `${b.count} domande`];
      if (b.best >= 0) bits.push(`record ${b.best}%`);

      let stato;
      let classe;
      let colore;
      if (b.due) {
        stato = `${b.due} oggi`;
        classe = "dovute";
        colore = "var(--warn)";
      } else if (b.unseen === b.count) {
        stato = "mai iniziato";
        classe = "";
        colore = "var(--muted)";
      } else if (b.nextDue) {
        stato = whenShort(b.nextDue);
        classe = "pari";
        colore = "var(--accent)";
      } else {
        stato = "in pari";
        classe = "pari";
        colore = "var(--accent)";
      }

      html += `
        <div class="card bank tap" data-bank="${esc(b.id)}">
          ${anello(b.pctAssodate, "mini", colore)}
          <div class="corpo">
            <div class="title">${esc(b.title)}</div>
            <div class="chips" style="gap:8px;margin-top:3px">
              <span class="small">${esc(bits.join(" · "))}</span>
              <span class="pill ${classe}">${esc(stato)}</span>
            </div>
          </div>
          <button class="icon-btn" data-menu="${esc(b.id)}" title="Opzioni"
                  style="width:32px;height:32px">${icona("altro", 18)}</button>
        </div>`;
    }
    html += `</div>`;
  }
  box.innerHTML = html;

  $$("[data-bank]", box).forEach((el) => {
    el.onclick = (e) => {
      if (e.target.closest("[data-menu]")) return;
      startFromBank(banks.find((b) => b.id === el.dataset.bank));
    };
  });
  $$("[data-menu]", box).forEach((el) => {
    el.onclick = (e) => {
      e.stopPropagation();
      bankMenu(banks.find((b) => b.id === el.dataset.menu));
    };
  });
}

function startFromBank(bank) {
  if (!bank || !bank.count) {
    toast("Questo banco non contiene domande valide.");
    return;
  }
  const options = [["tutte", `Tutte le domande (${bank.count})`]];
  if (bank.due) options.push(["ripasso", `Da ripassare oggi (${bank.due})`]);
  if (bank.unseen && bank.unseen < bank.count) {
    options.push(["nuove", `Solo quelle mai viste (${bank.unseen})`]);
  }
  if (bank.flags) options.push(["contrassegnate", `Solo le contrassegnate (${bank.flags})`]);
  if (bank.tags.length) options.push(["argomento", "Scegli un argomento…"]);

  if (options.length === 1) {
    startQuiz(bank, "tutte");
    return;
  }
  sheet(bank.title, options.map(([value, label]) =>
    `<button class="btn ghost wide mt" data-filter="${value}">${esc(label)}</button>`).join(""),
    (root, close) => {
      $$("[data-filter]", root).forEach((b) => {
        b.onclick = () => {
          close();
          if (b.dataset.filter === "argomento") chooseTag(bank);
          else startQuiz(bank, b.dataset.filter);
        };
      });
    });
}

function chooseTag(bank) {
  sheet("Argomento", bank.tags.map((t) =>
    `<button class="btn ghost wide mt" data-tag="${esc(t)}">#${esc(t)}</button>`).join(""),
    (root, close) => {
      $$("[data-tag]", root).forEach((b) => {
        b.onclick = () => { close(); startQuiz(bank, "argomento", b.dataset.tag); };
      });
    });
}

async function startQuiz(bank, filter, tag) {
  const questions = await bankQuestions(bank.id);
  const s = buildSession(bank, questions, filter, tag);
  if (!s.items.length) {
    toast("Non ci sono domande da ripassare in questo banco.");
    return;
  }
  session = s;
  go({ name: "quiz" });
}

function bankMenu(bank) {
  sheet(bank.title, `
    <button class="btn ghost wide mt" data-act="export">Esporta</button>
    ${bank.bundled ? "" : `<button class="btn ghost wide mt" data-act="rename">Rinomina</button>`}
    <button class="btn danger wide mt" data-act="delete">${bank.bundled ? "Nascondi" : "Elimina"}</button>
  `, (root, close) => {
    $("[data-act=export]", root).onclick = async () => {
      close();
      downloadFile(stemOf(bank.id) + ".txt", await bankText(bank.id), "text/plain");
    };
    const ren = $("[data-act=rename]", root);
    if (ren) {
      ren.onclick = () => {
        close();
        const name = prompt("Nuovo nome", bank.title);
        if (!name) return;
        const fresh = saveUserBank(name, store.user[bank.id]);
        delete store.user[bank.id];
        cache.delete(bank.id);
        renameBankData(bank.id, fresh);
        renderHome();
      };
    }
    $("[data-act=delete]", root).onclick = () => {
      close();
      const msg = bank.bundled
        ? `Nascondere «${bank.title}» dalla lista? È un quiz preinstallato: potrai rimetterlo dalle impostazioni.`
        : `Eliminare «${bank.title}»? Si perdono anche i suoi risultati.`;
      if (!confirm(msg)) return;
      if (bank.bundled) store.hidden.push(bank.id);
      else delete store.user[bank.id];
      forgetBank(bank.id);
      cache.delete(bank.id);
      save();
      renderHome();
    };
  });
}

function renameBankData(oldId, newId) {
  for (const map of [store.srs, store.flags, store.notes, store.tags]) {
    if (map[oldId]) {
      map[newId] = map[oldId];
      delete map[oldId];
    }
  }
  for (const h of store.history) {
    if (h.bank === oldId) {
      h.bank = newId;
      h.title = titleOf(newId);
    }
  }
  save();
}

// ------------------------------------------------------------------- quiz

function renderQuiz() {
  if (!session || !session.items.length) return go({ name: "home" }, false);
  const it = session.items[session.index];
  const revealed = it.revealed && !isExam(session);
  const last = session.index === session.items.length - 1;

  setHeader(session.bankTitle,
    `<button class="icon-btn ${it.flagged ? "flagged" : ""}" id="a-flag"
             title="Contrassegna">${icona("bandiera")}</button>
     <button class="icon-btn accent" id="a-map"
             title="Mappa domande">${icona("mappa")}</button>`,
    true);
  btnBack.title = "Esci dal quiz";

  const answersHtml = it.order.map((original) => {
    const isCorrect = it.q.correct.includes(original);
    const picked = it.selected.includes(original);
    let cls = "answer";
    if (revealed) {
      if (isCorrect) cls += " right";
      else if (picked) cls += " wrong";
      else cls += " dim";
    } else if (picked) {
      cls += " picked";
    }
    return `<button class="${cls}" data-a="${original}" ${revealed ? "disabled" : ""}>${esc(it.q.answers[original])}</button>`;
  }).join("");

  const needsConfirm = !isExam(session) && !revealed && it.q.multi && itemAnswered(it);
  const nota = noteOf(session.bankId, it.q.id);

  view.innerHTML = `
    <div class="spread small">
      <span>${session.index + 1} / ${session.items.length}</span>
      <span>${answeredCount(session)} risposte date</span>
    </div>
    <div class="progress"><div style="width:${(answeredCount(session) / session.items.length) * 100}%"></div></div>

    <div class="question">${esc(it.q.text)}</div>
    ${it.q.multi ? `<div class="small" style="color:var(--warn);margin-bottom:8px">
      Più risposte corrette: scegli tutte quelle giuste, poi conferma.</div>` : ""}

    <div class="answers">${answersHtml}</div>

    ${revealed ? `
      <div class="feedback ${itemRight(it) ? "ok" : "ko"}">
        <b>${itemRight(it) ? "Risposta esatta" : "Risposta sbagliata"}</b>
        ${it.q.explanation ? `<div style="margin-top:4px">${esc(it.q.explanation)}</div>` : ""}
        <div id="q-note" style="margin-top:8px;font-style:italic;cursor:pointer;${nota ? "" : "opacity:.7"}">
          ${nota ? esc(nota) : "Aggiungi una nota"}
        </div>
      </div>` : ""}

    <div class="quiz-nav">
      ${session.index > 0 ? `<button class="btn ghost" id="prev">Indietro</button>` : ""}
      <div class="grow"></div>
      ${needsConfirm ? `<button class="btn ghost" id="confirm">Conferma risposta</button>` : ""}
      <button class="btn" id="next">${last ? (isExam(session) ? "Consegna" : "Termina") : "Avanti"}</button>
    </div>
  `;

  $$("[data-a]", view).forEach((b) => {
    b.onclick = () => onAnswer(it, Number(b.dataset.a));
  });
  if ($("#prev")) $("#prev").onclick = () => { session.index--; render(); };
  if ($("#confirm")) $("#confirm").onclick = () => reveal(it);
  if ($("#q-note")) $("#q-note").onclick = () => editNote(session.bankId, it.q, render);
  $("#next").onclick = () => {
    if (last) askFinish();
    else { session.index++; render(); }
  };
  $("#a-flag").onclick = () => {
    const set = setOf(store.flags, session.bankId);
    if (set.has(it.q.id)) set.delete(it.q.id);
    else set.add(it.q.id);
    it.flagged = set.has(it.q.id);
    writeSet(store.flags, session.bankId, set);
    save();
    render();
  };
  $("#a-map").onclick = showMap;

  startTicker();
}

function onAnswer(it, original) {
  if (it.q.multi) {
    const i = it.selected.indexOf(original);
    if (i >= 0) it.selected.splice(i, 1);
    else it.selected.push(original);
  } else {
    it.selected = [original];
  }
  if (isExam(session) || it.q.multi) {
    render();
    return;
  }
  reveal(it);
}

function reveal(it) {
  if (!itemAnswered(it) || isExam(session)) return;
  it.revealed = true;
  if (navigator.vibrate) navigator.vibrate(itemRight(it) ? 20 : [0, 30, 70, 30]);
  render();

  const last = session.index === session.items.length - 1;
  if (store.settings.autoNext && itemRight(it) && !it.q.explanation && !last) {
    setTimeout(() => {
      if (current.name === "quiz" && session && session.index < session.items.length - 1) {
        session.index++;
        render();
      }
    }, 700);
  } else if (session.items.every(itemAnswered)) {
    setTimeout(() => { if (current.name === "quiz") finishQuiz(); }, 900);
  }
}

function askFinish() {
  const answered = answeredCount(session);
  if (answered === session.items.length
    || confirm(`Consegnare il quiz? Hai risposto a ${answered} domande su ${session.items.length}.`)) {
    finishQuiz();
  }
}

function finishQuiz() {
  stopTicker();
  recordResult(session);
  go({ name: "result" });
}

// timer e cronometro ----------------------------------------------------

function startTicker() {
  stopTicker();
  paintTimer();
  ticker = setInterval(() => {
    if (!session || current.name !== "quiz") return stopTicker();
    session.elapsed++;
    if (session.timerSeconds > 0) {
      session.secondsLeft--;
      paintTimer();
      if (session.secondsLeft <= 0) {
        session.timedOut = true;
        stopTicker();
        toast("Tempo scaduto: il quiz è stato consegnato.");
        finishQuiz();
      }
    }
  }, 1000);
}

function stopTicker() {
  if (ticker) clearInterval(ticker);
  ticker = null;
}

function paintTimer() {
  if (!session || session.timerSeconds <= 0) {
    headerTimer.hidden = true;
    return;
  }
  headerTimer.hidden = false;
  headerTimer.textContent = clock(session.secondsLeft);
  headerTimer.className = session.secondsLeft <= 60 ? "danger"
    : session.secondsLeft <= 300 ? "warn" : "";
}

function showMap() {
  const cells = session.items.map((it, i) => {
    let cls = "";
    if (it.revealed && !isExam(session)) cls = itemRight(it) ? "right" : "wrong";
    else if (itemAnswered(it)) cls = "done";
    if (it.flagged) cls += " flag";
    if (i === session.index) cls += " here";
    return `<button class="${cls}" data-go="${i}">${i + 1}</button>`;
  }).join("");

  sheet("Mappa domande",
    `<div class="small">${answeredCount(session)} risposte su ${session.items.length}</div>
     <div class="map">${cells}</div>
     <button class="btn wide mt" id="map-finish">${isExam(session) ? "Consegna" : "Termina"}</button>`,
    (root, close) => {
      $$("[data-go]", root).forEach((b) => {
        b.onclick = () => { close(); session.index = Number(b.dataset.go); render(); };
      });
      $("#map-finish", root).onclick = () => { close(); askFinish(); };
    });
}

// --------------------------------------------------------------- risultato

function renderResult() {
  if (!session) return go({ name: "home" }, false);
  const total = session.items.length;
  const right = correctCount(session);
  const p = sessionPct(session);
  const pass = store.settings.passPct;
  const color = p >= pass ? "var(--ok)" : (p >= pass - 15 ? "var(--warn)" : "var(--ko)");
  const wrongCount = total - right;

  setHeader("Risultato", "", true);

  const review = session.items.map((it, i) => {
    const ok = itemRight(it);
    const lines = it.order
      .filter((o) => it.q.correct.includes(o) || it.selected.includes(o))
      .map((o) => {
        const isCorrect = it.q.correct.includes(o);
        const picked = it.selected.includes(o);
        return `<div class="line" style="color:${isCorrect ? "var(--ok)" : "var(--ko)"};
          font-weight:${picked ? 700 : 400}">${isCorrect ? "✓" : "✗"} ${esc(it.q.answers[o])}</div>`;
      }).join("");
    const nota = noteOf(session.bankId, it.q.id);
    const tags = tagsOf(session.bankId, it.q);
    const card = cardOf(session.bankId, it.q.id);
    return `
      <div class="card review">
        <div>
          <span class="review-badge" style="background:${ok ? "var(--ok-bg)" : "var(--ko-bg)"};
            color:${ok ? "var(--ok)" : "var(--ko)"}">${i + 1}</span>
          <b>${esc(it.q.text)}</b>
        </div>
        <div style="margin-top:8px">
          ${lines}
          ${itemAnswered(it) ? "" : `<div class="small">Senza risposta</div>`}
        </div>
        ${it.q.explanation ? `<div class="small" style="margin-top:8px">Spiegazione: ${esc(it.q.explanation)}</div>` : ""}
        ${nota ? `<div style="margin-top:8px;font-style:italic;font-size:14px">${esc(nota)}</div>` : ""}
        ${tags.length ? `<div style="margin-top:6px;font-size:12px;color:var(--accent)">${
          tags.map((t) => "#" + esc(t)).join("  ")}</div>` : ""}
        <div class="spread" style="margin-top:4px">
          <div>
            <button class="btn text" data-note="${i}" style="padding:6px 8px">Nota</button>
            <button class="btn text" data-tags="${i}" style="padding:6px 8px">Argomenti</button>
          </div>
          <span class="small">${card
            ? `Scatola ${card.box} di ${SCATOLA_MAX} · torna ${whenShort(card.due)}`
            : "Mai affrontata"}</span>
        </div>
      </div>`;
  }).join("");

  view.innerHTML = `
    <div class="card center">
      <div class="small">Quiz completato</div>
      <div class="score" style="color:${color}">${p}%</div>
      <div class="bold" style="color:${color}">${p >= pass ? "Superato" : "Non superato"}</div>
      <div class="mt">${right} risposte esatte su ${total}</div>
      <div class="small">Soglia di superamento: ${pass}% · Tempo impiegato: ${duration(session.elapsed)}</div>
      ${session.timedOut ? `<div class="feedback ko mt">Tempo scaduto: il quiz è stato consegnato.</div>` : ""}
      ${wrongCount ? `<button class="btn wide mt" id="r-wrong">Rifai le sbagliate (${wrongCount})</button>` : ""}
      <div class="row mt">
        <button class="btn ghost grow" id="r-all">Rifai tutto</button>
        <button class="btn ghost grow" id="r-home">Torna alla lista</button>
      </div>
      <button class="btn text mt" id="r-share">Condividi</button>
    </div>

    <h2 class="section">Rivedi le domande</h2>
    ${review}
  `;

  if (wrongCount) {
    $("#r-wrong").onclick = () => {
      const items = session.items.filter((it) => !itemRight(it)).map((it) => ({
        q: it.q,
        order: shuffle(it.order.slice()),
        selected: [],
        flagged: it.flagged,
        revealed: false,
      }));
      session = Object.assign({}, session, {
        items, index: 0, elapsed: 0, timedOut: false,
        timerSeconds: 0, secondsLeft: 0,
      });
      go({ name: "quiz" });
    };
  }
  $("#r-all").onclick = async () => {
    const bank = (await allBanks()).find((b) => b.id === session.bankId);
    if (!bank) return go({ name: "home" });
    startQuiz(bank, "tutte");
  };
  $("#r-home").onclick = () => go({ name: "home" });
  $("#r-share").onclick = () => {
    const text = `LanQuiz — ${session.bankTitle}: ${p}% (${right}/${total})`;
    if (navigator.share) navigator.share({ text }).catch(() => {});
    else { navigator.clipboard.writeText(text); toast("Risultato copiato."); }
  };
  $$("[data-note]", view).forEach((b) => {
    b.onclick = () => editNote(session.bankId,
      session.items[Number(b.dataset.note)].q, renderResult);
  });
  $$("[data-tags]", view).forEach((b) => {
    b.onclick = () => editTags(session.bankId,
      session.items[Number(b.dataset.tags)].q, renderResult);
  });
}

// -------------------------------------------------------- note e argomenti

function editNote(bankId, q, after) {
  sheet("La tua nota", `
    <div class="small mt">Perché hai sbagliato? Te la ritrovi la volta dopo.</div>
    <textarea id="note-text" style="margin-top:8px">${esc(noteOf(bankId, q.id))}</textarea>
    <button class="btn wide mt" id="note-save">Salva</button>
  `, (root, close) => {
    $("#note-text", root).focus();
    $("#note-save", root).onclick = () => {
      setNote(bankId, q.id, $("#note-text", root).value);
      close();
      if (after) after();
    };
  });
}

function editTags(bankId, q, after) {
  sheet("Argomenti della domanda", `
    <div class="small mt">Separati da virgola: cloud storage, iam, networking</div>
    ${q.tags.length ? `<div class="small">Dal file: ${esc(q.tags.join(", "))}</div>` : ""}
    <input type="text" id="tag-text" style="margin-top:8px" value="${esc(userTagsOf(bankId, q.id).join(" "))}">
    <button class="btn wide mt" id="tag-save">Salva</button>
  `, (root, close) => {
    $("#tag-text", root).focus();
    $("#tag-save", root).onclick = () => {
      setUserTags(bankId, q.id, parseTagInput($("#tag-text", root).value));
      close();
      if (after) after();
    };
  });
}

// ------------------------------------------------------------ statistiche

function renderStats() {
  setHeader("Statistiche", "", true);
  const history = store.history.slice().reverse();

  if (!history.length) {
    view.innerHTML = `<div class="card small">Nessun tentativo registrato: completa un quiz e torna qui.</div>`;
    return;
  }

  const pass = store.settings.passPct;
  const totals = history.reduce((acc, h) => ({
    questions: acc.questions + h.total,
    correct: acc.correct + h.correct,
    seconds: acc.seconds + (h.seconds || 0),
  }), { questions: 0, correct: 0, seconds: 0 });
  const average = totals.questions ? Math.round((totals.correct / totals.questions) * 100) : 0;
  const tint = (v) => (v >= pass ? "var(--ok)" : (v >= pass - 15 ? "var(--warn)" : "var(--ko)"));

  const perBank = new Map();
  for (const h of history) {
    if (!perBank.has(h.bank)) perBank.set(h.bank, []);
    perBank.get(h.bank).push(h);
  }

  let banksHtml = "";
  const adesso = Date.now();
  for (const [bankId, list] of perBank) {
    const values = list.map((h) => pct(h));
    const avg = Math.round(values.reduce((a, b) => a + b, 0) / values.length);
    const best = Math.max(...values);
    const cards = store.srs[bankId] || {};
    const due = Object.values(cards).filter((c) => c.due <= adesso).length;
    banksHtml += `
      <div class="card">
        <div class="bold">${esc(list[0].title)}</div>
        <div class="small">${list.length} tentativi · media ${avg}% · record ${best}%</div>
        <div class="bar"><div style="width:${avg}%;background:${tint(avg)}"></div></div>
        ${due ? `<div class="small" style="color:var(--warn);margin-top:6px">${due} da ripassare oggi</div>` : ""}
      </div>`;
  }

  const recent = history.slice(0, 15).map((h) => `
    <div class="spread" style="padding:6px 0">
      <div>
        <div style="font-size:14px">${esc(h.title)}</div>
        <div class="small">${whenText(h.ts)} · ${h.mode} · ${duration(h.seconds || 0)}</div>
      </div>
      <b style="color:${tint(pct(h))}">${pct(h)}%</b>
    </div>`).join("");

  view.innerHTML = `
    <div class="card">
      <b class="titolo-card">Riepilogo generale</b>
      <div class="spread mt"><span class="small">Tentativi</span><b>${history.length}</b></div>
      <div class="spread"><span class="small">Domande affrontate</span><b>${totals.questions}</b></div>
      <div class="spread"><span class="small">Media</span><b style="color:${tint(average)}">${average}%</b></div>
      <div class="spread"><span class="small">Tempo totale</span><b>${duration(totals.seconds)}</b></div>
    </div>

    <h2 class="section">Per banco</h2>
    ${banksHtml}

    <div id="per-tag"></div>

    <h2 class="section">Ultimi tentativi</h2>
    <div class="card">${recent}</div>

    <button class="btn ghost wide mt" id="csv">Esporta in CSV</button>
    <button class="btn danger wide mt" id="reset">Azzera statistiche</button>
  `;

  paintTagStats(tint);

  $("#csv").onclick = () => {
    let csv = "quiz;data;modalita;corrette;totale;percentuale;secondi\n";
    for (const h of history) {
      csv += [h.title.replace(/;/g, ","), whenText(h.ts), h.mode,
        h.correct, h.total, pct(h), h.seconds || 0].join(";") + "\n";
    }
    downloadFile("lanquiz-statistiche.csv", csv, "text/csv");
  };
  $("#reset").onclick = () => {
    if (!confirm("Cancellare storico, medie e stato del ripasso?\n\nLe note e gli argomenti che hai scritto restano.")) return;
    store.history = [];
    store.srs = {};
    save();
    renderStats();
  };
}

/** Su quali argomenti vai peggio: si legge il conteggio ok/ko di ogni domanda. */
async function paintTagStats(tint) {
  if (!$("#per-tag")) return;
  const perTag = new Map();
  for (const bank of await allBanks()) {
    const cards = store.srs[bank.id];
    if (!cards) continue;
    for (const q of await bankQuestions(bank.id)) {
      const card = cards[q.id];
      if (!card) continue;
      for (const tag of tagsOf(bank.id, q)) {
        const stat = perTag.get(tag) || { ok: 0, ko: 0 };
        stat.ok += card.ok;
        stat.ko += card.ko;
        perTag.set(tag, stat);
      }
    }
  }
  const rows = Array.from(perTag.entries())
    .map(([tag, s]) => ({
      tag,
      total: s.ok + s.ko,
      pct: s.ok + s.ko ? Math.round((s.ok * 100) / (s.ok + s.ko)) : 0,
    }))
    .sort((a, b) => a.pct - b.pct);   // prima quelli che vanno peggio

  const box = $("#per-tag");
  if (!box) return;
  box.innerHTML = `<h2 class="section">Per argomento</h2><div class="card">${
    rows.length
      ? rows.map((r) => `<div class="spread" style="padding:4px 0">
          <span class="small">#${esc(r.tag)}</span>
          <b style="color:${tint(r.pct)}">${r.total} risposte · ${r.pct}% esatte</b>
        </div>`).join("")
      : `<div class="small">Nessun argomento ancora assegnato: aggiungine dal riepilogo
         di un quiz, oppure scrivili nei file con @argomento.</div>`
  }</div>`;
}

// ------------------------------------------------------------ impostazioni

function renderSettings() {
  setHeader("Impostazioni", "", true);
  const s = store.settings;
  const themeChip = (value, label) =>
    `<button class="chip" data-pick-theme="${value}" aria-pressed="${s.theme === value}">${label}</button>`;

  view.innerHTML = `
    <div class="card">
      <b class="titolo-card">Aspetto</b>
      <label class="field">Tema</label>
      <div class="chips">
        ${themeChip("sistema", "Come il sistema")}
        ${themeChip("chiaro", "Chiaro")}
        ${themeChip("scuro", "Scuro")}
      </div>
    </div>

    <div class="card">
      <b class="titolo-card">Valutazione</b>
      <label class="field" id="pass-label">Soglia di superamento: ${s.passPct}%</label>
      <input type="range" min="30" max="100" step="5" value="${s.passPct}" id="pass" style="width:100%">
    </div>

    <div class="card">
      <b class="titolo-card">Comportamento</b>
      <label class="switch">
        <input type="checkbox" id="auto" ${s.autoNext ? "checked" : ""}>
        Passa da sola alla domanda dopo
      </label>
    </div>

    <div class="card">
      <b class="titolo-card">Backup e trasferimento</b>
      <div class="small mt">Un solo file con quiz importati, stato del ripasso, note,
        argomenti e statistiche: serve a passare dal telefono a qui e viceversa.</div>
      <button class="btn ghost wide mt" id="bk-export">Esporta backup</button>
      <label class="btn ghost wide mt" style="display:block;text-align:center;cursor:pointer">
        Importa backup
        <input type="file" id="bk-file" accept=".json,application/json" hidden>
      </label>
    </div>

    <div class="card">
      <b class="titolo-card">Quiz preinstallati</b>
      ${store.hidden.length
        ? `<button class="btn ghost wide mt" id="restore">Rimetti in lista quelli nascosti (${store.hidden.length})</button>`
        : `<div class="small mt">Nessun quiz nascosto.</div>`}
    </div>

    <div class="card">
      <b class="titolo-card">Versione</b>
      <div class="small mt">Web app ${VERSION}. Si aggiorna da sola a ogni apertura con rete.</div>
      <div class="small mt">
        App Android: <a href="https://github.com/${REPO}/releases/latest">scarica l'APK</a>
      </div>
    </div>
  `;

  // attributo dedicato: "data-theme" sta gia su <html> e il selettore lo pescherebbe
  $$("[data-pick-theme]", view).forEach((c) => {
    c.onclick = () => {
      store.settings.theme = c.dataset.pickTheme;
      save();
      applyTheme();
      renderSettings();
    };
  });
  const pass = $("#pass");
  pass.oninput = () => {
    store.settings.passPct = Number(pass.value);
    $("#pass-label").textContent = `Soglia di superamento: ${pass.value}%`;
    save();
  };
  $("#auto").onchange = (e) => { store.settings.autoNext = e.target.checked; save(); };
  $("#bk-export").onclick = exportBackup;
  $("#bk-file").onchange = async (e) => {
    const file = e.target.files[0];
    if (file) importBackup(await file.text());
    e.target.value = "";
  };
  if ($("#restore")) {
    $("#restore").onclick = () => { store.hidden = []; save(); renderSettings(); };
  }
}

// ----------------------------------------------------------------- backup

/* Stesso formato letto e scritto dall'app Android (Store.exportAll). */

function exportBackup() {
  const data = {
    app: "LanQuiz",
    formato: 1,
    esportato: Date.now(),
    settings: store.settings,
    history: store.history,
    hidden: store.hidden,
    srs: store.srs,
    flags: store.flags,
    notes: store.notes,
    tags: store.tags,
    banks: store.user,
  };
  downloadFile(`lanquiz-backup-${new Date().toISOString().slice(0, 10)}.json`,
    JSON.stringify(data, null, 2), "application/json");
}

function importBackup(text) {
  let data;
  try {
    data = JSON.parse(text);
  } catch (e) {
    toast("Backup non importato: file illeggibile.");
    return;
  }
  if (data.app !== "LanQuiz") {
    toast("Backup non importato: non è un backup di LanQuiz.");
    return;
  }
  const unisci = confirm(
    "«OK» unisce il backup a quello che hai già: sul ripasso vince la versione "
    + "aggiornata più di recente.\n\n«Annulla» sostituisce tutto con il contenuto del file.");

  if (!unisci) store = emptyStore();
  if (data.settings) store.settings = Object.assign({}, DEFAULTS, data.settings);

  let aggiunti = 0;
  for (const [id, content] of Object.entries(data.banks || {})) {
    if (store.user[id] === undefined) {
      store.user[id] = content;
      store.migrated[id] = true;
      aggiunti++;
    }
  }

  const visti = new Set(store.history.map((h) => h.bank + "@" + h.ts));
  for (const h of data.history || []) {
    if (!visti.has(h.bank + "@" + h.ts)) {
      visti.add(h.bank + "@" + h.ts);
      store.history.push(h);
    }
  }
  store.history.sort((a, b) => a.ts - b.ts);
  store.history = store.history.slice(-500);

  for (const [bankId, cards] of Object.entries(data.srs || {})) {
    const mine = store.srs[bankId] || (store.srs[bankId] = {});
    for (const [q, card] of Object.entries(cards)) {
      // vince la voce toccata più di recente: è quella che sa come stai davvero
      if (!mine[q] || (card.last || 0) > (mine[q].last || 0)) mine[q] = card;
    }
  }
  for (const [bankId, map] of Object.entries(data.notes || {})) {
    const mine = store.notes[bankId] || (store.notes[bankId] = {});
    for (const [q, testo] of Object.entries(map)) {
      if (!mine[q]) mine[q] = testo;
    }
  }
  for (const [bankId, map] of Object.entries(data.tags || {})) {
    const mine = store.tags[bankId] || (store.tags[bankId] = {});
    for (const [q, tags] of Object.entries(map)) {
      mine[q] = Array.from(new Set((mine[q] || []).concat(tags)));
    }
  }
  for (const [bankId, ids] of Object.entries(data.flags || {})) {
    store.flags[bankId] = Array.from(new Set((store.flags[bankId] || []).concat(ids)));
  }
  store.hidden = Array.from(new Set(store.hidden.concat(data.hidden || [])));

  cache.clear();
  save();
  applyTheme();
  toast(`Backup importato: ${aggiunti} quiz aggiunti.`);
  go({ name: "home" });
}

// ---------------------------------------------------------------- importa

function renderImport() {
  setHeader("Importa quiz", "", true);
  view.innerHTML = `
    <div class="card">
      <b class="titolo-card">Da un file</b>
      <div class="small mt">File .txt, .csv o .json già nel formato del quiz.</div>
      <input type="file" id="file" accept=".txt,.csv,.json,text/plain,text/csv,application/json" class="mt">
    </div>

    <div class="card">
      <b class="titolo-card">Incolla il testo</b>
      <label class="field">Nome del quiz</label>
      <input type="text" id="name" placeholder="Il mio quiz">
      <label class="field">Una domanda per riga</label>
      <textarea id="text" placeholder="domanda;risposta esatta;errata;errata"></textarea>
      <button class="btn wide mt" id="do-text">Importa</button>
    </div>

    <div class="card">
      <b class="titolo-card">Da un indirizzo web</b>
      <label class="field">Indirizzo</label>
      <input type="url" id="url" placeholder="https://…">
      <button class="btn ghost wide mt" id="do-url">Importa</button>
      <div class="small mt">Funziona solo se il sito consente la lettura da altri domini (CORS).</div>
    </div>

    <div class="card">
      <b class="titolo-card">Formato dei file</b>
      <div class="small mt">
        Una domanda per riga, campi separati da punto e virgola:<br>
        <code>domanda;risposta esatta;errata;errata</code><br><br>
        Estensioni facoltative:<br>
        • un <code>*</code> davanti a una risposta la marca come corretta, così una
        domanda può averne più di una<br>
        • un campo che inizia con <code>##</code> è la spiegazione mostrata dopo la risposta<br>
        • un campo che inizia con <code>@</code> elenca gli argomenti della domanda<br>
        • le righe che iniziano con <code>#</code> sono commenti<br><br>
        Senza asterischi vale la regola di sempre: la prima risposta è quella esatta.
      </div>
    </div>
  `;

  $("#file").onchange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    finishImport(await file.text(), file.name);
  };
  $("#do-text").onclick = () => {
    finishImport($("#text").value, $("#name").value || "quiz");
  };
  $("#do-url").onclick = async () => {
    const url = $("#url").value.trim();
    if (!url) return;
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error("HTTP " + res.status);
      const name = url.split("/").pop().split("?")[0] || "quiz";
      finishImport(await res.text(), name);
    } catch (err) {
      toast("Importazione non riuscita: " + err.message);
    }
  };
}

function finishImport(content, name) {
  try {
    const id = saveUserBank(name, content);
    toast(`Importato «${titleOf(id)}»: ${parseBank(content).length} domande.`);
    go({ name: "home" });
  } catch (err) {
    toast("Importazione non riuscita: " + err.message);
  }
}

function downloadFile(name, content, type) {
  const blob = new Blob([content], { type: type + ";charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

// ---------------------------------------------------------------- pannello

function sheet(title, bodyHtml, wire) {
  const overlay = document.createElement("div");
  overlay.className = "overlay";
  overlay.innerHTML = `<div class="sheet">
    <b class="titolo-card">${esc(title)}</b>
    ${bodyHtml}
    <button class="btn text wide mt" data-close>Chiudi</button>
  </div>`;
  const close = () => overlay.remove();
  overlay.onclick = (e) => { if (e.target === overlay) close(); };
  document.body.appendChild(overlay);
  $("[data-close]", overlay).onclick = close;
  if (wire) wire(overlay, close);
}

// ------------------------------------------------------------------ avvio

async function boot() {
  applyTheme();
  document.getElementById("footer-version").textContent = VERSION;
  history.replaceState({ name: "home" }, "", "");

  try {
    bundledIds = await (await fetch("banks.json")).json();
  } catch (e) {
    bundledIds = [];
  }
  render();

  registraServiceWorker();
}

/**
 * Il banner compare solo quando c'è davvero una versione ferma in attesa: la
 * pagina era già controllata da un service worker (quindi non è la prima
 * visita) e quello nuovo è installato ma non ancora attivo. Chi tocca
 * «Aggiorna» lo fa partire, e la pagina si ricarica quando il nuovo prende il
 * controllo — non un istante prima, altrimenti si ricaricherebbe sulla vecchia.
 */
function registraServiceWorker() {
  if (!("serviceWorker" in navigator)) return;
  const banner = document.getElementById("update-banner");
  const aggiorna = document.getElementById("update-reload");
  const chiudi = document.getElementById("update-dismiss");
  const eraControllata = !!navigator.serviceWorker.controller;

  if (chiudi) chiudi.onclick = () => { banner.hidden = true; };

  const proponi = (attesa) => {
    if (!attesa || !eraControllata || !banner.hidden) return;
    banner.hidden = false;
    aggiorna.onclick = () => {
      aggiorna.disabled = true;
      navigator.serviceWorker.addEventListener("controllerchange",
        () => location.reload(), { once: true });
      attesa.postMessage("skip-waiting");
    };
  };

  navigator.serviceWorker.register("sw.js").then((reg) => {
    proponi(reg.waiting);
    reg.addEventListener("updatefound", () => {
      const fresh = reg.installing;
      if (!fresh) return;
      fresh.addEventListener("statechange", () => {
        if (fresh.state === "installed") proponi(reg.waiting);
      });
    });
  }).catch(() => {
    /* niente offline: l'app funziona lo stesso finché c'è rete */
  });
}

boot();
