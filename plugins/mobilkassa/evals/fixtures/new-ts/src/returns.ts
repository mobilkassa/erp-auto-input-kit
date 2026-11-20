// Returns: the customer brings back part of a closed sale.
import { saleByNumber, type SaleLine } from "./sales.ts";

export interface Return {
  number: string;
  saleNumber: string;
  lines: SaleLine[];
}

let counter = 0;

export function registerReturn(saleNumber: string, lines: SaleLine[]): Return {
  const sale = saleByNumber(saleNumber);
  if (!sale || sale.state !== "closed") throw new Error(`sale ${saleNumber} is not closed`);
  counter += 1;
  return { number: `R-${counter}`, saleNumber, lines };
}
