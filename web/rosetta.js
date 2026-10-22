// Shared helpers for the kid and parent pages. API base comes from config.js (written at deploy time).
const API = (window.ROSETTA_CONFIG && window.ROSETTA_CONFIG.apiUrl || "").replace(/\/$/, "");
const params = new URLSearchParams(location.search);
const CODE = (params.get("code") || localStorage.getItem("rosetta.code") || "").toUpperCase();
if (CODE) localStorage.setItem("rosetta.code", CODE);

async function api(path, opts = {}) {
  const r = await fetch(API + path, { headers: { "content-type": "application/json" }, ...opts });
  if (!r.ok) {
    const text = await r.text();
    let msg = text;
    try { msg = JSON.parse(text).error || text; } catch (e) { /* plain text */ }
    const err = new Error(msg); err.status = r.status; throw err;
  }
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

// Art ships with the web page (same art as the TV).
const thingArt = (c) => `things/${c}.webp`;
const avatarArt = (a) => `avatars/${a}.webp`;

const esc = (s) => String(s == null ? "" : s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const money = (h, v) => `${(h.settings && h.settings.currency) || "₹"}${Number(v || 0) % 1 === 0 ? Number(v || 0) : Number(v).toFixed(2)}`;

// Plain words for where a task stands (✦ marks something the AI is doing).
const STAGE_TEXT = {
  Ready: "Not started", AwaitingBefore: "Waiting for your photo", Scanning: "✦ Rosetta is looking…",
  Found: "Put them back, then snap again", AwaitingAfter: "Snap again when done", Verifying: "✦ Checking your two photos…",
  Partial: "Almost! Snap again when done", Checking: "Waiting for Mom or Dad", Done: "Done! 🎉",
};
const STUDY_TEXT = {
  Ready: "Not started", AwaitingBefore: "Snap your homework", Scanning: "✦ The AI Tutor is reading your work…",
  Checked: "✦ Marked by the AI Tutor", Practising: "Practice round: answer here or on the TV", Checking: "Waiting for Mom or Dad", Done: "Homework done! 🎉",
};
const HABIT_TEXT = { Ready: "Not done yet", Checking: "Waiting for Mom or Dad to check", Done: "Done! 🎉" };
const PROOF_TEXT = { none: "Tick it when it's done", note: "Write a short note", voice: "Send a voice note", photo: "Snap a photo" };

function stageText(q) {
  if (q.kind === "habit") return HABIT_TEXT[q.stage] || "Not done yet";
  return (q.kind === "study" ? STUDY_TEXT : STAGE_TEXT)[q.stage] || q.stage;
}

const KIND_ICON = { study: "✏️", tidy: "🧸", habit: "✅" };
const taskIcon = (q) => q.icon || KIND_ICON[q.kind] || "⭐";

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
      <div><div>${esc(t.name)}</div><div class="home">→ ${esc(t.home)}</div></div>
      <div class="st">${t.returned ? "✓ BACK" : "★ " + t.stars}</div>
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

/** A daily task's voice note: recorded in the browser (MediaRecorder), then straight to S3 for ✦ the AI to listen. */
async function uploadVoice(questId, blob) {
  const type = (blob.type || "audio/webm").split(";")[0];
  const { uploadUrl, contentType } = await post(`/household/${CODE}/upload`, { questId, phase: "voice", contentType: type });
  const r = await fetch(uploadUrl, { method: "PUT", headers: { "content-type": contentType }, body: blob });
  if (!r.ok) throw new Error(`upload failed ${r.status}`);
}

/** Tap to start, tap to stop. Resolves with the recording, or null if the phone has no microphone access. */
function voiceRecorder() {
  let rec = null, chunks = [], stream = null;
  const pick = ["audio/webm", "audio/mp4", "audio/ogg"].find((t) => window.MediaRecorder && MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(t));
  return {
    get recording() { return !!rec; },
    async start() {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      chunks = [];
      rec = new MediaRecorder(stream, pick ? { mimeType: pick } : undefined);
      rec.ondataavailable = (e) => e.data.size && chunks.push(e.data);
      rec.start();
    },
    stop() {
      return new Promise((res) => {
        if (!rec) return res(null);
        rec.onstop = () => {
          stream.getTracks().forEach((t) => t.stop());
          const blob = new Blob(chunks, { type: (rec.mimeType || pick || "audio/webm").split(";")[0] });
          rec = null; res(blob);
        };
        rec.stop();
      });
    },
  };
}

function ago(ms) {
  const s = Math.max(1, Math.round((Date.now() - ms) / 1000));
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  if (s < 86400) return `${Math.round(s / 3600)} h ago`;
  return `${Math.round(s / 86400)} d ago`;
}

function toast(text) {
  const t = document.getElementById("toast");
  if (!t) return;
  t.textContent = text; t.hidden = false;
  clearTimeout(toast.t); toast.t = setTimeout(() => (t.hidden = true), 2800);
}

// Polling refreshes every few seconds; only rebuild a section when its HTML actually changes, so taps aren't lost.
const shownHtml = {};
function setHtml(id, html) {
  if (shownHtml[id] === html) return false;
  shownHtml[id] = html;
  document.getElementById(id).innerHTML = html;
  return true;
}
