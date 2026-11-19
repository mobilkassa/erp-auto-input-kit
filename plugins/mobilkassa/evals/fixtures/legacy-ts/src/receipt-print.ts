// Prints the customer's slip with the QR code of the fiscal receipt and keeps the fiscal sign in the ERP.
import { fiscalize, type Invoice } from "./kassa.ts";

const savedSigns = new Map<string, string>();

export function qrBlock(url: string): string {
  return `[QR ${url}]`;
}

export async function sellAndPrint(invoice: Invoice): Promise<string> {
  const receipt = await fiscalize(invoice);
  savedSigns.set(invoice.number, receipt.fiscalSign);
  return [`Чек ${receipt.receiptId}`, `ФМ ${receipt.fmNo}, смена ${receipt.shift}`, `ФП ${receipt.fiscalSign}`, qrBlock(receipt.qrUrl)].join("\n");
}

export function fiscalSignOf(invoiceNumber: string): string | undefined {
  return savedSigns.get(invoiceNumber);
}
