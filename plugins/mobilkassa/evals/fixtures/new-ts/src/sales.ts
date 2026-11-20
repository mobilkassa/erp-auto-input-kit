// Sales: the cashier's order becomes a closed sale.
import { productById } from "./products.ts";

export interface SaleLine {
  productId: string;
  qty: number;
}

export interface Sale {
  number: string;
  lines: SaleLine[];
  cash: number;
  card: number;
  state: "open" | "closed";
}

let counter = 0;
const sales = new Map<string, Sale>();

export function openSale(lines: SaleLine[], cash: number, card: number): Sale {
  counter += 1;
  const sale: Sale = { number: `S-${counter}`, lines, cash, card, state: "open" };
  sales.set(sale.number, sale);
  return sale;
}

export function saleTotal(sale: Sale): number {
  return sale.lines.reduce((sum, line) => sum + productById(line.productId).price * line.qty, 0);
}

export function closeSale(number: string): Sale {
  const sale = sales.get(number);
  if (!sale) throw new Error(`no sale ${number}`);
  sale.state = "closed";
  return sale;
}

export function saleByNumber(number: string): Sale | undefined {
  return sales.get(number);
}
