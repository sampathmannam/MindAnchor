// k6 load test for the AnchorCore LLM letter generation path.
//
// MindAnchor is an Android app with no HTTP server, so this
// script targets the LLM provider endpoints directly:
//   - Google AI Studio (generativelanguage.googleapis.com)
//   - OpenRouter  (openrouter.ai/api/v1)
//   - Groq        (api.groq.com/openai/v1)
//
// Run with one of:
//   LLM_KEY=... LLM_PROVIDER=google  k6 run tools/loadtest-llm.js
//   LLM_KEY=... LLM_PROVIDER=openrouter k6 run tools/loadtest-llm.js
//   LLM_KEY=... LLM_PROVIDER=groq     k6 run tools/loadtest-llm.js
//
// The request shape mirrors what LetterViewModel.runGeneration
// sends — including the userPrompt, systemPrompt, maxOutputTokens,
// and the streaming-disabled single-shot path. Thresholds are
// calibrated against the AnchorState "warming up" worst case: a
// 200ms median is the per-letter budget the on-device UX can
// absorb before the spinner feels stuck.

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

const provider = __ENV.LLM_PROVIDER || "google";
const apiKey = __ENV.LLM_KEY || "";
if (!apiKey) {
  throw new Error("LLM_KEY env var is required");
}

const endpoints = {
  google: {
    url: "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
    build: () => ({
      method: "POST",
      url: endpoints.google.url + "?key=" + apiKey,
      body: JSON.stringify({
        contents: [
          {
            role: "user",
            parts: [{ text: "In 2-3 sentences, reflect on today." }],
          },
        ],
        systemInstruction: {
          parts: [{ text: "Write a 200-300 word letter in the launcher's own voice." }],
        },
        generationConfig: { maxOutputTokens: 400, temperature: 0.7 },
        safetySettings: [],
      }),
      params: { headers: { "Content-Type": "application/json" } },
    }),
  },
  openrouter: {
    url: "https://openrouter.ai/api/v1/chat/completions",
    build: () => ({
      method: "POST",
      url: endpoints.openrouter.url,
      body: JSON.stringify({
        model: "google/gemini-2.0-flash-001",
        max_tokens: 400,
        temperature: 0.7,
        messages: [
          { role: "system", content: "Write a 200-300 word letter in the launcher's own voice." },
          { role: "user", content: "In 2-3 sentences, reflect on today." },
        ],
      }),
      params: {
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " + apiKey,
        },
      },
    }),
  },
  groq: {
    url: "https://api.groq.com/openai/v1/chat/completions",
    build: () => ({
      method: "POST",
      url: endpoints.groq.url,
      body: JSON.stringify({
        model: "llama-3.1-70b-versatile",
        max_tokens: 400,
        temperature: 0.7,
        messages: [
          { role: "system", content: "Write a 200-300 word letter in the launcher's own voice." },
          { role: "user", content: "In 2-3 sentences, reflect on today." },
        ],
      }),
      params: {
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " + apiKey,
        },
      },
    }),
  },
};

const cfg = endpoints[provider];
if (!cfg) throw new Error("unknown LLM_PROVIDER: " + provider);

const callLatency = new Trend("llm_call_latency_ms", true);
const callErrors = new Counter("llm_call_errors");

export const options = {
  // AnchorCore spec §G.3: the LLM call is once per letter
  // generation. Multiple cards = multiple calls but the
  // rate stays in single digits per minute per user. The
  // load test ramps to a realistic 5 VUs for 30s to
  // catch provider-side rate-limit issues.
  stages: [
    { duration: "5s", target: 5 },
    { duration: "20s", target: 5 },
    { duration: "5s", target: 0 },
  ],
  thresholds: {
    // The on-device UX can absorb 200ms median; >500ms
    // p95 means the spinner feels stuck.
    "llm_call_latency_ms:p(50)": ["<200"],
    "llm_call_latency_ms:p(95)": ["<500"],
    "llm_call_errors": ["<1"],
  },
};

export default function () {
  const t0 = Date.now();
  const r = http.request(cfg.build().method, cfg.build().url, cfg.build().body, cfg.build().params);
  const dt = Date.now() - t0;
  callLatency.add(dt);
  const ok = check(r, {
    "status 200": (r) => r.status === 200,
    "body has text": (r) => {
      try {
        const j = JSON.parse(r.body);
        // Google: candidates[0].content.parts[0].text
        // OpenAI-compatible: choices[0].message.content
        return (j.candidates?.[0]?.content?.parts?.[0]?.text) ||
               (j.choices?.[0]?.message?.content);
      } catch (_) {
        return false;
      }
    },
  });
  if (!ok) callErrors.add(1);
  sleep(1);
}
