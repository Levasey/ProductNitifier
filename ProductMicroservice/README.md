# ProductMicroservice

Микросервис на **Spring Boot 4** с **Spring Kafka**: создание продукта по HTTP (`POST /product`), публикация события `ProductCreatedEvent` в топик `product-created-events-topic` (JSON).

## Требования

- **JDK 17** (в логах у вас может быть другая версия — для сборки заявлена 17 в `pom.xml`)
- **Apache Maven** 3.6+ или **`./mvnw`** в корне репозитория
- **Apache Kafka** (KRaft или ZooKeeper). Адреса брокеров задаются в [`application.properties`](src/main/resources/application.properties) через **`spring.kafka.bootstrap-servers`** (по умолчанию `localhost:9092,localhost:9094`). Используйте именно **`bootstrap-servers`** (множественное число): вариант `bootstrap-server` в Spring Boot **не подставляется** и оставляет настройки по умолчанию.

## HTTP API

| Метод | Путь | Тело запроса (JSON) | Ответ |
|--------|------|---------------------|--------|
| `POST` | `/product` | `title` (string), `price` (number), `quantity` (integer) | **201 Created**, тело — строка `productId` |

При ошибке в обработчике возможен ответ **500** с телом `ErrorMessage` (см. [`ProductController`](src/main/java/com/example/productmicroservice/controller/ProductController.java)).

`server.port=0` — порт Tomcat **случайный** при каждом запуске; смотрите строку вида `Tomcat started on port ...` в логах.

## Быстрый старт

```bash
./mvnw spring-boot:run
```

Или JAR:

```bash
./mvnw clean package
java -jar target/ProductMicroservice-0.0.1-SNAPSHOT.jar
```

Пример запроса:

```bash
curl -s -X POST "http://localhost:<PORT>/product" \
  -H "Content-Type: application/json" \
  -d '{"title":"Sample","price":19.99,"quantity":10}'
```

## Структура кода (кратко)

- [`ProductController`](src/main/java/com/example/productmicroservice/controller/ProductController.java) — HTTP-слой.
- [`ProductService`](src/main/java/com/example/productmicroservice/service/ProductService.java) — бизнес-логика и отправка в Kafka через `KafkaTemplate<String, ProductCreatedEvent>`.
- [`KafkaConfig`](src/main/java/com/example/productmicroservice/config/KafkaConfig.java) — бины `ProducerFactory`, `KafkaTemplate` и объявление топика `NewTopic`.

## Kafka

### Топик `product-created-events-topic`

- Объявляется бином `NewTopic` в [`KafkaConfig`](src/main/java/com/example/productmicroservice/config/KafkaConfig.java): **3 партиции**, **replication factor 3**, в конфиге топика **`min.insync.replicas=2`**.
- Такой RF и `min.insync.replicas` рассчитаны на **кластер из нескольких брокеров** (как минимум три узла с репликами на разных брокерах). На **одном** брокере создание топика с `replicas=3` завершится ошибкой — для локальной разработки на одном узле уменьшите `replicas` (и при необходимости `min.insync.replicas` / `acks`) под вашу среду.
- При старте приложения **KafkaAdmin** создаёт топик, если его ещё нет (`spring.kafka.admin.auto-create=true`).

### Продюсер

- Настройки продюсера задаются в [`application.properties`](src/main/resources/application.properties):
  - **`spring.kafka.producer.acks=all`** — ждём подтверждения от всех реплик в ISR (согласуется с `min.insync.replicas` на топике).
  - **`spring.kafka.producer.properties.enable.idempotence=true`** — идемпотентный продюсер (после сбоев не дублирует запись при повторной отправке).
  - **`spring.kafka.producer.properties.max.in.flight.requests.per.connection=5`** — верхняя граница неподтверждённых запросов на соединение; при включённой идемпотентности Kafka допускает значения до **5** (по умолчанию всё равно **5**).
  - Таймауты и отложенная отправка: **`delivery.timeout.ms`**, **`linger.ms`**, **`request.timeout.ms`** (значения — в файле).
- В [`KafkaConfig`](src/main/java/com/example/productmicroservice/config/KafkaConfig.java) эти свойства собираются в `ProducerFactory` / `KafkaTemplate` для типа значения `ProductCreatedEvent` (JSON-сериализация через `JsonSerializer`).

Команды для локальной Kafka (список топиков, consumer, **`kafka-configs`** для топика): [**KAFKA-KOMANDY.md**](KAFKA-KOMANDY.md).

### Частые предупреждения consumer

- **`UNKNOWN_TOPIC_OR_PARTITION`** для партиции `…-1` часто означает, что **consumer запустили до того**, как топик получил нужное число партиций, или топик ещё не создан. Запустите приложение, дождитесь успешного старта, затем снова запустите `kafka-console-consumer`, при необходимости с **новой** `--group` или `--from-beginning`.
- Убедитесь, что **`--bootstrap-server`** у CLI совпадает с тем, куда ходит приложение.

## Postman

В каталоге [`postman/`](postman/) лежат файлы для импорта:

- **`ProductMicroservice.postman_collection.json`** — коллекция с переменной `baseUrl`
- **`Local.postman_environment.json`** — окружение с `baseUrl` (по умолчанию `http://localhost:8080`)

Импорт: **File → Import** в Postman. Подставьте в `baseUrl` реальный URL из логов (из‑за `server.port=0` порт каждый раз новый).

## Зависимости и Spring Boot 4

- Для JSON в HTTP подключён **`spring-boot-starter-json`** (Jackson 3 / `tools.jackson` через Boot).
- Клиент **Kafka 4.x** при настройке продюсера ожидает классы **`com.fasterxml.jackson`**; в `pom.xml` явно добавлен **`com.fasterxml.jackson.core:jackson-databind`**, чтобы не было `ClassNotFoundException` для `TypeReference`.

## Конфигурация

Основной файл: [`src/main/resources/application.properties`](src/main/resources/application.properties).

## Сборка и тесты

```bash
./mvnw verify
```

## Стек

| Компонент | Заметка |
|-----------|---------|
| Spring Boot | 4.0.x (`pom.xml`) |
| Java | 17 |
| spring-boot-starter-kafka | да |
| spring-boot-starter-webmvc | да |
| spring-boot-starter-json | да |
| jackson-databind (com.fasterxml) | явно, для совместимости с Kafka-клиентом |
