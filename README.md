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
