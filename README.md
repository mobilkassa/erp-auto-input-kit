# ERP Auto-Input Kit

Набор для перевода учётной системы (ERP) на «Автоввод данных чека» кассы Mobilkassa: учётная система передаёт в кассу предчек, а чек оформляет кассир. С 1 декабря 2026 года прежние методы порта 6461 `registerSale`, `registerSaleFull`, `registerRefund`, `registerRefundFull`, `getReceipt` и `getReceiptByPayCheckNumber` перенаправляются в предчеки или отклоняются.

## Состав

- `guide/` — руководство по методам предчеков, таблица кодов ошибок и примеры запросов и ответов. `guide/llms.txt` читает любой ИИ-инструмент.
- `plugins/mobilkassa/` — плагин Claude Code: `/mobilkassa:migrate` переводит учётную систему со старых вызовов на предчеки; `/mobilkassa:analyze`, `/mobilkassa:plan`, `/mobilkassa:implement` выполняют по одному шагу.
- `mock-kassa/` — тестовая касса на порту 6461: принимает предчеки, проверяет их по протоколу и показывает экран кассира в браузере.

## Установка плагина

В Claude Code:

```
/plugin marketplace add mobilkassa/erp-auto-input-kit
/plugin install mobilkassa@mobilkassa-kit
```

Затем в папке учётной системы: `/mobilkassa:migrate`.

## Команды

| Команда | Что делает | Что пишет |
|---|---|---|
| `/mobilkassa:migrate` | Весь путь: анализ, план, изменения по шагам с остановкой после каждого, сценарии S1–S8 на тестовой кассе, список приёмки | `mobilkassa-auto-input/analysis.md`, `plan.md`, `acceptance.md`, изменения кода |
| `/mobilkassa:analyze` | Находит вызовы порта 6461 и использование фискальных полей, код не меняет | `analysis.md` |
| `/mobilkassa:plan` | Пошаговый план перехода, код не меняет | `plan.md` |
| `/mobilkassa:implement [шаг]` | Один шаг плана с проверкой на тестовой кассе | изменения кода, отметка в `plan.md` |

Код меняется только после того, как разработчик одобрил план. Перед приёмкой на настоящей кассе с тестовым ФМ всё проверяется на тестовой кассе (`mock-kassa/`, ключ `mock-kassa-key`).

## Тестовая касса

```
cd mock-kassa
./gradlew run --args="--idle-seconds 5"
```

Без Java: скачайте `mock-kassa-1.0.2-windows.zip` со страницы [Releases](https://github.com/mobilkassa/erp-auto-input-kit/releases/tag/v1.0.2), распакуйте и запустите `mock-kassamock-kassa.exe --idle-seconds 5`.

Экран кассира — `http://127.0.0.1:6461/`. Если на этом компьютере работает Pospoint, он занимает порт 6461: добавьте `--port 6462`.

## Лицензия

MIT, см. `LICENSE`. Клиенты из `plugins/mobilkassa/skills/auto-input/clients/` можно копировать в код учётной системы.
