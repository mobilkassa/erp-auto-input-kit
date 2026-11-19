# Учётная система на 1С по прежней схеме (пример для проверки плагина)

Фрагмент выгрузки конфигурации в файлы: общий модуль `КассаMobilkassa`. Пробивает продажу через `registerSaleFull` с таймаутом 30 секунд и без номера документа, сохраняет `fiscalSign`, `fmNo`, `shiftNumber` и печатает QR-код по `url`, возвращает через `registerRefundFull` с `idRefundReceipt`, читает чек через `getReceiptByPayCheckNumber`. `getXReport` после 1 декабря работает как раньше.
