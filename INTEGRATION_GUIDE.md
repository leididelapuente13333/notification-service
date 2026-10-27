# Notification Service - Integration Guide

This guide provides technical specifications, message contracts, and code samples for integrating producer services (e.g., `user-service`, `auth-service`) with the `notification-service`.

---

## 1. Architecture Overview

Communication between producer services and `notification-service` is **asynchronous and decoupled** using a **RabbitMQ** message broker.

```
+--------------------+        AMQP (Topic)         +----------------------+        REST API        +-------------------+
|  Producer Service  | -------------------------> | notification-service | ---------------------> |   Brevo (Email)   |
| (e.g. user-service)|    Exchange: domain.events |                      |                        |                   |
+--------------------+   Routing Key: user.*       +----------------------+                        +-------------------+
```

- **Broker:** RabbitMQ (e.g., CloudAMQP in production, local RabbitMQ for dev).
- **Exchange:** `domain.events` (Type: `topic`, Durable: `true`).
- **Notification Queue:** `notification.events.queue` (already bound by `notification-service` to `domain.events` with pattern `user.#`).

---

## 2. Message Contract

### AMQP Headers & Properties
- **Content-Type:** `application/json`
- **Delivery Mode:** `2` (Persistent)

### JSON Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["eventName", "occurredOn", "payload"],
  "properties": {
    "eventName": {
      "type": "string",
      "enum": ["user.created", "user.updated", "user.deleted"]
    },
    "occurredOn": {
      "type": "string",
      "format": "date-time",
      "description": "ISO-8601 timestamp, e.g. 2026-10-18T21:40:00"
    },
    "payload": {
      "type": "object",
      "required": ["name", "email"],
      "properties": {
        "name": {
          "type": "string",
          "description": "Recipient full or display name"
        },
        "email": {
          "type": "string",
          "format": "email",
          "description": "Valid recipient email address"
        }
      },
      "additionalProperties": true
    }
  }
}
```

---

## 3. Supported Events & Actions

| Event Name (`eventName`) | Recommended Routing Key | Required Payload Fields | Notification Result |
| :--- | :--- | :--- | :--- |
| `user.created` | `user.created` | `name`, `email` | Sends **Welcome Email** with subject: *"Welcome to our platform"* |
| `user.updated` | `user.updated` | `name`, `email` | Sends **Account Updated Email** with subject: *"Your account was updated"* |
| `user.deleted` | `user.deleted` | None (payload optional) | Ignored gracefully (no email sent) |

---

## 4. Concrete Message Payloads

### Example 1: User Created (`user.created`)
```json
{
  "eventName": "user.created",
  "occurredOn": "2026-10-18T21:40:00",
  "payload": {
    "name": "Jane Doe",
    "email": "jane.doe@example.com"
  }
}
```

### Example 2: User Updated (`user.updated`)
```json
{
  "eventName": "user.updated",
  "occurredOn": "2026-10-18T21:45:00",
  "payload": {
    "name": "Jane Doe",
    "email": "jane.doe@example.com"
  }
}
```

---

## 5. Implementation Code Examples

### A. Node.js / TypeScript (`amqplib`)

```typescript
import amqp from 'amqplib';

const RABBITMQ_URL = process.env.RABBITMQ_URL || 'amqp://guest:guest@localhost:5672';
const EXCHANGE_NAME = 'domain.events';

export async function publishUserCreatedEvent(name: string, email: string) {
  const connection = await amqp.connect(RABBITMQ_URL);
  const channel = await connection.createChannel();

  await channel.assertExchange(EXCHANGE_NAME, 'topic', { durable: true });

  const event = {
    eventName: 'user.created',
    occurredOn: new Date().toISOString().replace(/\.\d{3}Z$/, ''),
    payload: { name, email }
  };

  channel.publish(
    EXCHANGE_NAME,
    'user.created',
    Buffer.from(JSON.stringify(event)),
    {
      contentType: 'application/json',
      persistent: true
    }
  );

  await channel.close();
  await connection.close();
}
```

---

### B. Java / Spring Boot (`spring-boot-starter-amqp`)

```java
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserEventPublisher {

  private static final String EXCHANGE = "domain.events";
  private static final String ROUTING_KEY_USER_CREATED = "user.created";

  private final RabbitTemplate rabbitTemplate;

  public UserEventPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void publishUserCreated(String name, String email) {
    Map<String, Object> event = Map.of(
        "eventName", "user.created",
        "occurredOn", LocalDateTime.now().toString(),
        "payload", Map.of(
            "name", name,
            "email", email
        )
    );

    rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY_USER_CREATED, event);
  }
}
```

---

### C. Python (`pika`)

```python
import json
import os
from datetime import datetime
import pika

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672")
EXCHANGE_NAME = "domain.events"

def publish_user_created(name: str, email: str):
    params = pika.URLParameters(RABBITMQ_URL)
    connection = pika.BlockingConnection(params)
    channel = connection.channel()

    channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type="topic", durable=True)

    event = {
        "eventName": "user.created",
        "occurredOn": datetime.now().isoformat(),
        "payload": {
            "name": name,
            "email": email
        }
    }

    channel.basic_publish(
        exchange=EXCHANGE_NAME,
        routing_key="user.created",
        body=json.dumps(event),
        properties=pika.BasicProperties(
            content_type="application/json",
            delivery_mode=2  # persistent
        )
    )

    connection.close()
```

---

## 6. Important Notes for Producers

1. **Transactional Outbox / After Commit:** Always publish events **after** the database transaction commits successfully to prevent phantom emails for rolled-back registrations.
2. **Email Validation:** The `email` field is strictly validated with `^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$`. Ensure client-side or domain validation passes before sending.
3. **Field Names are Case-Sensitive:** Ensure `payload` has exact lowercase keys `"name"` and `"email"`.
