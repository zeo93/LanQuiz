/* LanQuiz web — la stessa app Android in versione PWA.
   Tutto gira nel browser: i banchi preinstallati arrivano da banks/, quelli
   importati e i risultati stanno in localStorage. Nessun server. */
"use strict";

const VERSION = "1.0";
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

function clock(seconds) {
  const s = Math.max(0, Math.round(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  const pad = (n) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(r)}` : `${pad(m)}:${pad(r)}`;
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

function loadStore() {
  let data = {};
  try {
    data = JSON.parse(localStorage.getItem(STORE_KEY) || "{}");
  } catch (e) {
    data = {};
  }
  return {
    settings: Object.assign({}, DEFAULTS, data.settings || {}),
    history: data.history || [],
    wrong: data.wrong || {},
    flags: data.flags || {},
    hidden: data.hidden || [],
    resume: data.resume || null,
    user: data.user || {},
  };
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
   · # commento  ·  separatore ; poi tab poi virgola  ·  JSON per l'export. */

function qid(text) {
  const norm = text.trim().replace(/\s+/g, " ").toLowerCase();
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  for (let i = 0; i < norm.length; i++) {
    h1 = Math.imul(h1 ^ norm.charCodeAt(i), 0x01000193) >>> 0;
    h2 = Math.imul(h2 + norm.charCodeAt(i), 0x85ebca6b) >>> 0;
  }
  return h1.toString(16).padStart(8, "0") + h2.toString(16).padStart(8, "0");
}

function makeQuestion(text, answers, correct, explanation) {
  return {
    text, answers, correct,
    explanation: explanation || "",
    id: qid(text),
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
  let explanation = "";
  for (let i = 1; i < parts.length; i++) {
    let f = parts[i].trim();
    if (!f) continue;
    if (f.startsWith("##")) {
      explanation = f.slice(2).trim();
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
  return makeQuestion(text, answers, correct, explanation);
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
        return makeQuestion(o.q || o.text || "", answers, correct, o.explanation);
      })
      .filter((q) => q.text.trim() && q.answers.length >= 2);
  } catch (e) {
    return [];
  }
}

// ------------------------------------------------------------------ banchi

const cache = new Map();   // id -> domande già lette
let bundledIds = [];

function stemOf(id) {
  return id.replace(/\.[A-Za-z0-9]+$/, "");
}

function titleOf(id) {
  return stemOf(id).replace(/_/g, " ").trim();
}

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
  if (!cache.has(id)) cache.set(id, parseBank(await bankText(id)));
  return cache.get(id);
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
    const attempts = store.history.filter((h) => h.bank === id);
    banks.push({
      id,
      title: titleOf(id),
      category: categoryOf(id),
      bundled: store.user[id] === undefined,
      count: questions.length,
      best: attempts.length ? Math.max(...attempts.map((h) => pct(h))) : -1,
      wrong: setOf(store.wrong, id).size,
      flags: setOf(store.flags, id).size,
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
  cache.delete(unique);
  save();
  return unique;
}

function forgetBank(id) {
  delete store.wrong[id];
  delete store.flags[id];
  store.history = store.history.filter((h) => h.bank !== id);
  if (store.resume && store.resume.bankId === id) store.resume = null;
}

// --------------------------------------------------------------- sessione

const pct = (r) => (r.total > 0 ? Math.round((r.correct / r.total) * 100) : 0);

function buildSession(bank, questions, filter) {
  const s = store.settings;
  let pool = questions.slice();
  if (filter === "sbagliate") {
    const ids = setOf(store.wrong, bank.id);
    pool = pool.filter((q) => ids.has(q.id));
  } else if (filter === "contrassegnate") {
    const ids = setOf(store.flags, bank.id);
    pool = pool.filter((q) => ids.has(q.id));
  }
  if (s.shuffleQ) shuffle(pool);
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
const sessionPct = (s) => (s.items.length ? Math.round((correctCount(s) / s.items.length) * 100) : 0);

function recordResult(s) {
  store.history.push({
    bank: s.bankId, title: s.bankTitle, ts: Date.now(),
    correct: correctCount(s), total: s.items.length, mode: s.mode, seconds: s.elapsed,
  });
  while (store.history.length > 500) store.history.shift();

  const wrong = setOf(store.wrong, s.bankId);
  for (const it of s.items) {
    if (itemRight(it)) wrong.delete(it.q.id);
    else wrong.add(it.q.id);
  }
  writeSet(store.wrong, s.bankId, wrong);
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

function back() {
  history.back();
}

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
  parkSession();               // uscire dal quiz lo rende riprendibile
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
    `<button class="icon-btn accent" id="a-stats" title="Statistiche">&#9776;</button>
     <button class="icon-btn accent" id="a-import" title="Importa">&#43;</button>
     <button class="icon-btn accent" id="a-settings" title="Impostazioni">&#9881;</button>`,
    false);

  const s = store.settings;
  const chip = (group, value, label, on) =>
    `<button class="chip" data-group="${group}" data-value="${value}" aria-pressed="${on}">${label}</button>`;

  const countValue = [0, 10, 25, 50].includes(s.count) ? s.count : "custom";
  const timerValue = [0, 15, 30, 90].includes(s.timer) ? s.timer : "custom";

  view.innerHTML = `
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

    <input type="text" id="q" placeholder="Cerca un quiz…" value="${esc(homeQuery)}">

    <div class="card">
      <b style="color:var(--indigo)">Impostazioni sessione</b>

      <label class="field">Modalità</label>
      <div class="chips">
        ${chip("mode", "studio", "Studio", s.mode === "studio")}
        ${chip("mode", "esame", "Esame", s.mode === "esame")}
      </div>
      <div class="small" style="margin-top:4px">
        ${s.mode === "esame"
          ? "Nessun riscontro fino alla consegna."
          : "Risposta svelata subito, con spiegazione."}
      </div>

      <label class="field">Numero di domande</label>
      <div class="chips">
        ${chip("count", 0, "Tutte", countValue === 0)}
        ${chip("count", 10, "10", countValue === 10)}
        ${chip("count", 25, "25", countValue === 25)}
        ${chip("count", 50, "Prova esame", countValue === 50)}
        ${chip("count", "custom", countValue === "custom" ? s.count : "Altro…", countValue === "custom")}
      </div>

      <label class="field">Timer</label>
      <div class="chips">
        ${chip("timer", 0, "Nessuno", timerValue === 0)}
        ${chip("timer", 15, "15 min", timerValue === 15)}
        ${chip("timer", 30, "30 min", timerValue === 30)}
        ${chip("timer", 90, "90 min", timerValue === 90)}
        ${chip("timer", "custom", timerValue === "custom" ? s.timer + " min" : "Altro…", timerValue === "custom")}
      </div>

      <label class="switch">
        <input type="checkbox" id="sq" ${s.shuffleQ ? "checked" : ""}> Mescola le domande
      </label>
      <label class="switch">
        <input type="checkbox" id="sa" ${s.shuffleA ? "checked" : ""}> Mescola le risposte
      </label>
    </div>

    <div id="banks"><div class="small">Carico i quiz…</div></div>
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
    paintBanks();
  };

  $("#sq").onchange = (e) => { store.settings.shuffleQ = e.target.checked; save(); };
  $("#sa").onchange = (e) => { store.settings.shuffleA = e.target.checked; save(); };

  $$(".chip").forEach((c) => {
    c.onclick = () => onSettingChip(c.dataset.group, c.dataset.value);
  });

  paintBanks();
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
  }
  save();
  renderHome();
}

async function paintBanks() {
  const box = $("#banks");
  if (!box) return;
  const banks = (await allBanks()).filter((b) => {
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
      if (b.wrong) bits.push(`${b.wrong} da ripassare`);
      if (b.flags) bits.push(`${b.flags} contrassegnate`);
      html += `
        <div class="card bank tap" data-bank="${esc(b.id)}">
          <div class="spread">
            <div>
              <div class="title">${esc(b.title)}</div>
              <div class="small">${esc(bits.join(" · "))}</div>
            </div>
            <button class="icon-btn" data-menu="${esc(b.id)}" title="Opzioni">&#8942;</button>
          </div>
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

async function startFromBank(bank) {
  if (!bank || !bank.count) {
    toast("Questo banco non contiene domande valide.");
    return;
  }
  if (!bank.wrong && !bank.flags) {
    return startQuiz(bank, "tutte");
  }
  const options = [["tutte", `Tutte le domande (${bank.count})`]];
  if (bank.wrong) options.push(["sbagliate", `Solo quelle sbagliate (${bank.wrong})`]);
  if (bank.flags) options.push(["contrassegnate", `Solo le contrassegnate (${bank.flags})`]);
  sheet(bank.title, options.map(([value, label]) =>
    `<button class="btn ghost wide mt" data-filter="${value}">${esc(label)}</button>`).join(""),
    (root, close) => {
      $$("[data-filter]", root).forEach((b) => {
        b.onclick = () => { close(); startQuiz(bank, b.dataset.filter); };
      });
    });
}

async function startQuiz(bank, filter) {
  const questions = await bankQuestions(bank.id);
  const s = buildSession(bank, questions, filter);
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
      const text = await bankText(bank.id);
      downloadFile(stemOf(bank.id) + ".txt", text, "text/plain");
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
  if (store.wrong[oldId]) { store.wrong[newId] = store.wrong[oldId]; delete store.wrong[oldId]; }
  if (store.flags[oldId]) { store.flags[newId] = store.flags[oldId]; delete store.flags[oldId]; }
  for (const h of store.history) {
    if (h.bank === oldId) { h.bank = newId; h.title = titleOf(newId); }
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
    `<button class="icon-btn ${it.flagged ? "flagged" : ""}" id="a-flag" title="Contrassegna">&#9873;</button>
     <button class="icon-btn accent" id="a-map" title="Mappa domande">&#9638;</button>`,
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
      </div>` : ""}

    <div class="quiz-nav">
      ${session.index > 0 ? `<button class="btn ghost" id="prev">Indietro</button>` : ""}
      <div class="grow"></div>
      ${needsConfirm ? `<button class="btn ghost" id="confirm">Conferma risposta</button>` : ""}
      <button class="btn" id="next">${last ? (isExam(session) ? "Consegna" : "Termina") : "Avanti"}</button>
    </div>
  `;

  $$("[data-a]").forEach((b) => {
    b.onclick = () => onAnswer(it, Number(b.dataset.a));
  });
  if ($("#prev")) $("#prev").onclick = () => { session.index--; render(); };
  if ($("#confirm")) $("#confirm").onclick = () => reveal(it);
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

// timer e cronometro ---------------------------------------------------

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

// -------------------------------------------------------------- risultato

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
    const banks = await allBanks();
    const bank = banks.find((b) => b.id === session.bankId);
    if (!bank) return go({ name: "home" });
    startQuiz(bank, "tutte");
  };
  $("#r-home").onclick = () => go({ name: "home" });
  $("#r-share").onclick = () => {
    const text = `LanQuiz — ${session.bankTitle}: ${p}% (${right}/${total})`;
    if (navigator.share) navigator.share({ text }).catch(() => {});
    else { navigator.clipboard.writeText(text); toast("Risultato copiato."); }
  };
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
  for (const [bankId, list] of perBank) {
    const values = list.map((h) => pct(h));
    const avg = Math.round(values.reduce((a, b) => a + b, 0) / values.length);
    const best = Math.max(...values);
    const wrong = setOf(store.wrong, bankId).size;
    banksHtml += `
      <div class="card">
        <div class="bold">${esc(list[0].title)}</div>
        <div class="small">${list.length} tentativi · media ${avg}% · record ${best}%</div>
        <div class="bar"><div style="width:${avg}%;background:${tint(avg)}"></div></div>
        ${wrong ? `<div class="small" style="color:var(--warn);margin-top:6px">${wrong} da ripassare</div>` : ""}
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
      <b style="color:var(--indigo)">Riepilogo generale</b>
      <div class="spread mt"><span class="small">Tentativi</span><b>${history.length}</b></div>
      <div class="spread"><span class="small">Domande affrontate</span><b>${totals.questions}</b></div>
      <div class="spread"><span class="small">Media</span><b style="color:${tint(average)}">${average}%</b></div>
      <div class="spread"><span class="small">Tempo totale</span><b>${duration(totals.seconds)}</b></div>
    </div>

    <h2 class="section">Per banco</h2>
    ${banksHtml}

    <h2 class="section">Ultimi tentativi</h2>
    <div class="card">${recent}</div>

    <button class="btn ghost wide mt" id="csv">Esporta in CSV</button>
    <button class="btn danger wide mt" id="reset">Azzera statistiche</button>
  `;

  $("#csv").onclick = () => {
    let csv = "quiz;data;modalita;corrette;totale;percentuale;secondi\n";
    for (const h of history) {
      csv += [h.title.replace(/;/g, ","), whenText(h.ts), h.mode,
        h.correct, h.total, pct(h), h.seconds || 0].join(";") + "\n";
    }
    downloadFile("lanquiz-statistiche.csv", csv, "text/csv");
  };
  $("#reset").onclick = () => {
    if (!confirm("Cancellare storico, medie e lista delle domande da ripassare?")) return;
    store.history = [];
    store.wrong = {};
    save();
    renderStats();
  };
}

// ------------------------------------------------------------ impostazioni

function renderSettings() {
  setHeader("Impostazioni", "", true);
  const s = store.settings;
  const themeChip = (value, label) =>
    `<button class="chip" data-pick-theme="${value}" aria-pressed="${s.theme === value}">${label}</button>`;

  view.innerHTML = `
    <div class="card">
      <b style="color:var(--indigo)">Aspetto</b>
      <label class="field">Tema</label>
      <div class="chips">
        ${themeChip("sistema", "Come il sistema")}
        ${themeChip("chiaro", "Chiaro")}
        ${themeChip("scuro", "Scuro")}
      </div>
    </div>

    <div class="card">
      <b style="color:var(--indigo)">Valutazione</b>
      <label class="field" id="pass-label">Soglia di superamento: ${s.passPct}%</label>
      <input type="range" min="30" max="100" step="5" value="${s.passPct}" id="pass" style="width:100%">
    </div>

    <div class="card">
      <b style="color:var(--indigo)">Comportamento</b>
      <label class="switch">
        <input type="checkbox" id="auto" ${s.autoNext ? "checked" : ""}>
        Passa da sola alla domanda dopo
      </label>
    </div>

    <div class="card">
      <b style="color:var(--indigo)">Quiz preinstallati</b>
      ${store.hidden.length
        ? `<button class="btn ghost wide mt" id="restore">Rimetti in lista quelli nascosti (${store.hidden.length})</button>`
        : `<div class="small mt">Nessun quiz nascosto.</div>`}
    </div>

    <div class="card">
      <b style="color:var(--indigo)">Versione</b>
      <div class="small mt">Web app ${VERSION}. Si aggiorna da sola a ogni apertura con rete.</div>
      <div class="small mt">
        App Android:
        <a href="https://github.com/${REPO}/releases/latest">scarica l'APK</a>
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
  if ($("#restore")) {
    $("#restore").onclick = () => { store.hidden = []; save(); renderSettings(); };
  }
}

// ---------------------------------------------------------------- importa

function renderImport() {
  setHeader("Importa quiz", "", true);
  view.innerHTML = `
    <div class="card">
      <b style="color:var(--indigo)">Da un file</b>
      <div class="small mt">File .txt, .csv o .json già nel formato del quiz.</div>
      <input type="file" id="file" accept=".txt,.csv,.json,text/plain,text/csv,application/json" class="mt">
    </div>

    <div class="card">
      <b style="color:var(--indigo)">Incolla il testo</b>
      <label class="field">Nome del quiz</label>
      <input type="text" id="name" placeholder="Il mio quiz">
      <label class="field">Una domanda per riga</label>
      <textarea id="text" placeholder="domanda;risposta esatta;errata;errata"></textarea>
      <button class="btn wide mt" id="do-text">Importa</button>
    </div>

    <div class="card">
      <b style="color:var(--indigo)">Da un indirizzo web</b>
      <label class="field">Indirizzo</label>
      <input type="url" id="url" placeholder="https://…">
      <button class="btn ghost wide mt" id="do-url">Importa</button>
      <div class="small mt">Funziona solo se il sito consente la lettura da altri domini (CORS).</div>
    </div>

    <div class="card">
      <b style="color:var(--indigo)">Formato dei file</b>
      <div class="small mt">
        Una domanda per riga, campi separati da punto e virgola:<br>
        <code>domanda;risposta esatta;errata;errata</code><br><br>
        Estensioni facoltative:<br>
        • un <code>*</code> davanti a una risposta la marca come corretta, così una
        domanda può averne più di una<br>
        • un campo che inizia con <code>##</code> è la spiegazione mostrata dopo la risposta<br>
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

// --------------------------------------------------------------- pannello

function sheet(title, bodyHtml, wire) {
  const overlay = document.createElement("div");
  overlay.className = "overlay";
  overlay.innerHTML = `<div class="sheet">
    <b style="color:var(--indigo)">${esc(title)}</b>
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

  if ("serviceWorker" in navigator) {
    try {
      const reg = await navigator.serviceWorker.register("sw.js");
      reg.addEventListener("updatefound", () => {
        const fresh = reg.installing;
        if (!fresh) return;
        fresh.addEventListener("statechange", () => {
          if (fresh.state === "installed" && navigator.serviceWorker.controller) {
            const banner = document.getElementById("update-banner");
            banner.hidden = false;
            document.getElementById("update-reload").onclick = () => {
              fresh.postMessage("skip-waiting");
              location.reload();
            };
          }
        });
      });
    } catch (e) {
      /* niente offline: l'app funziona lo stesso finché c'è rete */
    }
  }
}

boot();
