/* Orb Mobile — client */
(() => {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const store = {
    get: (k, d = "") => { try { return localStorage.getItem(k) ?? d; } catch { return d; } },
    set: (k, v) => { try { localStorage.setItem(k, v); } catch {} },
  };

  const state = {
    server: store.get("orb.server", ""),
    token: store.get("orb.token", ""),
    config: null,
    tts: store.get("orb.tts", "on") !== "off",
    busy: false,
    recording: false,
    hasMessages: false,
    installPrompt: null,
  };

  const el = {
    chat: $("chat"), hero: $("hero"), input: $("input"), send: $("sendBtn"),
    mic: $("micBtn"), thinking: $("thinking"), status: $("status"),
    name: $("assistantName"), toast: $("toast"), backdrop: $("backdrop"),
    memorySheet: $("memorySheet"), settingsSheet: $("settingsSheet"),
    memoryList: $("memoryList"), recBar: $("recordingBar"), recTime: $("recTime"),
    voiceToggle: $("voiceToggle"),
  };

  const escapeHtml = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[c]));

  function baseUrl() { return state.server.replace(/\/+$/, ""); }

  async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.token) headers.set("Authorization", `Bearer ${state.token}`);
    const response = await fetch(baseUrl() + path, { ...options, headers });
    if (!response.ok) {
      let detail = "";
      try { detail = (await response.json()).detail || ""; } catch {}
      const error = new Error(detail || `HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    const type = response.headers.get("content-type") || "";
    return type.includes("application/json") ? response.json() : response;
  }

  /* ── UI helpers ─────────────────────────────────────────────────────────── */

  let toastTimer = null;
  function toast(message, kind = "") {
    el.toast.textContent = message;
    el.toast.className = `toast show ${kind}`.trim();
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.toast.className = "toast hidden"; }, 4200);
  }

  function setStatus(text, kind = "") {
    el.status.innerHTML = `<span class="dot ${kind}"></span> ${escapeHtml(text)}`;
  }

  function setThinking(on) {
    el.thinking.classList.toggle("hidden", !on);
    el.thinking.setAttribute("aria-hidden", String(!on));
  }

  function linkify(container, text) {
    const urlRe = /(https?:\/\/[^\s<>"']+)/g;
    let last = 0;
    let match;
    while ((match = urlRe.exec(text)) !== null) {
      if (match.index > last) container.appendChild(document.createTextNode(text.slice(last, match.index)));
      const a = document.createElement("a");
      a.href = match[0];
      a.target = "_blank";
      a.rel = "noopener noreferrer";
      a.textContent = match[0];
      container.appendChild(a);
      last = match.index + match[0].length;
    }
    if (last < text.length) container.appendChild(document.createTextNode(text.slice(last)));
  }

  function stamp(ts) {
    const date = ts ? new Date(ts * 1000) : new Date();
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }

  function addMessage(role, text, meta = {}) {
    if (!state.hasMessages) {
      state.hasMessages = true;
      el.hero?.classList.add("hidden");
    }
    const wrap = document.createElement("article");
    wrap.className = `msg ${role === "user" ? "user" : "bot"}`;

    const bubble = document.createElement("div");
    bubble.className = "bubble";
    linkify(bubble, String(text ?? ""));
    wrap.appendChild(bubble);

    if (Array.isArray(meta.tools) && meta.tools.length) {
      const tools = document.createElement("div");
      tools.className = "tools";
      for (const tool of meta.tools) {
        const chip = document.createElement("span");
        chip.className = "tool-chip";
        chip.textContent = `⚙ ${tool.name || "tool"}`;
        chip.title = String(tool.result || "").slice(0, 300);
        tools.appendChild(chip);
      }
      wrap.appendChild(tools);
    }

    const time = document.createElement("span");
    time.className = "meta";
    time.textContent = stamp(meta.ts);
    wrap.appendChild(time);

    el.chat.appendChild(wrap);
    el.chat.scrollTop = el.chat.scrollHeight;
    return wrap;
  }

  /* ── Speech output ──────────────────────────────────────────────────────── */

  let audioEl = null;
  let audioUnlocked = false;

  function unlockAudio() {
    if (audioUnlocked) return;
    audioUnlocked = true;
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      const ctx = new AudioCtx();
      ctx.resume();
      const source = ctx.createBufferSource();
      source.buffer = ctx.createBuffer(1, 1, 22050);
      source.connect(ctx.destination);
      source.start(0);
    } catch {}
    try {
      if (!audioEl) audioEl = new Audio();
      audioEl.src = "data:audio/wav;base64,UklGRiYAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQIAAAAAAA==";
      audioEl.play().catch(() => {});
    } catch {}
  }

  function playBlob(blob) {
    if (!audioEl) audioEl = new Audio();
    audioEl.pause();
    audioEl.src = URL.createObjectURL(blob);
    audioEl.play().catch(() => {});
  }

  function browserSpeak(text) {
    if (!("speechSynthesis" in window)) return;
    try {
      speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      const lang = state.config?.language || "pt-BR";
      utterance.lang = lang;
      const voices = speechSynthesis.getVoices();
      const exact = voices.find((v) => v.lang?.toLowerCase() === lang.toLowerCase());
      const prefix = voices.find((v) => v.lang?.toLowerCase().startsWith(lang.slice(0, 2).toLowerCase()));
      utterance.voice = exact || prefix || null;
      utterance.rate = 1.02;
      utterance.pitch = 0.92;
      speechSynthesis.speak(utterance);
    } catch {}
  }

  async function speak(text) {
    if (!state.tts || !text) return;
    if (state.config?.tts_engine === "gemini") {
      try {
        const response = await fetch(`${baseUrl()}/api/tts`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}),
          },
          body: JSON.stringify({ text: text.slice(0, 1800), voice: state.config?.voice || "" }),
        });
        if (response.ok) {
          playBlob(await response.blob());
          return;
        }
      } catch {}
    }
    browserSpeak(text);
  }

  /* ── Recording ──────────────────────────────────────────────────────────── */

  const recorder = {
    ctx: null, stream: null, source: null, processor: null,
    chunks: [], startedAt: 0, timer: null,
  };

  function downsample(input, inRate, outRate) {
    if (outRate === inRate) return input;
    const ratio = inRate / outRate;
    const length = Math.floor(input.length / ratio);
    const output = new Float32Array(length);
    for (let i = 0; i < length; i += 1) {
      const pos = i * ratio;
      const low = Math.floor(pos);
      const high = Math.min(low + 1, input.length - 1);
      const frac = pos - low;
      output[i] = input[low] * (1 - frac) + input[high] * frac;
    }
    return output;
  }

  function encodeWav(samples, sampleRate) {
    const buffer = new ArrayBuffer(44 + samples.length * 2);
    const view = new DataView(buffer);
    const write = (offset, string) => {
      for (let i = 0; i < string.length; i += 1) view.setUint8(offset + i, string.charCodeAt(i));
    };
    write(0, "RIFF");
    view.setUint32(4, 36 + samples.length * 2, true);
    write(8, "WAVE");
    write(12, "fmt ");
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    write(36, "data");
    view.setUint32(40, samples.length * 2, true);
    let offset = 44;
    for (let i = 0; i < samples.length; i += 1) {
      const s = Math.max(-1, Math.min(1, samples[i]));
      view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true);
      offset += 2;
    }
    return new Blob([view], { type: "audio/wav" });
  }

  function tickRecording() {
    const seconds = Math.floor((Date.now() - recorder.startedAt) / 1000);
    el.recTime.textContent = `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
    if (seconds >= 60) stopRecording(true);
  }

  async function startRecording() {
    if (state.recording || state.busy) return;
    if (!navigator.mediaDevices?.getUserMedia) {
      toast("Este navegador não permite gravar áudio. Use o campo de texto.", "error");
      return;
    }
    try {
      recorder.stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1 },
      });
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      recorder.ctx = new AudioCtx();
      await recorder.ctx.resume();
      recorder.source = recorder.ctx.createMediaStreamSource(recorder.stream);
      recorder.processor = recorder.ctx.createScriptProcessor(4096, 1, 1);
      recorder.chunks = [];
      recorder.processor.onaudioprocess = (event) => {
        if (!state.recording) return;
        recorder.chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)));
      };
      recorder.source.connect(recorder.processor);
      recorder.processor.connect(recorder.ctx.destination);
      state.recording = true;
      recorder.startedAt = Date.now();
      el.mic.classList.add("recording");
      el.mic.setAttribute("aria-pressed", "true");
      el.recBar.classList.remove("hidden");
      el.recTime.textContent = "0:00";
      recorder.timer = setInterval(tickRecording, 250);
      if (navigator.vibrate) navigator.vibrate(20);
    } catch (error) {
      cleanupRecording();
      toast("Não consegui acessar o microfone. Verifique a permissão do navegador.", "error");
    }
  }

  function cleanupRecording() {
    state.recording = false;
    clearInterval(recorder.timer);
    recorder.timer = null;
    try { recorder.processor?.disconnect(); } catch {}
    try { recorder.source?.disconnect(); } catch {}
    try { recorder.stream?.getTracks().forEach((t) => t.stop()); } catch {}
    try { recorder.ctx?.close(); } catch {}
    recorder.processor = recorder.source = recorder.stream = recorder.ctx = null;
    el.mic.classList.remove("recording");
    el.mic.setAttribute("aria-pressed", "false");
    el.recBar.classList.add("hidden");
  }

  async function stopRecording(send = true) {
    if (!state.recording) return;
    const chunks = recorder.chunks.slice();
    const rate = recorder.ctx?.sampleRate || 48000;
    cleanupRecording();
    if (!send || !chunks.length) return;

    const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    const merged = new Float32Array(total);
    let offset = 0;
    for (const chunk of chunks) { merged.set(chunk, offset); offset += chunk.length; }
    const pcm = downsample(merged, rate, 16000);
    const blob = encodeWav(pcm, 16000);

    if (blob.size < 4000) {
      toast("Gravação muito curta. Segure e fale de novo.", "error");
      return;
    }

    state.busy = true;
    setThinking(true);
    try {
      const form = new FormData();
      form.append("audio", blob, "voice.wav");
      const speakParam = state.config?.tts_engine === "gemini" && state.tts ? "true" : "false";
      const result = await api(`/api/voice?speak=${speakParam}`, { method: "POST", body: form });
      if (result.transcript) addMessage("user", result.transcript);
      else addMessage("user", "🎤 (áudio)");
      addMessage("assistant", result.reply, { tools: result.tools });
      if (!result.ok) toast(result.reply, "error");
      if (result.audio) {
        const bytes = Uint8Array.from(atob(result.audio), (c) => c.charCodeAt(0));
        playBlob(new Blob([bytes], { type: "audio/wav" }));
      } else if (result.ok) {
        speak(result.reply);
      }
    } catch (error) {
      toast(error.message || "Falha ao enviar o áudio.", "error");
      addMessage("assistant", "Não consegui processar o áudio agora. Tente novamente.");
    } finally {
      state.busy = false;
      setThinking(false);
    }
  }

  /* ── Chat ───────────────────────────────────────────────────────────────── */

  function autosize() {
    el.input.style.height = "auto";
    el.input.style.height = `${Math.min(el.input.scrollHeight, 132)}px`;
    el.send.disabled = !el.input.value.trim() || state.busy;
  }

  async function sendMessage(text) {
    const message = (text ?? el.input.value).trim();
    if (!message || state.busy) return;
    if (!state.config?.api_key_set) {
      toast("Configure a chave da API Gemini nos ajustes para eu funcionar.", "error");
      openSheet("settingsSheet");
      return;
    }
    el.input.value = "";
    autosize();
    addMessage("user", message);
    state.busy = true;
    setThinking(true);
    try {
      const result = await api("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message }),
      });
      addMessage("assistant", result.reply, { tools: result.tools });
      if (!result.ok) toast(result.reply, "error");
      else speak(result.reply);
    } catch (error) {
      const offline = error.status ? `Erro ${error.status}.` : "Sem conexão com o servidor.";
      toast(`${offline} ${error.message || ""}`.trim(), "error");
      addMessage("assistant", "Não consegui alcançar o servidor do Orb. Verifique o endereço nos ajustes.");
    } finally {
      state.busy = false;
      setThinking(false);
      autosize();
    }
  }

  /* ── Sheets ─────────────────────────────────────────────────────────────── */

  function openSheet(id) {
    document.querySelectorAll(".sheet.open").forEach((sheet) => sheet.classList.remove("open"));
    const sheet = $(id);
    if (!sheet) return;
    sheet.classList.add("open");
    sheet.setAttribute("aria-hidden", "false");
    el.backdrop.classList.remove("hidden");
    if (id === "memorySheet") loadMemory();
  }

  function closeSheets() {
    document.querySelectorAll(".sheet.open").forEach((sheet) => {
      sheet.classList.remove("open");
      sheet.setAttribute("aria-hidden", "true");
    });
    el.backdrop.classList.add("hidden");
  }

  /* ── Memory ─────────────────────────────────────────────────────────────── */

  async function loadMemory() {
    el.memoryList.innerHTML = '<p class="memory-empty">carregando…</p>';
    try {
      const data = await api("/api/memory");
      renderMemory(data.entries || []);
    } catch (error) {
      el.memoryList.innerHTML = `<p class="memory-empty">${escapeHtml(error.message)}</p>`;
    }
  }

  function renderMemory(entries) {
    el.memoryList.innerHTML = "";
    if (!entries.length) {
      el.memoryList.innerHTML = '<p class="memory-empty">Nada guardado ainda. Converse comigo e eu lembro do que importa.</p>';
      return;
    }
    const sorted = [...entries].sort((a, b) => (b.updated_at || 0) - (a.updated_at || 0));
    for (const entry of sorted) {
      const item = document.createElement("div");
      item.className = "memory-item";
      const body = document.createElement("div");
      body.className = "mi-body";
      const key = document.createElement("div");
      key.className = "mi-key";
      key.textContent = entry.key;
      const value = document.createElement("div");
      value.className = "mi-value";
      value.textContent = entry.value;
      const cat = document.createElement("div");
      cat.className = "mi-cat";
      cat.textContent = entry.category;
      body.append(key, value, cat);
      const remove = document.createElement("button");
      remove.type = "button";
      remove.textContent = "×";
      remove.title = "Apagar";
      remove.addEventListener("click", async () => {
        try {
          await api(`/api/memory/${encodeURIComponent(entry.id)}`, { method: "DELETE" });
          loadMemory();
        } catch (error) { toast(error.message, "error"); }
      });
      item.append(body, remove);
      el.memoryList.appendChild(item);
    }
  }

  /* ── Settings ───────────────────────────────────────────────────────────── */

  function fillSettings(config) {
    $("cfgName").value = config.assistant_name || "";
    $("cfgUser").value = config.user_name || "";
    $("cfgLanguage").value = config.language || "pt-BR";
    $("cfgPersona").value = config.persona || "";
    $("cfgTts").value = config.tts_engine || "browser";
    $("cfgServer").value = state.server;
    $("cfgToken").value = state.token;
    $("cfgApiKey").value = "";
    $("cfgApiKey").placeholder = config.api_key_set ? "chave configurada — deixe em branco para manter" : "AIza…";

    const voiceSelect = $("cfgVoice");
    voiceSelect.innerHTML = "";
    for (const voice of config.voices || ["Charon"]) {
      const option = document.createElement("option");
      option.value = voice;
      option.textContent = voice;
      voiceSelect.appendChild(option);
    }
    voiceSelect.value = config.voice || "Charon";
  }

  async function saveSettings() {
    const payload = {
      assistant_name: $("cfgName").value.trim() || "Orb",
      user_name: $("cfgUser").value.trim(),
      language: $("cfgLanguage").value,
      persona: $("cfgPersona").value.trim(),
      tts_engine: $("cfgTts").value,
      voice: $("cfgVoice").value,
    };
    const key = $("cfgApiKey").value.trim();
    if (key) payload.api_key = key;
    try {
      state.server = $("cfgServer").value.trim();
      state.token = $("cfgToken").value.trim();
      store.set("orb.server", state.server);
      store.set("orb.token", state.token);
      const config = await api("/api/config", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      state.config = config;
      fillSettings(config);
      el.name.textContent = (config.assistant_name || "Orb").toUpperCase();
      closeSheets();
      toast("Ajustes salvos.", "ok");
      checkHealth();
    } catch (error) {
      toast(`Não consegui salvar: ${error.message}`, "error");
    }
  }

  /* ── Boot ───────────────────────────────────────────────────────────────── */

  async function loadConfig() {
    const config = await api("/api/config");
    state.config = config;
    el.name.textContent = (config.assistant_name || "Orb").toUpperCase();
    document.title = config.assistant_name || "Orb";
    fillSettings(config);
    if (!config.api_key_set) {
      setStatus("falta a chave da API", "offline");
      setTimeout(() => toast("Abra os ajustes e cole sua chave Gemini para ativar o Orb.", "error"), 900);
    }
  }

  async function loadHistory() {
    try {
      const data = await api("/api/history?limit=40");
      for (const message of data.messages || []) {
        addMessage(message.role === "user" ? "user" : "assistant", message.text, { ts: message.ts });
      }
    } catch {}
  }

  async function checkHealth() {
    try {
      const health = await api("/api/health");
      setStatus("online", "online");
      if (!health.api_key_set) setStatus("falta a chave da API", "offline");
      else if (state.config) setStatus(`online · ${state.config.assistant_name || "Orb"}`, "online");
    } catch {
      setStatus("servidor offline", "offline");
    }
  }

  function connectWS() {
    const explicit = state.server ? state.server.replace(/^http/, "ws") : "";
    const url = explicit
      ? `${explicit}/ws${state.token ? `?token=${encodeURIComponent(state.token)}` : ""}`
      : `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws${state.token ? `?token=${encodeURIComponent(state.token)}` : ""}`;
    let socket;
    try { socket = new WebSocket(url); } catch { return; }
    socket.addEventListener("message", (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === "reminder") {
          const text = data.reminder?.text || "Lembrete";
          toast(`⏰ ${text}`, "ok");
          if (window.Notification?.permission === "granted") {
            new Notification("Orb", { body: text, icon: "/icons/icon-192.png" });
          }
          speak(`Lembrete: ${text}`);
        }
      } catch {}
    });
    socket.addEventListener("close", () => setTimeout(connectWS, 5000));
    socket.addEventListener("error", () => socket.close());
  }

  function bindEvents() {
    document.addEventListener("pointerdown", unlockAudio, { once: true });
    el.send.addEventListener("click", () => sendMessage());
    el.input.addEventListener("input", autosize);
    el.input.addEventListener("keydown", (event) => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
      }
    });

    el.mic.addEventListener("click", () => {
      if (state.recording) stopRecording(true);
      else startRecording();
    });
    $("recCancel").addEventListener("click", () => stopRecording(false));

    $("chips").addEventListener("click", (event) => {
      const chip = event.target.closest(".chip");
      if (chip) sendMessage(chip.dataset.say || chip.textContent);
    });

    $("memoryBtn").addEventListener("click", () => openSheet("memorySheet"));
    $("settingsBtn").addEventListener("click", () => openSheet("settingsSheet"));
    el.backdrop.addEventListener("click", closeSheets);
    document.querySelectorAll("[data-close]").forEach((button) => {
      button.addEventListener("click", closeSheets);
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") closeSheets();
    });

    el.voiceToggle.addEventListener("click", () => {
      state.tts = !state.tts;
      store.set("orb.tts", state.tts ? "on" : "off");
      el.voiceToggle.setAttribute("aria-pressed", String(state.tts));
      toast(state.tts ? "Voz ativada." : "Voz desativada.");
      if (!state.tts) { try { speechSynthesis.cancel(); } catch {} }
    });

    $("memoryForm").addEventListener("submit", async (event) => {
      event.preventDefault();
      const key = $("memKey").value.trim();
      const value = $("memValue").value.trim();
      if (!key || !value) return;
      try {
        await api("/api/memory", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ key, value, category: $("memCategory").value }),
        });
        $("memKey").value = "";
        $("memValue").value = "";
        loadMemory();
        toast("Guardado na memória.", "ok");
      } catch (error) { toast(error.message, "error"); }
    });

    $("clearMemory").addEventListener("click", async () => {
      if (!confirm("Apagar toda a memória do Orb? Isso não pode ser desfeito.")) return;
      try { await api("/api/memory", { method: "DELETE" }); loadMemory(); toast("Memória apagada."); }
      catch (error) { toast(error.message, "error"); }
    });

    $("clearHistory").addEventListener("click", async () => {
      if (!confirm("Limpar a conversa atual?")) return;
      try {
        await api("/api/history", { method: "DELETE" });
        el.chat.querySelectorAll(".msg").forEach((node) => node.remove());
        state.hasMessages = false;
        el.hero?.classList.remove("hidden");
        closeSheets();
        toast("Conversa limpa.");
      } catch (error) { toast(error.message, "error"); }
    });

    $("saveSettings").addEventListener("click", saveSettings);

    window.addEventListener("beforeinstallprompt", (event) => {
      event.preventDefault();
      state.installPrompt = event;
      $("installBtn").hidden = false;
    });
    $("installBtn").addEventListener("click", async () => {
      if (!state.installPrompt) return;
      state.installPrompt.prompt();
      await state.installPrompt.userChoice;
      state.installPrompt = null;
      $("installBtn").hidden = true;
    });

    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) checkHealth();
    });
  }

  async function boot() {
    el.voiceToggle.setAttribute("aria-pressed", String(state.tts));
    bindEvents();
    autosize();
    setStatus("conectando…");
    try {
      await loadConfig();
      await loadHistory();
      await checkHealth();
      connectWS();
    } catch (error) {
      setStatus("servidor offline", "offline");
      toast("Não consegui falar com o servidor do Orb. Abra os ajustes e informe o endereço.", "error");
    }
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js").catch(() => {});
    }
    if (window.Notification?.permission === "default") {
      setTimeout(() => Notification.requestPermission().catch(() => {}), 4000);
    }
    setInterval(checkHealth, 30000);
  }

  document.addEventListener("DOMContentLoaded", boot);
})();
