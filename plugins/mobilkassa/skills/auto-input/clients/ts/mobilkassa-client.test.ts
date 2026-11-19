// Runs against the mock kassa: MOCK_URL=http://127.0.0.1:6461 node --test (mock started with --idle-seconds 2).
import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { MobilkassaClient, type PreReceiptRequest } from "./mobilkassa-client.ts";

const MOCK = process.env.MOCK_URL ?? "http://127.0.0.1:6461";
const up = await fetch(`${MOCK}/health`).then((r) => r.ok).catch(() => false);
const skip = up ? false : `mock kassa is not running at ${MOCK}`;

const client = new MobilkassaClient({ url: `${MOCK}/pospoint`, key: "mock-kassa-key", timeoutMs: 10_000, pollMs: 200 });

const tea = { name: "Чай чёрный 100 г", amount: 2, price: 15000, mxik: "02101001001000000", ownerType: 0, unit: "1", packageCode: 1500, taxPercent: 12, sku: "2050" };
const sale = (n: string, extra: Partial<PreReceiptRequest> = {}): PreReceiptRequest => ({ payCheckNumber: n, type: "SALE", items: [tea], ...extra });

async function cashier(action: string, n: string, changes?: unknown): Promise<void> {
  const until = Date.now() + 5000;
  for (;;) {
    const state = await fetch(`${MOCK}/mock/state`).then((r) => r.json());
    const here = state.open?.payCheckNumber === n || state.parked.some((p: { payCheckNumber: string }) => p.payCheckNumber === n);
    if (here) break;
    if (Date.now() > until) throw new Error(`${n} never reached the desk`);
    await new Promise((r) => setTimeout(r, 20));
  }
  const answer = await fetch(`${MOCK}/mock/${action}`, { method: "POST", body: JSON.stringify({ payCheckNumber: n, changes }) });
  assert.equal(answer.status, 200, await answer.text());
}

async function sold(n: string) {
  const call = client.sendPreReceipt(sale(n, { byCash: 30000 }));
  await cashier("issue", n);
  return call;
}

beforeEach(async () => {
  if (up) await fetch(`${MOCK}/mock/reset`, { method: "POST" });
});

test("S1 sale issued", { skip }, async () => {
  const outcome = await sold("S1");
  assert.equal(outcome.kind, "issued");
  if (outcome.kind !== "issued") return;
  assert.equal(outcome.result.changed, false);
  assert.deepEqual(outcome.result.payments, [{ type: "CASH", amount: 30000 }]);
});

test("S2 cashier lowers and pays card", { skip }, async () => {
  const call = client.sendPreReceipt(sale("S2"));
  await cashier("issue", "S2", { items: [{ sku: "2050", amount: 1 }], payments: [{ type: "CARD", amount: 15000 }] });
  const outcome = await call;
  assert.equal(outcome.kind, "issued");
  if (outcome.kind !== "issued") return;
  assert.equal(outcome.result.changed, true);
  assert.equal(outcome.result.items?.[0].amount, 1);
  assert.deepEqual(outcome.result.payments, [{ type: "CARD", amount: 15000 }]);
});

test("S3 idle answers waiting, status later issued", { skip }, async () => {
  const outcome = await client.sendPreReceipt(sale("S3", { byCash: 30000 }));
  assert.equal(outcome.kind, "waiting");
  await cashier("issue", "S3");
  const later = await client.waitForResult("S3", 5000);
  assert.equal(later.kind, "issued");
});

test("S3 a client timeout is waiting, not a failure", { skip }, async () => {
  const short = new MobilkassaClient({ url: `${MOCK}/pospoint`, key: "mock-kassa-key", timeoutMs: 300 });
  assert.equal((await short.sendPreReceipt(sale("S3T"))).kind, "waiting");
});

test("S4 rejected, corrected re-send accepted", { skip }, async () => {
  const call = client.sendPreReceipt(sale("S4"));
  await cashier("reject", "S4");
  assert.equal((await call).kind, "rejected");
  const again = client.sendPreReceipt(sale("S4", { items: [{ ...tea, amount: 1 }] }));
  await cashier("issue", "S4");
  const outcome = await again;
  assert.equal(outcome.kind, "issued");
  if (outcome.kind === "issued") assert.equal(outcome.result.total, 15000);
});

// 14824 on a re-send: the document is already issued, so the client asks its status and answers issued.
test("S5 repeat of an issued document answers issued, no second receipt", { skip }, async () => {
  await sold("S5");
  const outcome = await client.sendPreReceipt(sale("S5", { byCash: 30000 }));
  assert.equal(outcome.kind, "issued");
  if (outcome.kind === "issued") assert.equal(outcome.result.total, 30000);
  const state = await fetch(`${MOCK}/mock/state`).then((r) => r.json());
  assert.equal(state.done.length, 1);
});

test("S6 partial refund by sku", { skip }, async () => {
  await sold("S6");
  const call = client.sendRefundPreReceipt({ payCheckNumber: "S6-R", salePayCheckNumber: "S6", items: [{ sku: "2050", amount: 1 }] });
  await cashier("issue", "S6-R");
  const outcome = await call;
  assert.equal(outcome.kind, "issued");
  if (outcome.kind !== "issued") return;
  assert.equal(outcome.result.type, "REFUND");
  assert.deepEqual(outcome.result.items?.map((i) => [i.sku, i.amount]), [["2050", 1]]);
});

test("S7 refund over the remainder answers 14807", { skip }, async () => {
  await sold("S7");
  const outcome = await client.sendRefundPreReceipt({ payCheckNumber: "S7-R", salePayCheckNumber: "S7", items: [{ sku: "2050", amount: 3 }] });
  assert.equal(outcome.kind, "failed");
  if (outcome.kind === "failed") assert.equal(outcome.errors[0].code, 14807);
});

test("id goes out as a string of digits", { skip }, async () => {
  await client.getPreReceiptStatus("NONE");
  const log = await fetch(`${MOCK}/mock/log`).then((r) => r.json());
  assert.match(log[0].request, /"id":"\d+"/);
});

test("a kassa that is down is a failure", async () => {
  const down = new MobilkassaClient({ url: "http://127.0.0.1:1/pospoint", key: "k", timeoutMs: 2000 });
  const outcome = await down.getPreReceiptStatus("X");
  assert.equal(outcome.kind, "failed");
  if (outcome.kind === "failed") assert.equal(outcome.errors[0].code, 0);
});
