// Mobilkassa auto-input client: pre-receipts on port 6461, protocol 1.0.2.
// No dependencies; Node 18+ and the Electron main process (not the renderer). Copy this file into the ERP.

export interface PreReceiptItem {
  name: string;
  amount: number;
  price: number;
  mxik: string;
  ownerType: number;
  unit: string;
  packageCode: number;
  taxPercent: 0 | 6 | 12;
  sku: string; // the ERP's position key: required, 14804 without it
  mkCode?: string[];
  discount?: number;
  barcode?: string;
  comTin?: string;
  recipeId?: string;
}

export interface PreReceiptRequest {
  payCheckNumber: string;
  type: "SALE" | "ADVANCE" | "CREDIT";
  items: PreReceiptItem[];
  byCash?: number;
  byCard?: number;
  discount?: number;
  cardType?: 0 | 1 | 2 | 3;
  rrn?: string;
  socialCardNumber?: string;
  phoneNumber?: string;
  mkDetailed?: boolean;
  markValidationSkip?: boolean;
}

export interface PreReceiptRefundItem {
  amount: number;
  sku?: string;
  uid?: number;
  mxik?: string;
  barcode?: string;
  name?: string;
  mkCode?: string[];
}

// Name the sale by salePayCheckNumber, or by saleId (the old idRefundReceipt).
export interface PreReceiptRefundRequest {
  payCheckNumber: string;
  salePayCheckNumber?: string;
  saleId?: number;
  items?: PreReceiptRefundItem[];
  byCash?: number;
  byCard?: number;
}

export interface PreReceiptResult {
  payCheckNumber: string;
  status: "WAITING" | "ISSUED" | "REJECTED";
  type?: "SALE" | "ADVANCE" | "CREDIT" | "REFUND";
  changed?: boolean;
  items?: { sku?: string; name: string; amount: number; price: number; discount: number; sum: number }[];
  payments?: { type: "CASH" | "CARD" | "ONLINE" | "DEBT"; amount: number }[];
  total?: number;
}

export interface RpcError {
  code: number;
  message: string;
}

// waiting: the cashier has not finished (14827 or an HTTP timeout) — ask getPreReceiptStatus later.
// rejected: the cashier rejected it (14828) — a corrected pre-receipt may go out with the same number.
export type Outcome =
  | { kind: "issued"; result: PreReceiptResult }
  | { kind: "waiting" }
  | { kind: "rejected" }
  | { kind: "failed"; errors: RpcError[] };

export interface ClientOptions {
  url?: string; // http://<kassa>:6461/pospoint
  key: string; // the 6461 key from the kassa's «Автоввод данных чека» settings block
  timeoutMs?: number; // above the kassa's 90 s hold
  pollMs?: number;
}

const WAITING = 14827;
const REJECTED = 14828;
const ALREADY_ISSUED = 14824;

export class MobilkassaClient {
  private readonly url: string;
  private readonly key: string;
  private readonly timeoutMs: number;
  private readonly pollMs: number;
  private nextId = 1;

  constructor(options: ClientOptions) {
    this.url = options.url ?? "http://127.0.0.1:6461/pospoint";
    this.key = options.key;
    this.timeoutMs = options.timeoutMs ?? 120_000;
    this.pollMs = options.pollMs ?? 5_000;
  }

  // Held until the cashier issues or rejects. Always send your own payCheckNumber: a repeat is then safe.
  sendPreReceipt(request: PreReceiptRequest): Promise<Outcome> {
    return this.send("sendPreReceipt", request, request.payCheckNumber);
  }

  sendRefundPreReceipt(request: PreReceiptRefundRequest): Promise<Outcome> {
    return this.send("sendRefundPreReceipt", request, request.payCheckNumber);
  }

  // 14824 on a re-send means this document was already issued (its answer was lost, or the ERP restarted):
  // its status carries the composition, so the caller gets "issued", never a failure that invites a second receipt.
  private async send(method: string, request: object, payCheckNumber: string): Promise<Outcome> {
    const outcome = await this.call(method, request);
    if (outcome.kind === "failed" && outcome.errors.some((e) => e.code === ALREADY_ISSUED)) {
      const status = await this.getPreReceiptStatus(payCheckNumber);
      if (status.kind === "issued") return status;
    }
    return outcome;
  }

  getPreReceiptStatus(payCheckNumber: string): Promise<Outcome> {
    return this.call("getPreReceiptStatus", { payCheckNumber });
  }

  // After "waiting": asks the status every pollMs until issued, rejected, failed or maxMs passes.
  async waitForResult(payCheckNumber: string, maxMs: number): Promise<Outcome> {
    const until = Date.now() + maxMs;
    for (;;) {
      const outcome = await this.getPreReceiptStatus(payCheckNumber);
      if (outcome.kind !== "waiting" || Date.now() + this.pollMs > until) return outcome;
      await new Promise((resolve) => setTimeout(resolve, this.pollMs));
    }
  }

  private async call(method: string, params: object): Promise<Outcome> {
    const body = JSON.stringify({ id: String(this.nextId++), key: this.key, version: "1.0.2", method, params });
    let answer: { result?: PreReceiptResult; error?: RpcError[] };
    try {
      const response = await fetch(this.url, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body,
        signal: AbortSignal.timeout(this.timeoutMs),
      });
      answer = await response.json();
    } catch (e) {
      if (e instanceof Error && (e.name === "TimeoutError" || e.name === "AbortError")) return { kind: "waiting" };
      return { kind: "failed", errors: [{ code: 0, message: `Нет связи с кассой: ${e instanceof Error ? e.message : String(e)}` }] };
    }
    const errors = answer.error ?? [];
    if (errors.some((e) => e.code === WAITING)) return { kind: "waiting" };
    if (errors.some((e) => e.code === REJECTED)) return { kind: "rejected" };
    if (errors.length > 0 || !answer.result) return { kind: "failed", errors };
    switch (answer.result.status) {
      case "ISSUED":
        return { kind: "issued", result: answer.result };
      case "REJECTED":
        return { kind: "rejected" };
      default:
        return { kind: "waiting" };
    }
  }
}
