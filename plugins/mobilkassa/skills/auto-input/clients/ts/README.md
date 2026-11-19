# Клиент TypeScript (Node, Electron)

`mobilkassa-client.ts` — клиент методов предчеков порта 6461 без зависимостей. Работает в Node 18+ и в главном процессе Electron.

## Как подключить

1. Скопируйте `mobilkassa-client.ts` в учётную систему (для JavaScript уберите типы или соберите через `tsc`).
2. Создайте клиент с адресом и ключом из блока «Автоввод данных чека» в настройках кассы:

```ts
import { MobilkassaClient } from "./mobilkassa-client";

const kassa = new MobilkassaClient({ url: "http://192.168.1.20:6461/pospoint", key: "<ключ кассы>" });

const outcome = await kassa.sendPreReceipt({
  payCheckNumber: invoice.number, // свой номер документа: повтор с ним безопасен
  type: "SALE",
  items: invoice.lines.map((l) => ({
    name: l.name, amount: l.qty, price: l.price, mxik: l.mxik, ownerType: l.ownerType,
    unit: l.unitCode, packageCode: l.packageCode, taxPercent: l.vat, sku: l.id,
  })),
});

switch (outcome.kind) {
  case "issued":   saveReceipt(invoice, outcome.result); break;            // итоговый состав и оплата
  case "waiting":  showWaiting(invoice); await kassa.waitForResult(invoice.number, 10 * 60_000); break;
  case "rejected": showRejected(invoice); break;                             // «отклонён кассиром»
  case "failed":   showErrors(outcome.errors); break;
}
```

## Electron

Вызывайте клиент из главного процесса (`main`), а не из окна (`renderer`): из окна запрос к другому адресу упрётся в CORS и политику безопасности. Окну передавайте результат через IPC.

## Таймаут

По умолчанию 120 секунд: касса держит вызов до 90 секунд, пока кассир не начнёт работу, и без ограничения, когда начал. Таймаут — это не ошибка: клиент отвечает `waiting`, дальше спрашивайте `getPreReceiptStatus` (`waitForResult`).

## Тесты

Тесты идут против тестовой кассы: запустите её с `--idle-seconds 2`, затем

```
MOCK_URL=http://127.0.0.1:6461 node --test mobilkassa-client.test.ts
```

Без тестовой кассы тесты сценариев пропускаются.
