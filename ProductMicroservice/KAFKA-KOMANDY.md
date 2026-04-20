# Kafka — команды и пояснения (Linux, KRaft)

Каталог установки: `~/kafka/current`  
Переменная окружения (удобно добавить в `~/.bashrc`):

```bash
export KAFKA_HOME="$HOME/kafka/current"
export PATH="$KAFKA_HOME/bin:$PATH"
```

В этой сборке используется **KRaft** (не ZooKeeper). Основной конфиг одной ноды:  
`$KAFKA_HOME/config/server.properties`  
(файла `config/kraft/server.properties` в дистрибутиве нет.)

---

## 1. Сгенерировать UUID кластера

Идентификатор кластера должен быть **валидным UUID**, не произвольной строкой.

```bash
"$KAFKA_HOME/bin/kafka-storage.sh" random-uuid
```

Сохраните вывод в переменную:

```bash
CLUSTER_ID=$("$KAFKA_HOME/bin/kafka-storage.sh" random-uuid)
echo "$CLUSTER_ID"
```

---

## 2. Первичное форматирование хранилища (один раз)

Выполняется **до первого** запуска брокера, с тем же конфигом, с которым потом будете стартовать.

Флаг **`--standalone`** нужен, если в `server.properties` задан  
`controller.quorum.bootstrap.servers`, а **`controller.quorum.voters` не задан**  
(типичный пример из официального tarball для одной ноды).

```bash
CLUSTER_ID=$("$KAFKA_HOME/bin/kafka-storage.sh" random-uuid)

"$KAFKA_HOME/bin/kafka-storage.sh" format \
  -t "$CLUSTER_ID" \
  -c "$KAFKA_HOME/config/server.properties" \
  --standalone
```

**Пояснение `--standalone`:** инициализация **одноузлового** динамического кворума контроллера; без него `format` может потребовать `--initial-controllers`, `--no-initial-controllers` или настройку `controller.quorum.voters`.

Если каталоги уже форматировались и нужно не падать на повторе:

```bash
"$KAFKA_HOME/bin/kafka-storage.sh" format ... --ignore-formatted
```
(остальные аргументы те же.)

---

## 3. Запуск брокера

**Обязательно** тот же конфиг, что и при `format`:

```bash
"$KAFKA_HOME/bin/kafka-server-start.sh" "$KAFKA_HOME/config/server.properties"
```

**Остановка брокера**

- В том же терминале, где сервер запущен на переднем плане: **`Ctrl+C`** (аккуратное завершение).

- Из **другого** терминала или если процесс в **фоне** (`nohup` и т.п.):

```bash
"$KAFKA_HOME/bin/kafka-server-stop.sh"
```

Без аргументов скрипт завершает **все** локальные процессы `kafka.Kafka` (если у вас несколько брокеров на машине — остановятся все). Чтобы остановить **один** узел по `node.id` из конфига:

```bash
"$KAFKA_HOME/bin/kafka-server-stop.sh" --node-id=1
```

(подставьте `2` или `3` для других брокеров из раздела 4).

Либо в фоне (пример):

```bash
nohup "$KAFKA_HOME/bin/kafka-server-start.sh" "$KAFKA_HOME/config/server.properties" \
  > "$HOME/kafka/kafka.log" 2>&1 &
```

---

## 4. Кластер из трёх брокеров (KRaft, локально)

В каталоге `~/kafka` лежат три отдельных конфига:

| Узел | Файл | Клиент (PLAINTEXT) | Контроллер (CONTROLLER) |
|------|------|--------------------|-------------------------|
| 1 | `~/kafka/server-1/server-1.properties` | 9092 | 9093 |
| 2 | `~/kafka/server-2/server-2.properties` | 9094 | 9095 |
| 3 | `~/kafka/server-3/server-3.properties` | 9096 | 9097 |

У каждого узла свой `node.id` и свой `log.dirs`; во всех конфигах совпадает строка `controller.quorum.voters` (статический кворум).

### 4.1. Какие параметры менять в `server-1.properties` (и в `server-2`, `server-3`)

Удобный способ: скопировать шаблон `$KAFKA_HOME/config/server.properties` три раза в `~/kafka/server-1/server-1.properties` и т.д., затем привести каждый файл к своему узлу.

**Одинаково во всех трёх файлах** (копия с одного эталона, без правок между собой, кроме общей строки кворума):

| Параметр | Значение | Зачем |
|----------|----------|--------|
| `process.roles` | `broker,controller` | KRaft: на каждой машине и брокер, и контроллер (комбинированная нода). |
| `controller.quorum.voters` | `1@localhost:9093,2@localhost:9095,3@localhost:9097` | Статический кворум: **id** каждого узла, **хост** и **порт listener CONTROLLER** этого узла. Строка **одна и та же** в `server-1`, `server-2`, `server-3`. |

**Не должно быть** в конфиге кластера с `controller.quorum.voters`: строки `controller.quorum.bootstrap.servers` (она для одноузлового шаблона без `voters`). Если копируете из дефолтного `server.properties`, удалите `controller.quorum.bootstrap.servers`.

**Свой на каждый файл** (для `server-1` — пример ниже; для `server-2` и `server-3` — те же имена параметров, но свои номера и порты из таблицы выше):

| Параметр | Пример для `server-1.properties` | Пояснение |
|----------|-----------------------------------|-----------|
| `node.id` | `1` | Уникальный целочисленный id узла; в `server-2` → `2`, в `server-3` → `3`. Должен совпадать с id в `controller.quorum.voters`. |
| `listeners` | `PLAINTEXT://:9092,CONTROLLER://:9093` | Свои **два** порта на узел: клиентский (`PLAINTEXT`) и кворум контроллеров (`CONTROLLER`). У второго узла, например: `PLAINTEXT://:9094,CONTROLLER://:9095`. |
| `advertised.listeners` | `PLAINTEXT://localhost:9092` | То, что отдают клиентам для `PLAINTEXT`; порт как у `PLAINTEXT` в `listeners`. На удалённых хостах вместо `localhost` укажите реальное имя или IP. |
| `inter.broker.listener.name` | `PLAINTEXT` | Обычно без изменений; совпадает с именем listener для трафика между брокерами. |
| `log.dirs` | `/tmp/server-1/kraft-combined-logs` | **Отдельный каталог на каждый узел**; нельзя указывать один и тот же путь для двух процессов. Для узла 2/3: например `/tmp/server-2/...`, `/tmp/server-3/...`. |

После правок выполните `kafka-storage.sh format` с **одним** `CLUSTER_ID` для всех трёх конфигов (подраздел 4.2).

### 4.2. Форматирование хранилища (один раз)

Один и тот же **CLUSTER_ID** для всех трёх узлов. Флаг **`--standalone`** здесь **не** используется (он для одной ноды с `controller.quorum.bootstrap.servers` без `controller.quorum.voters`).

```bash
export KAFKA_HOME="$HOME/kafka/current"
CLUSTER_ID=$("$KAFKA_HOME/bin/kafka-storage.sh" random-uuid)
echo "$CLUSTER_ID"

for cfg in "$HOME/kafka/server-1/server-1.properties" \
           "$HOME/kafka/server-2/server-2.properties" \
           "$HOME/kafka/server-3/server-3.properties"; do
  "$KAFKA_HOME/bin/kafka-storage.sh" format -t "$CLUSTER_ID" -c "$cfg" --ignore-formatted
done
```

Повторный запуск `format` на уже отформатированных каталогах: оставьте `--ignore-formatted`, как в примере.

### 4.3. Запуск трёх брокеров в разных терминалах

Откройте **три** отдельных окна или вкладки терминала. В **каждом** выполните **один** из блоков ниже (своему окну — свой блок). Порядок запуска обычно не важен; удобно идти по номерам 1 → 2 → 3.

Если каталога `~/kafka/current` нет, задайте путь к дистрибутиву вручную, например:

```bash
export KAFKA_HOME="$HOME/kafka/kafka_2.13-4.0.2"
```

**Терминал 1 — брокер 1**

```bash
export KAFKA_HOME="$HOME/kafka/current"
"$KAFKA_HOME/bin/kafka-server-start.sh" "$HOME/kafka/server-1/server-1.properties"
```

**Терминал 2 — брокер 2**

```bash
export KAFKA_HOME="$HOME/kafka/current"
"$KAFKA_HOME/bin/kafka-server-start.sh" "$HOME/kafka/server-2/server-2.properties"
```

**Терминал 3 — брокер 3**

```bash
export KAFKA_HOME="$HOME/kafka/current"
"$KAFKA_HOME/bin/kafka-server-start.sh" "$HOME/kafka/server-3/server-3.properties"
```

**Остановка:** в соответствующем терминале — **`Ctrl+C`**, либо из любого терминала:

```bash
"$KAFKA_HOME/bin/kafka-server-stop.sh" --node-id=1
"$KAFKA_HOME/bin/kafka-server-stop.sh" --node-id=2
"$KAFKA_HOME/bin/kafka-server-stop.sh" --node-id=3
```

Чтобы остановить **все** три брокера сразу (без указания id):

```bash
export KAFKA_HOME="$HOME/kafka/kafka_2.13-4.0.2"
"$KAFKA_HOME/bin/kafka-server-stop.sh"
```

### 4.4. Bootstrap для клиентов

Укажите все три порта брокера:

```text
localhost:9092,localhost:9094,localhost:9096
```

### 4.5. Проверка кворума

```bash
"$KAFKA_HOME/bin/kafka-metadata-quorum.sh" --bootstrap-server localhost:9092 describe --status
```

---

## 5. Проверка версии

```bash
"$KAFKA_HOME/bin/kafka-server-start.sh" --version
```

---

## 6. Примеры работы с топиками (после запуска брокера)

Создать топик:

```bash
"$KAFKA_HOME/bin/kafka-topics.sh" \
  --bootstrap-server localhost:9092 \
  --create \
  --topic test \
  --partitions 1 \
  --replication-factor 1
```

Список топиков:

```bash
"$KAFKA_HOME/bin/kafka-topics.sh" \
  --bootstrap-server localhost:9092 \
  --list
```

Описание топика:

```bash
"$KAFKA_HOME/bin/kafka-topics.sh" \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic test
```

Порт клиента по умолчанию в однонодовом конфиге: **9092** (смотрите `listeners` / `advertised.listeners` в `server.properties`).

Если подняты **все три брокера** (раздел 4), подставьте в `--bootstrap-server` три адреса — тогда команды те же, например список топиков:

```bash
BS="localhost:9092,localhost:9094,localhost:9096"
"$KAFKA_HOME/bin/kafka-topics.sh" --bootstrap-server "$BS" --list
```

Топик с репликацией по кластеру (пример: три реплики, три партиции — только когда работают три брокера):

```bash
BS="localhost:9092,localhost:9094,localhost:9096"
"$KAFKA_HOME/bin/kafka-topics.sh" \
  --bootstrap-server "$BS" \
  --create \
  --topic test-rf3 \
  --partitions 3 \
  --replication-factor 3
```

### 6.1. Пример сессии: два топика, список, описание, удаление и повторное создание

Задайте каталог дистрибутива (симлинк `~/kafka/current` на распакованный Kafka):

```bash
export KAFKA_HOME="$HOME/kafka/current"
```

Пример сессии в терминале (список, затем создание топика):

```bash
"$KAFKA_HOME/bin/kafka-topics.sh" --list --bootstrap-server localhost:9092,localhost:9094

"$KAFKA_HOME/bin/kafka-topics.sh" --create --topic payment-sent-events-topic \
  --partitions 3 --replication-factor 3 \
  --bootstrap-server localhost:9092,localhost:9094
```

Ожидаемый вывод после `--create`: `Created topic payment-sent-events-topic.`

Второй топик с теми же параметрами (нужны **три** работающих брокера; в `--bootstrap-server` достаточно указать любые узлы кластера, например два порта на одной машине):

```bash
"$KAFKA_HOME/bin/kafka-topics.sh" --create --topic payment-created-events-topic \
  --partitions 3 --replication-factor 3 \
  --bootstrap-server localhost:9092,localhost:9094
```

Если каталога `~/kafka/current` нет, укажите путь к распакованному архиву вручную, например:

```bash
export KAFKA_HOME="$HOME/kafka/kafka_2.13-4.0.2"
```

Чтобы вызывать `kafka-topics.sh` без префикса, один раз в сессии или в `~/.bashrc`:

```bash
export PATH="$KAFKA_HOME/bin:$PATH"
kafka-topics.sh --list --bootstrap-server localhost:9092,localhost:9094
```

Описание **всех** топиков (без `--topic` — вывод по каждому топику: партиции, лидер, реплики, ISR):

```bash
"$KAFKA_HOME/bin/kafka-topics.sh" --describe --bootstrap-server localhost:9092,localhost:9094
```

Удалить топик и проверить список:

```bash
"$KAFKA_HOME/bin/kafka-topics.sh" --delete --topic payment-sent-events-topic \
  --bootstrap-server localhost:9092,localhost:9094

"$KAFKA_HOME/bin/kafka-topics.sh" --list --bootstrap-server localhost:9092,localhost:9094
```

Создать топик с тем же именем снова:

```bash
"$KAFKA_HOME/bin/kafka-topics.sh" --create --topic payment-sent-events-topic \
  --partitions 3 --replication-factor 3 \
  --bootstrap-server localhost:9092,localhost:9094
```

### 6.2. Console producer с ключом (`parse.key`, `key.separator`)

Топик `payment-canceled-events-topic` в кластере из трёх брокеров (см. раздел 4) создают так:

```bash
"$KAFKA_HOME/bin/kafka-topics.sh" --create --topic payment-canceled-events-topic \
  --partitions 3 --replication-factor 3 \
  --bootstrap-server localhost:9092,localhost:9094
```

Дальше можно писать сообщения, где **ключ и значение** разделены первым вхождением разделителя (по умолчанию `:`). Формат строки в stdin: `ключ:значение`.

```bash
"$KAFKA_HOME/bin/kafka-console-producer.sh" \
  --bootstrap-server localhost:9092,localhost:9094 \
  --topic payment-canceled-events-topic \
  --property parse.key=true \
  --property key.separator=:
```

Дальше вводите строки и завершайте сессию **`Ctrl+D`** (EOF). Пример строк:

```text
order-1001:{"reason":"user_cancel","ts":"2026-04-14T10:00:00Z"}
order-1002:{"reason":"timeout","ts":"2026-04-14T10:01:00Z"}
```

Если в значении нужен символ `:` — он попадёт в тело сообщения целиком после первого `:` (разделитель только между ключом и остатком строки).

### 6.3. Console consumer: формат вывода и `--property`

**`--from-beginning`** — читать с самого старого смещения в топике. Без этого флага consumer подхватывает **только сообщения, опубликованные после запуска** (полезно для «живого» просмотра). Завершение сессии: **`Ctrl+C`** (в конце будет строка вида `Processed a total of N messages`).

**По умолчанию** выводится только **значение** (тело сообщения), по строке на сообщение:

```bash
"$KAFKA_HOME/bin/kafka-console-consumer.sh" \
  --bootstrap-server localhost:9092,localhost:9094 \
  --topic payment-canceled-events-topic \
  --from-beginning
```

Пример вывода (если в топике писали JSON без вывода ключа в producer):

```text
{"reason":"user_cancel","ts":"2026-04-14T10:00:00Z"}
{"reason":"timeout","ts":"2026-04-14T10:01:00Z"}
```

**Ключ и значение:** включите `print.key=true`. Между ключом и значением по умолчанию ставится **табуляция** (`\t`):

```bash
"$KAFKA_HOME/bin/kafka-console-consumer.sh" \
  --bootstrap-server localhost:9092,localhost:9094 \
  --topic payment-canceled-events-topic \
  --from-beginning \
  --property print.key=true
```

Пример:

```text
order-1001	{"reason":"user_cancel","ts":"2026-04-14T10:00:00Z"}
order-1002	{"reason":"timeout","ts":"2026-04-14T10:01:00Z"}
```

Чтобы вместо таба использовать тот же разделитель, что и в producer (`:`), добавьте `key.separator`:

```bash
"$KAFKA_HOME/bin/kafka-console-consumer.sh" \
  --bootstrap-server localhost:9092,localhost:9094 \
  --topic payment-canceled-events-topic \
  --from-beginning \
  --property print.key=true \
  --property key.separator=:
```

**Только ключи** (без тела сообщения): `print.key=true` и `print.value=false`:

```bash
"$KAFKA_HOME/bin/kafka-console-consumer.sh" \
  --bootstrap-server localhost:9092,localhost:9094 \
  --topic payment-canceled-events-topic \
  --from-beginning \
  --property print.key=true \
  --property print.value=false
```

**Почему при одном только `print.value=false` на экране пусто:** по умолчанию `print.key=false`. Тогда отключается и ключ, и значение — сообщения **обрабатываются** (счётчик в конце растёт), но **ничего не печатается**. Чтобы что-то увидеть, включайте хотя бы `print.key=true` или верните `print.value=true` (это значение по умолчанию для значения).

**Каждое свойство — отдельный аргумент `--property`:** неверно ` --property print.key=true print.value=false ` (второе не попадёт в formatter). Нужно:

```text
--property print.key=true --property print.value=false
```

**Ошибка `…: команда не найдена` при вставке из чата:** в буфер попала **вторая копия** строки приглашения (`user@…~$`). Оболочка пытается выполнить её как имя команды. Вставляйте только вызов `kafka-console-consumer.sh` и аргументы, **без** префикса `user@…:~$`.

Для кластера из трёх брокеров (раздел 4) в `--bootstrap-server` можно указать все три адреса, например `localhost:9092,localhost:9094,localhost:9096` — достаточно любого подмножества узлов для обнаружения метаданных.

### 6.4. Изменение настроек топика (`kafka-configs`)

Скрипт: **`$KAFKA_HOME/bin/kafka-configs.sh`** (при необходимости задайте `KAFKA_HOME`, см. начало файла).

Пример: для топика микросервиса **`product-created-events-topic`** задать **`min.insync.replicas=2`** (имеет смысл при **replication factor ≥ 2** и достаточном числе брокеров в ISR):

**Один брокер** (разработка, порт по умолчанию 9092):

```bash
"$KAFKA_HOME/bin/kafka-configs.sh" \
  --bootstrap-server localhost:9092 \
  --alter \
  --entity-type topics \
  --entity-name product-created-events-topic \
  --add-config min.insync.replicas=2
```

**Кластер из трёх брокеров** (раздел 4) — подставьте те же адреса, что и для `kafka-topics.sh`:

```bash
BS="localhost:9092,localhost:9094,localhost:9096"
"$KAFKA_HOME/bin/kafka-configs.sh" \
  --bootstrap-server "$BS" \
  --alter \
  --entity-type topics \
  --entity-name product-created-events-topic \
  --add-config min.insync.replicas=2
```

Просмотр текущих настроек топика:

```bash
"$KAFKA_HOME/bin/kafka-configs.sh" \
  --bootstrap-server localhost:9092 \
  --describe \
  --entity-type topics \
  --entity-name product-created-events-topic
```

---

## 7. Типичные ошибки

| Ошибка | Что проверить |
|--------|----------------|
| `user@…: команда не найдена` при вставке длинной строки | В команду попало **приглашение shell** (`user@…~$`) второй раз; вставляйте только текст команды. |
| `kafka-topics.sh: команда не найдена` | Скрипт не в `PATH`; вызывайте `"$KAFKA_HOME/bin/kafka-topics.sh"` или `export PATH="$KAFKA_HOME/bin:$PATH"`. |
| Неверный путь к скрипту | Скрипты в `$KAFKA_HOME/bin/`, не в системном `/bin/`. |
| Неверный путь к конфигу | Обычно `$KAFKA_HOME/config/server.properties`, не `/config/server.properties`. |
| Неверный `-t` | Только UUID из `random-uuid`. |
| Требование `--standalone` / контроллеры | Для **одной** ноды с шаблоном из tarball — `--standalone` к `format`. Для **трёх** брокеров с `controller.quorum.voters` — **без** `--standalone`, один `CLUSTER_ID` на все узлы. |
| Несовпадение с `format` | Запускайте `kafka-server-start` с тем же `.properties`, что и при `format`. |

---

## 8. Java

Скрипты ожидают установленный **JDK/JRE**. Для продакшена чаще рекомендуют **17 или 21**; при проблемах на другой версии задайте:

```bash
export JAVA_HOME=/путь/к/java
```

---

## Сводка «с нуля» (копирование блока)

**Одна нода** (шаблон `config/server.properties`):

```bash
export KAFKA_HOME="$HOME/kafka/current"
CLUSTER_ID=$("$KAFKA_HOME/bin/kafka-storage.sh" random-uuid)
"$KAFKA_HOME/bin/kafka-storage.sh" format -t "$CLUSTER_ID" \
  -c "$KAFKA_HOME/config/server.properties" --standalone
"$KAFKA_HOME/bin/kafka-server-start.sh" "$KAFKA_HOME/config/server.properties"
```

Остановка одного брокера: **`"$KAFKA_HOME/bin/kafka-server-stop.sh"`** (см. **раздел 3**).

**Три брокера** — см. **раздел 4** (форматирование циклом и три терминала с `server-1` … `server-3`).

Файл с метаданными/логами лежит в каталогах из **`log.dirs`** в выбранном `server.properties` (часто под `/tmp/...` — смотрите свой конфиг).
