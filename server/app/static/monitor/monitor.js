const wsProto = location.protocol === "https:" ? "wss" : "ws";
const telemetryUrl = `${wsProto}://${location.host}/ws/telemetry?client=MONITOR`;
const controlUrl = `${wsProto}://${location.host}/ws/control`;

const $ = (id) => document.getElementById(id);
const log = (msg) => {
  const el = $("log");
  const line = document.createElement("div");
  line.textContent = `[${new Date().toLocaleTimeString()}] ${msg}`;
  el.prepend(line);
  while (el.childNodes.length > 200) el.removeChild(el.lastChild);
};

// ---------- rolling waveform buffers ----------
const PPG_BUFFER_LEN = 400;
const ECG_BUFFER_LEN = 800;
let ppgBuffer = [];
let ecgBuffer = [];

function pushSamples(buffer, maxLen, samples) {
  buffer.push(...samples);
  if (buffer.length > maxLen) buffer.splice(0, buffer.length - maxLen);
}

function drawWaveform(canvasId, buffer) {
  const canvas = $(canvasId);
  const ctx = canvas.getContext("2d");
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);
  if (buffer.length < 2) return;

  const min = Math.min(...buffer);
  const max = Math.max(...buffer);
  const range = max - min || 1;

  ctx.strokeStyle = "#4fc3f7";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  buffer.forEach((v, i) => {
    const x = (i / (buffer.length - 1)) * w;
    const y = h - ((v - min) / range) * (h - 10) - 5;
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();
}

function renderLoop() {
  drawWaveform("ppg-canvas", ppgBuffer);
  drawWaveform("ecg-canvas", ecgBuffer);
  requestAnimationFrame(renderLoop);
}
requestAnimationFrame(renderLoop);

// ---------- telemetry handling ----------
function setDot(id, on) {
  $(id).classList.toggle("on", on);
}

function renderVitals(msg) {
  const d = msg.data;
  $("v-bp").textContent = `${d.bpSys ?? "—"} / ${d.bpDia ?? "—"}`;
  $("v-spo2").textContent = d.spo2 ?? "—";
  $("v-hr").textContent = d.heartRate ?? "—";
  $("v-temp").textContent = d.temperature ?? "—";
  $("v-vitals-ts").textContent = new Date(msg.timestamp).toLocaleTimeString();
  $("v-patientId").textContent = msg.patientId ?? "—";
  $("v-examId").textContent = msg.examId ?? "—";
}

function renderGlassState(state) {
  $("g-connected").textContent = state.connected;
  $("g-examMode").textContent = state.examMode;
  $("g-laterality").textContent = state.laterality;
  $("g-recording").textContent = state.recording;
  $("g-frozen").textContent = state.frozen;
  $("g-endoscope").textContent = state.endoscopeConnected;
  $("g-lastSeen").textContent = state.lastSeen ? new Date(state.lastSeen).toLocaleTimeString() : "—";

  $("v-patientId").textContent = state.patientId ?? "—";
  $("v-examId").textContent = state.examId ?? "—";
  $("v-displayName").textContent = state.patientDisplayName ?? "—";
  $("v-ageSex").textContent = `${state.age ?? "—"} / ${state.sex ?? "—"}`;

  if (state.bpSys != null) $("v-bp").textContent = `${state.bpSys} / ${state.bpDia}`;
  if (state.spo2 != null) $("v-spo2").textContent = state.spo2;
  if (state.heartRate != null) $("v-hr").textContent = state.heartRate;
  if (state.temperature != null) $("v-temp").textContent = state.temperature;
}

const deviceRows = new Map();
function renderDeviceStatus(status) {
  deviceRows.set(status.device, status);
  const tbody = document.querySelector("#device-table tbody");
  tbody.innerHTML = "";
  [...deviceRows.values()]
    .sort((a, b) => a.device.localeCompare(b.device))
    .forEach((s) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${s.device}</td>
        <td class="state-${s.state}">${s.state}</td>
        <td>${s.lastSeen ? new Date(s.lastSeen).toLocaleTimeString() : "—"}</td>`;
      tbody.appendChild(tr);
    });
}

function connectTelemetry() {
  const ws = new WebSocket(telemetryUrl);

  ws.onopen = () => {
    setDot("telemetry-dot", true);
    log("telemetry connected");
  };
  ws.onclose = () => {
    setDot("telemetry-dot", false);
    log("telemetry disconnected — retrying in 2s");
    setTimeout(connectTelemetry, 2000);
  };
  ws.onerror = () => ws.close();

  ws.onmessage = (evt) => {
    const msg = JSON.parse(evt.data);
    switch (msg.type) {
      case "vitals":
        renderVitals(msg);
        break;
      case "raw_signal":
        if (msg.signal === "PPG_IR") pushSamples(ppgBuffer, PPG_BUFFER_LEN, msg.samples);
        if (msg.signal === "ECG") pushSamples(ecgBuffer, ECG_BUFFER_LEN, msg.samples);
        break;
      case "glass_state":
        renderGlassState(msg);
        break;
      case "device_status":
        renderDeviceStatus(msg);
        break;
      case "event":
        log(`event: ${msg.eventType} (exam=${msg.examId ?? "none"})`);
        break;
      default:
        break;
    }
  };
}

// ---------- control channel ----------
let controlWs = null;
function connectControl() {
  controlWs = new WebSocket(controlUrl);
  controlWs.onopen = () => {
    setDot("control-dot", true);
    log("control connected");
  };
  controlWs.onclose = () => {
    setDot("control-dot", false);
    log("control disconnected — retrying in 2s");
    setTimeout(connectControl, 2000);
  };
  controlWs.onerror = () => controlWs.close();
  controlWs.onmessage = (evt) => {
    const msg = JSON.parse(evt.data);
    if (msg.type === "error") log(`control error: ${msg.message}`);
  };
}

document.querySelectorAll("button[data-cmd]").forEach((btn) => {
  btn.addEventListener("click", () => {
    if (!controlWs || controlWs.readyState !== WebSocket.OPEN) {
      log("control channel not connected");
      return;
    }
    controlWs.send(JSON.stringify({ command: btn.dataset.cmd }));
  });
});

// ---------- REST: patient + exam ----------
let activeExamId = null;

$("patient-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const patientId = $("f-patientId").value.trim();
  const displayName = $("f-displayName").value.trim();
  const age = $("f-age").value ? Number($("f-age").value) : null;
  const sex = $("f-sex").value || null;

  try {
    let res = await fetch("/patients", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ patientId, displayName, age, sex }),
    });
    if (res.status === 409) {
      log(`patient ${patientId} already exists, starting exam anyway`);
    } else if (!res.ok) {
      throw new Error(await res.text());
    } else {
      log(`patient ${patientId} created`);
    }

    res = await fetch("/exams", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ patientId }),
    });
    if (!res.ok) throw new Error(await res.text());
    const exam = await res.json();
    activeExamId = exam.examId;
    log(`exam ${exam.examId} started for ${patientId}`);
  } catch (err) {
    log(`error: ${err.message}`);
  }
});

$("btn-end-exam").addEventListener("click", async () => {
  if (!activeExamId) {
    log("no active exam to end");
    return;
  }
  const res = await fetch(`/exams/${activeExamId}/end`, { method: "POST" });
  if (res.ok) {
    log(`exam ${activeExamId} ended`);
    activeExamId = null;
  } else {
    log(`error ending exam: ${await res.text()}`);
  }
});

connectTelemetry();
connectControl();
