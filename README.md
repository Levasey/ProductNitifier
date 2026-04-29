# ProductNitifier

Демонстрация событийной связки микросервисов на **Spring Boot 4** и **Apache Kafka**: создание продукта через HTTP, публикация события `ProductCreatedEvent` и его обработка во втором сервисе (логирование «уведомления»).

## Состав репозитория

| Модуль | Назначение |
|--------|------------|
| [`core`](core/) | Общий контракт событий и ошибок (`ProductCreatedEvent`, `DepositRequestedEvent`, `WithdrawalRequestedEvent`, `RetryableException`, `NotRetryableException`) как библиотека для всех сервисов. |
| [`ProductMicroservice`](ProductMicroservice/) | REST API: `POST /product` → запись в Kafka, топик **`product-created-events-topic`**. |
| [`EmailNotificationMicroservice`](EmailNotificationMicroservice/) | Consumer группы `product-created-events`: читает тот же топик, вызывает mock HTTP endpoint и применяет retry/DLT стратегию по типу ошибки. |
| [`TransferService`](TransferService/) | REST API переводов (`POST /transfers`) и публикация событий запросов на списание/зачисление. |
| [`WithdrawalService`](WithdrawalService/) | Consumer события `WithdrawalRequestedEvent`: обработка шага списания в transfer-flow. |
| [`DepositService`](DepositService/) | Consumer события `DepositRequestedEvent`: обработка шага зачисления в transfer-flow. |
| [`mockservice`](mockservice/) | Локальный HTTP стаб для проверки сценариев успеха/ошибки (`/response/200` и `/response/500`). |

Топики и сериализация JSON настраиваются в коде и в `application.properties` каждого сервиса.

## Event-driven сценарии

В проекте сейчас реализованы два независимых сценария:

1. **Product Notification flow**
   - `ProductMicroservice` публикует `ProductCreatedEvent`.
   - `EmailNotificationMicroservice` читает событие и выполняет отправку уведомления (через mock endpoint).

2. **Transfer flow (оркестрация через события)**
   - `TransferService` принимает HTTP-запрос на перевод.
   - Публикуется `WithdrawalRequestedEvent` для шага списания.
   - Публикуется `DepositRequestedEvent` для шага зачисления.
   - `WithdrawalService` и `DepositService` обрабатывают соответствующие события.

Оба сценария используют общий модуль `core` для единых event-контрактов между сервисами.

## Требования

- **JDK 17**
- **Maven** 3.6+ или **`./mvnw`** в каталоге нужного модуля
- Доступный **Kafka** (`spring.kafka.bootstrap-servers` по умолчанию: `localhost:9092,localhost:9094` в обоих сервисах)

## Сборка и запуск

В корне репозитория есть **агрегирующий** [`pom.xml`](pom.xml) (packaging `pom`): из каталога проекта можно собрать всё сразу — Maven подтянет зависимости между модулями (`core` → сервисы):

```bash
cd /path/to/ProductNitifier
mvn clean install
```

Либо по отдельности: сначала установите **`core`** в локальный репозиторий Maven, затем остальные модули.

```bash
# 1. Установить общую библиотеку событий
cd core
./mvnw install -DskipTests

# 2. Локальный mock-сервис для проверки HTTP-ответов (в отдельном терминале)
cd ../mockservice
./mvnw spring-boot:run

# 3. Продюсер (в отдельном терминале)
cd ../ProductMicroservice
./mvnw spring-boot:run

# 4. Консьюмер (в отдельном терминале)
cd ../EmailNotificationMicroservice
./mvnw spring-boot:run
```

Для transfer-flow поднимите сервисы в отдельных терминалах:

```bash
# 5. REST API переводов
cd ../TransferService
./mvnw spring-boot:run

# 6. Consumer списаний
cd ../WithdrawalService
./mvnw spring-boot:run

# 7. Consumer зачислений
cd ../DepositService
./mvnw spring-boot:run
```

У обоих сервисов **`server.port=0`** — фактический HTTP-порт смотрите в логах (`Tomcat started on port ...`). Для вызова API используйте этот порт.

Пример создания продукта:

```bash
curl -s -X POST "http://localhost:<PORT_PRODUCT>/product" \
  -H "Content-Type: application/json" \
  -d '{"title":"Sample","price":19.99,"quantity":10}'
```

В логах **EmailNotificationMicroservice** должно появиться сообщение вида `Product created event received: Sample`.

Пример создания перевода:

```bash
curl -s -X POST "http://localhost:<PORT_TRANSFER>/transfers" \
  -H "Content-Type: application/json" \
  -d '{"senderId":"user-1","recipientId":"user-2","amount":120.50}'
```

После вызова ожидайте логи обработки в `WithdrawalService` и `DepositService`.

## Kafka через Docker Compose

В репозитории есть `docker-compose.yml` с Kafka в KRaft-режиме (3 брокера, без Zookeeper).

```bash
# запуск кластера
docker compose up -d

# статус сервисов
docker compose ps
```

Bootstrap-серверы для приложений на хосте:

- `localhost:9092`
- `localhost:9094`
- `localhost:9096`

## Где смотреть логи Kafka

Логи Kafka пишутся в `stdout/stderr` контейнеров (стандартный docker logging driver).

```bash
# все Kafka-сервисы
docker compose logs -f kafka-1 kafka-2 kafka-3

# только один брокер
docker compose logs -f kafka-1
```

Важно: в `docker-compose.yml` не настроен отдельный файловый лог-том; персистентно сохраняются только данные Kafka (`/bitnami/kafka`) в именованных томах.

## Troubleshooting KRaft

Если один брокер (например, `kafka-1`) уходит в `Exited` с ошибками metadata log, пересоздайте только его том:

```bash
docker compose rm -f kafka-1
docker volume rm productnitifier_kafka-1-data
docker compose up -d kafka-1
docker compose ps
```

Это переинициализирует данные только проблемного брокера, не затрагивая остальные сервисы.

## Профили EmailNotificationMicroservice

В `EmailNotificationMicroservice` URL mock endpoint вынесен в профильные конфиги:

- `application-dev.properties` → `mockservice.response-url=http://localhost:8090/response/200`
- `application-test.properties` → `mockservice.response-url=http://localhost:8090/response/500`
- в `application.properties` задан `spring.profiles.default=dev`

Примеры запуска consumer:

```bash
# Сценарий успеха (200), по умолчанию
cd EmailNotificationMicroservice
./mvnw spring-boot:run

# Сценарий ошибки (500) для проверки retry/DLT
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

## H2 и веб-консоль (EmailNotificationMicroservice)

Сервис использует **in-memory H2** для JPA (настройки в `application.properties`: `jdbc:h2:mem:testdb`, логин `test`, пароль `test`).

**Spring Boot 4** не подключает веб-консоль H2 «из коробки**: автоконфигурация вынесена в отдельный артефакт. В `EmailNotificationMicroservice/pom.xml` должна быть зависимость `spring-boot-h2console`; без неё `spring.h2.console.enabled=true` не регистрирует UI, и запросы к `/h2-console` дают **404**.

После запуска consumer смотрите **фактический порт** в логе (`server.port=0`) и откройте в браузере:

`http://localhost:<порт-из-логов>/h2-console`

С теми же учётными данными, что и у DataSource, подключитесь к `jdbc:h2:mem:testdb` (только пока JVM процесса ещё жив: база in-memory).

## Документация по ProductMicroservice

Подробно про API, настройки продюсера Kafka, топик и Postman — в [**ProductMicroservice/README.md**](ProductMicroservice/README.md). Команды для Kafka CLI — в [**ProductMicroservice/KAFKA-KOMANDY.md**](ProductMicroservice/KAFKA-KOMANDY.md).

## Тесты

Быстрый запуск тестов по модулю переводов:

```bash
cd TransferService
mvn test
```

Что покрыто в `TransferService`:

- `TransferServiceImplTest` проверяет happy-path и ошибки (недоступный remote-service, ошибка Kafka, ошибка сохранения в `TransferRepository`) и гарантирует корректную публикацию/непубликацию событий.
- `KafkaConfigTest` проверяет критичные producer-настройки, создание Kafka/JPA transaction manager и именование топиков.

## Стек

- Java 17, Spring Boot 4.0.x, Spring Kafka  
- Общий контракт событий: модуль `core`
