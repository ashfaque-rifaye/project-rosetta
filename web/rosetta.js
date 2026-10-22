// Shared helpers for the kid and parent pages. API base comes from config.js (written at deploy time).
const API = (window.ROSETTA_CONFIG && window.ROSETTA_CONFIG.apiUrl || "").replace(/\/$/, "");
const params = new URLSearchParams(location.search);
const CODE = (params.get("code") || localStorage.getItem("rosetta.code") || "").toUpperCase();
if (CODE) localStorage.setItem("rosetta.code", CODE);

async function api(path, opts = {}) {
  const r = await fetch(API + path, { headers: { "content-type": "application/json" }, ...opts });
  if (!r.ok) throw new Error(`${r.status} ${await r.text()}`);
  return r.json();
}
const getHousehold = () => api(`/household/${CODE}`);
const post = (path, body = {}) => api(path, { method: "POST", body: JSON.stringify(body) });

// Parent actions carry the household PIN (header x-parent-pin). It lives only on the parent's own phone.
const PARENT_KEY = "rosetta.parent";
function parentSession() {
  try { return JSON.parse(localStorage.getItem(PARENT_KEY) || "null"); } catch (e) { return null; }
}
function asParent(path, { method = "POST", body, pin } = {}) {
  const s = parentSession();
  return api(path, {
    method, headers: { "content-type": "application/json", "x-parent-pin": pin || (s && s.pin) || "" },
    ...(body ? { body: JSON.stringify(body) } : method === "POST" ? { body: "{}" } : {}),
  });
}

// Lost Thing portraits ship with the web page (same art as the TV).
const thingArt = (c) => `things/${c}.webp`;

const STAGE_TEXT = {
  Ready: "Not started", AwaitingBefore: "Waiting for your photo", Scanning: "The crew is looking…",
  Found: "Bring them home, then snap again", AwaitingAfter: "Snap again when done", Verifying: "Checking on everyone…",
  Partial: "Almost! Snap again when done", Done: "All home! 🎉",
};
const STUDY_TEXT = {
  Ready: "Not started", AwaitingBefore: "Snap your homework", Scanning: "Tutor is reading your work…",
  Checked: "Tutor marked your work", Practising: "Practice round: answer here or on the TV", Done: "Homework done! 🎉",
};

function crewHtml(stage, study = false) {
  const on = (study
    ? { Scanning: ["Tutor"], Checked: ["Tutor", "Coach"], Practising: ["Coach", "Keeper"], Done: ["Keeper"] }
    : { Scanning: ["Scout", "Coach"], Found: ["Keeper"], Verifying: ["Judge"], Partial: ["Judge", "Keeper"], Done: ["Judge", "Keeper"] })[stage] || [];
  const crew = study ? ["Tutor", "Coach", "Keeper"] : ["Scout", "Coach", "Judge", "Keeper"];
  return `<div class="crew">${crew.map((n) => `<span class="${on.includes(n) ? "on" : ""}">${n}</span>`).join("")}</div>`;
}

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

/** Tutor's marking: a tick or cross per question, with the hint (never the answer) for the ones to fix. */
function markingHtml(hw) {
  return hw.items.map((i) => `
    <div class="mark ${i.correct ? "ok" : "fix"}">
      <span class="tick">${i.correct ? "✓" : "✗"}</span>
      <div><div>${i.n}. ${esc(i.question)} <b>${esc(i.kidAnswer)}</b></div>${i.correct ? "" : `<div class="hint">${esc(i.hint)}</div>`}</div>
    </div>`).join("");
}

/** One multiple-choice question; `answered` is the chosen index once answered. */
function questionHtml(q, answered) {
  const btn = (c, i) => {
    const cls = answered == null ? "" : i === q.answer ? "right" : i === answered ? "wrong" : "";
    return `<button class="choice ${cls}" data-i="${i}" ${answered == null ? "" : "disabled"}>${"ABCD"[i]} · ${esc(c)}</button>`;
  };
  return `<div class="q">${esc(q.prompt)}</div><div class="choices">${q.choices.map(btn).join("")}</div>` +
    (answered == null ? "" : `<p class="why">${answered === q.answer ? "Yes! " : "Nearly. "}${esc(q.why)}</p>`);
}

function thingsHtml(things) {
  return things.map((t) => `
    <div class="thing ${t.returned ? "done" : ""}">
      <img src="${thingArt(t.character)}" alt="">
      <div><div>${t.name}</div><div class="home">→ ${t.home}</div></div>
      <div class="st">${t.returned ? "✓ HOME" : "★ " + t.stars}</div>
    </div>`).join("");
}

/** Shrink a camera photo on the phone before upload (faster, cheaper for the vision model). */
async function shrink(file, max = 1600) {
  const bmp = await createImageBitmap(file);
  const s = Math.min(1, max / Math.max(bmp.width, bmp.height));
  const c = document.createElement("canvas");
  c.width = Math.round(bmp.width * s); c.height = Math.round(bmp.height * s);
  c.getContext("2d").drawImage(bmp, 0, 0, c.width, c.height);
  return new Promise((res) => c.toBlob(res, "image/jpeg", 0.86));
}

async function uploadPhoto(questId, file) {
  const { uploadUrl, phase } = await post(`/household/${CODE}/upload`, { questId });
  const blob = await shrink(file);
  const r = await fetch(uploadUrl, { method: "PUT", headers: { "content-type": "image/jpeg" }, body: blob });
  if (!r.ok) throw new Error(`upload failed ${r.status}`);
  return phase;
}
