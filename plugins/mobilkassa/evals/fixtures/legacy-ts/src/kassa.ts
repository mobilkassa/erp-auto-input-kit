// Receipts through the Mobilkassa kassa, the old way: the ERP issues the fiscal receipt itself.

const KASSA_URL = process.env.KASSA_URL ?? "http://127.0.0.1:6461/pospoint";
const KASSA_KEY = process.env.KASSA_KEY ?? "";
let nextId = 1;

export async function callKassa(method: string, params: object): Promise<any> {
  const response = await fetch(KASSA_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: String(nextId++), key: KASSA_KEY, version: "1.0.2", method, params }),
    signal: AbortSignal.timeout(30_000),
  });
  const answer = await response.json();
  if (answer.error) throw new Error(`kassa: ${answer.error[0].code} ${answer.error[0].message}`);
  return answer.result;
}

export interface InvoiceLine {
  productId: string;
  name: string;
  qty: number;
  price: number;
  mxik: string;
  unitCode: string;
  packageCode: number;
  vat: number;
}

export interface Invoice {
  number: string;
  lines: InvoiceLine[];
  cash: number;
  card: number;
}

export interface FiscalReceipt {
  receiptId: number;
  fiscalSign: string;
  qrUrl: string;
  fmNo: string;
  shift: number;
}

export async function fiscalize(invoice: Invoice): Promise<FiscalReceipt> {
  const receipt = await callKassa("registerSaleFull", {
    items: invoice.lines.map((l) => ({
      name: l.name, amount: l.qty, price: l.price, mxik: l.mxik, ownerType: 0,
      unit: l.unitCode, packageCode: l.packageCode, taxPercent: l.vat,
    })),
    byCash: invoice.cash,
    byCard: invoice.card,
  });
  return { receiptId: receipt.id, fiscalSign: receipt.fiscalSign, qrUrl: receipt.url, fmNo: receipt.fmNo, shift: receipt.shiftNumber };
}

export async function refund(receiptId: number, lines: InvoiceLine[], cash: number): Promise<string> {
  const receipt = await callKassa("registerRefundFull", {
    idRefundReceipt: receiptId,
    items: lines.map((l) => ({ name: l.name, amount: l.qty, price: l.price, mxik: l.mxik })),
    byCash: cash,
    byCard: 0,
  });
  return receipt.fiscalSign;
}

export async function findReceipt(invoiceNumber: string): Promise<FiscalReceipt | null> {
  const receipt = await callKassa("getReceiptByPayCheckNumber", { payCheckNumber: invoiceNumber });
  return receipt ? { receiptId: receipt.id, fiscalSign: receipt.fiscalSign, qrUrl: receipt.url, fmNo: receipt.fmNo, shift: receipt.shiftNumber } : null;
}
