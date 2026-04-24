# ProductNitifier

Демонстрация событийной связки микросервисов на **Spring Boot 4** и **Apache Kafka**: создание продукта через HTTP, публикация события `ProductCreatedEvent` и его обработка во втором сервисе (логирование «уведомления»).

## Состав репозитория

| Модуль | Назначение |
|--------|------------|
| [`core`](core/) | Общая модель события [`ProductCreatedEvent`](core/src/main/java/com/example/core/ProductCreatedEvent.java) (JAR, без Spring-приложения). |
| [`ProductMicroservice`](ProductMicroservice/) | REST API: `POST /product` → запись в Kafka, топик **`product-created-events-topic`**. |
| [`EmailNotificationMicroservice`](EmailNotificationMicroservice/) | Consumer группы `product-created-events`: читает тот же топик, вызывает mock HTTP endpoint и применяет retry/DLT стратегию по типу ошибки. |
| [`mockservice`](mockservice/) | Локальный HTTP стаб для проверки сценариев успеха/ошибки (`/response/200` и `/response/500`). |

Топик и сериализация JSON настраиваются в коде и в `application.properties` каждого сервиса.

## Требования

- **JDK 17**
- **Maven** 3.6+ или **`./mvnw`** в каталоге нужного модуля
- Доступный **Kafka** (`spring.kafka.bootstrap-servers` по умолчанию: `localhost:9092,localhost:9094` в обоих сервисах)

## Сборка и запуск

Модули **не** объединены родительским `pom.xml`: сначала установите **`core`** в локальный репозиторий Maven, затем запускайте сервисы.

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

У обоих сервисов **`server.port=0`** — фактический HTTP-порт смотрите в логах (`Tomcat started on port ...`). Для вызова API используйте этот порт.

Пример создания продукта:

```bash
curl -s -X POST "http://localhost:<PORT_PRODUCT>/product" \
  -H "Content-Type: application/json" \
  -d '{"title":"Sample","price":19.99,"quantity":10}'
```

В логах **EmailNotificationMicroservice** должно появиться сообщение вида `Product created event received: Sample`.

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

## Документация по ProductMicroservice

Подробно про API, настройки продюсера Kafka, топик и Postman — в [**ProductMicroservice/README.md**](ProductMicroservice/README.md). Команды для Kafka CLI — в [**ProductMicroservice/KAFKA-KOMANDY.md**](ProductMicroservice/KAFKA-KOMANDY.md).

## Стек

- Java 17, Spring Boot 4.0.x, Spring Kafka  
- Общий контракт событий: модуль `core`
