/**
 * WebSocket smoke test — simulates a user sending a text message.
 * Usage: node scripts/ws-smoke-test.mjs [wsUrl] [message]
 */
const wsUrl = process.argv[2] || "ws://127.0.0.1:8080/ws/chat";
const userText = process.argv[3] || "你好，请简单介绍一下你自己。";
const timeoutMs = 60000;

const messages = [];
let finished = false;

function finish(code) {
  if (finished) return;
  finished = true;
  console.log("\n--- Summary ---");
  for (const m of messages) {
    console.log(JSON.stringify(m));
  }
  process.exit(code);
}

const timer = setTimeout(() => {
  console.error(`Timeout after ${timeoutMs}ms`);
  finish(1);
}, timeoutMs);

console.log("Connecting to", wsUrl);
const ws = new WebSocket(wsUrl);

ws.addEventListener("open", () => {
  console.log("Connected, waiting for session...");
});

ws.addEventListener("message", (ev) => {
  let data;
  try {
    data = JSON.parse(ev.data);
  } catch {
    console.log("Non-JSON:", String(ev.data).slice(0, 200));
    return;
  }
  messages.push(data);
  const preview =
    data.message ||
    (typeof data.text === "string" ? data.text.slice(0, 80) : "") ||
    data.delta ||
    "";
  console.log("<<", data.type, preview);

  if (data.type === "session") {
    console.log("Sending user_message:", userText);
    ws.send(JSON.stringify({ type: "user_message", text: userText }));
  }

  if (data.type === "assistant_done") {
    clearTimeout(timer);
    console.log("\nSUCCESS: full reply:", data.text);
    ws.close();
    finish(0);
  }

  if (data.type === "error") {
    clearTimeout(timer);
    console.error("\nERROR from server:", data.message);
    ws.close();
    finish(2);
  }
});

ws.addEventListener("error", () => {
  clearTimeout(timer);
  console.error("WebSocket error");
  finish(3);
});

ws.addEventListener("close", (ev) => {
  if (!finished) {
    clearTimeout(timer);
    console.error("Closed before assistant_done:", ev.code, ev.reason);
    finish(4);
  }
});
