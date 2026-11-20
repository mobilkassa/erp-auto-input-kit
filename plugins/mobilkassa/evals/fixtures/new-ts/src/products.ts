// Product card. Prices in sum.
export interface Product {
  id: string;
  title: string;
  price: number;
  ikpu: string;
  unitCode: string;
  packageCode: number;
  vat: 0 | 6 | 12;
}

const catalog = new Map<string, Product>();

export function productById(id: string): Product {
  const product = catalog.get(id);
  if (!product) throw new Error(`no product ${id}`);
  return product;
}

export function addProduct(product: Product): void {
  catalog.set(product.id, product);
}
