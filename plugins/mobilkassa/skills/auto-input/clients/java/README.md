# Клиент Java

`src/main/java/uz/mobilkassa/autoinput/` — `MobilkassaClient` и `Outcome`. Java 21+, зависимость одна: Gson (`com.google.code.gson:gson`). Если в учётной системе уже есть Jackson, замените `JsonObject` на его `ObjectNode` — логика та же.

```java
MobilkassaClient kassa = new MobilkassaClient("http://192.168.1.20:6461/pospoint", "<ключ кассы>");

Outcome outcome = kassa.sendPreReceipt(preReceipt); // JsonObject: payCheckNumber, type, items, ...
switch (outcome) {
    case Outcome.Issued issued -> saveReceipt(issued.result());       // итоговый состав и оплата
    case Outcome.Waiting w -> kassa.waitForResult(number, Duration.ofMinutes(10));
    case Outcome.Rejected r -> showRejected();                         // «отклонён кассиром»
    case Outcome.Failed failed -> showErrors(failed.errors());
}
```

Таймаут запроса — 120 секунд (у `java.net.http.HttpClient` по умолчанию таймаута нет). Таймаут — это `Waiting`, не ошибка. Вызов держит поток, пока кассир не оформит чек: вызывайте клиент из фонового потока или очереди, не из потока интерфейса.

Тесты — против тестовой кассы, запущенной с `--idle-seconds 2`: `MOCK_URL=http://127.0.0.1:6461 gradle test`. Без тестовой кассы тесты пропускаются.
