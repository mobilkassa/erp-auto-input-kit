// End-of-day X report from the kassa. This call keeps working after 1 December.

const KASSA_URL = process.env.KASSA_URL ?? "http://127.0.0.1:6461/pospoint";
const KASSA_KEY = process.env.KASSA_KEY ?? "";

export async function xReport(): Promise<unknown> {
  const response = await fetch(KASSA_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: "900", key: KASSA_KEY, version: "1.0.2", method: "getXReport", params: {} }),
  });
  return (await response.json()).result;
}
