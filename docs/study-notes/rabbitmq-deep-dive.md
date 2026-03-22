# RabbitMQ — Deep Dive

---

## What is a Message Broker?

A message broker is a middleman that receives messages from one service (producer)
and delivers them to another service (consumer). Neither side talks to each other
directly — they only talk to the broker.

```
Without broker:                        With broker:
───────────────                        ────────────
ServiceA ──► ServiceB  (tight         ServiceA ──► Broker ──► ServiceB
             coupling,                                    ──► ServiceC
             what if B is down?)                          ──► ServiceD
```

### Why this matters

**Without broker:**
- Service A must know Service B's address
- If Service B is down, the message is lost
- Service A is blocked waiting for B to respond
- Adding a new consumer means changing Service A's code

**With broker:**
- Service A only knows the broker's address
- If Service B is down, broker holds the message until B comes back
- Service A fires and moves on — no waiting
- Add new consumers without touching Service A

---

## What is RabbitMQ?

RabbitMQ is an open source message broker built on the AMQP protocol.
It was built by Rabbit Technologies in 2007, now maintained by VMware/Broadcom.

It is used by companies like:
- Instagram (notification delivery)
- Reddit (job queues)
- NASA (telemetry data routing)

---

## RabbitMQ Architecture

### The 5 core concepts

```
Producer  ──►  Exchange  ──►  Queue  ──►  Consumer
                   │
              (routing rules
               called bindings)
```

---

### 1. Producer

Any application that sends a message to RabbitMQ.
In RideOrbit: `BookingNotificationService` calling `messagingTemplate.convertAndSend()`

---

### 2. Exchange

The entry point into RabbitMQ. Receives messages from producers and decides
which queue to put them in based on routing rules.

Think of it as a post office sorting room — it reads the address and puts the
letter in the right bag.

There are 4 types of exchange:

**Direct Exchange**
Routes message to a queue whose binding key exactly matches the routing key.
```
Message routing key: "booking.confirmed"
Queue A binding key: "booking.confirmed"  ← gets the message
Queue B binding key: "booking.cancelled" ← does NOT get the message
```

**Topic Exchange** ← what STOMP /topic uses
Routes based on pattern matching with wildcards.
```
* = one word
# = zero or more words

Message routing key: "booking.5.status"
Queue binding: "booking.*.status"  ← matches  ✓
Queue binding: "booking.#"         ← matches  ✓
Queue binding: "driver.*.status"   ← no match ✗
```

**Fanout Exchange**
Ignores routing key completely. Sends message to ALL bound queues.
```
Message arrives
    ↓
Exchange sends to Queue A, Queue B, Queue C simultaneously
Routing key is irrelevant
```

**Headers Exchange**
Routes based on message header attributes instead of routing key.
Rarely used in practice.

---

### 3. Queue

A buffer that holds messages until a consumer picks them up.
Messages sit in order (FIFO — first in, first out).

```
Producer sends 3 messages:
Queue: [msg1] [msg2] [msg3]

Consumer reads:
msg1 delivered → Queue: [msg2] [msg3]
msg2 delivered → Queue: [msg3]
msg3 delivered → Queue: []
```

**Queue properties:**

| Property | What it does |
|---|---|
| Durable | Survives RabbitMQ restart (stored to disk) |
| Exclusive | Only one consumer, deleted when consumer disconnects |
| Auto-delete | Deleted when last consumer disconnects |
| TTL | Message expires after X milliseconds if not consumed |

---

### 4. Binding

A rule that connects an exchange to a queue.
It tells the exchange: "messages matching this pattern go to this queue."

```
Exchange ──[binding: "booking.*"]──► Queue A
Exchange ──[binding: "driver.*"]───► Queue B
Exchange ──[binding: "#"]──────────► Queue C  (gets everything)
```

---

### 5. Consumer

Any application that reads messages from a queue.
In RideOrbit: the browser (WebSocket client) subscribed to `/topic/booking.5`

---

## Full architecture diagram

```
                    RabbitMQ
┌───────────────────────────────────────────────────────┐
│                                                       │
│   Producer                                            │
│   (Spring App)                                        │
│       │                                               │
│       │ PUBLISH to exchange                           │
│       ▼                                               │
│   ┌──────────┐  binding: booking.*  ┌──────────────┐  │
│   │ Exchange │─────────────────────►│   Queue A    │  │
│   │ (topic)  │                      │ [msg][msg]   │  │
│   │          │  binding: driver.*   └──────┬───────┘  │
│   │          │─────────────────────►       │          │
│   │          │        ┌──────────────┐     │          │
│   │          │        │   Queue B    │     │          │
│   └──────────┘        │ [msg]        │     │          │
│                       └──────┬───────┘     │          │
│                              │             │          │
└──────────────────────────────┼─────────────┼──────────┘
                               │             │
                               ▼             ▼
                          Consumer B    Consumer A
                          (Browser)     (Browser)
```

---

## How RabbitMQ works with Spring WebSocket (STOMP)

When Spring connects to RabbitMQ via `enableStompBrokerRelay`:

**Step 1 — App starts**
```
Spring opens system TCP connection to RabbitMQ :61613
This connection is used to SEND messages
```

**Step 2 — Browser connects**
```
Browser → ws://localhost:8080/ws
Spring upgrades to WebSocket + STOMP
Spring opens a client TCP connection to RabbitMQ on behalf of this browser
```

**Step 3 — Browser subscribes**
```
Browser sends: SUBSCRIBE /topic/booking.5

Spring relay forwards this to RabbitMQ via client connection
RabbitMQ creates a queue for this browser and binds it to /topic/booking.5
```

**Step 4 — Message published**
```
Spring sends: SEND /topic/booking.5 {event}

RabbitMQ looks up all queues bound to /topic/booking.5
Delivers to each consumer (browser) across all instances
```

**Step 5 — Browser disconnects**
```
RabbitMQ removes the queue for this browser
Subscription is cleaned up automatically
```

---

## Message acknowledgement

By default RabbitMQ uses auto-acknowledgement — it assumes the message was
delivered successfully the moment it sends it.

With manual acknowledgement:
```
RabbitMQ delivers message to Consumer
Consumer processes it
Consumer sends ACK back to RabbitMQ  ← "I got it"

If consumer crashes before ACK:
    RabbitMQ re-queues the message
    Delivers to another consumer
    Message is never lost
```

---

## Message persistence

By default messages live in memory — lost if RabbitMQ restarts.

With persistence:
```
Producer marks message as persistent
RabbitMQ writes it to disk
RabbitMQ restarts → message is still in the queue
Consumer eventually receives it
```

Both the queue AND the message must be marked durable/persistent for this to work.

---

## Dead Letter Queue (DLQ)

When a message cannot be delivered (consumer rejects it, TTL expires, queue full),
it goes to a Dead Letter Queue instead of being silently dropped.

```
Normal Queue
    │
    │ message rejected / expired / queue full
    ▼
Dead Letter Exchange
    │
    ▼
Dead Letter Queue  ← you can inspect failed messages here
```

Useful for debugging — you can see exactly what messages failed and why.

---

## RabbitMQ vs Kafka — when to use what

| | RabbitMQ | Kafka |
|---|---|---|
| Best for | Task queues, notifications, RPC | Event streaming, analytics, logs |
| Message retention | Deleted after consumed | Stored for configurable time |
| Replay messages | No | Yes |
| Throughput | High (tens of thousands/sec) | Very high (millions/sec) |
| STOMP support | Yes — native | No |
| Complexity | Medium | High |
| Use in RideOrbit | Perfect fit | Overkill |

Simple rule:
```
Need to deliver a task/notification to a service?  → RabbitMQ
Need to store and replay a stream of events?        → Kafka
```

---

## Interview Questions

### Basic

**Q: What is a message broker?**
A middleman that receives messages from producers and delivers them to consumers.
Decouples services so they don't talk to each other directly.

**Q: What is the difference between a queue and a topic?**
Queue — one message, one consumer. Message is deleted after consumed.
Topic — one message, many consumers. Each subscriber gets a copy.
RabbitMQ uses exchanges + bindings to implement both patterns.

**Q: What is the difference between AMQP and STOMP?**
AMQP is RabbitMQ's main protocol — feature-rich, binary, used for service-to-service.
STOMP is a simpler text-based protocol — used for WebSocket/browser connections.
RabbitMQ supports both via plugins.

**Q: What is an exchange in RabbitMQ?**
The entry point that receives messages from producers and routes them to queues
based on binding rules. Four types: direct, topic, fanout, headers.

**Q: What is a binding?**
A rule connecting an exchange to a queue. Tells the exchange which messages
should go to which queue based on routing key patterns.

---

### Intermediate

**Q: What happens if a consumer is down when a message is published?**
If the queue is durable and the message is persistent, RabbitMQ holds the message
on disk. When the consumer comes back online it receives the message.
If the queue is not durable, the message is lost.

**Q: What is message acknowledgement and why does it matter?**
After delivering a message, RabbitMQ waits for an ACK from the consumer.
If the consumer crashes before ACKing, RabbitMQ re-queues the message and
delivers it to another consumer. Without acknowledgement, messages can be lost
if the consumer crashes mid-processing.

**Q: What is a Dead Letter Queue?**
A queue where messages end up when they cannot be delivered — rejected by consumer,
TTL expired, or queue is full. Used for debugging and handling failed messages
instead of silently dropping them.

**Q: What is the difference between direct, topic, and fanout exchanges?**
Direct — exact routing key match.
Topic — wildcard pattern matching (* for one word, # for many).
Fanout — ignores routing key, sends to all bound queues.

**Q: How does RabbitMQ handle multiple consumers on the same queue?**
Round-robin by default — messages distributed evenly across all consumers.
If Consumer A is busy, the next message goes to Consumer B.
This is how you scale workers — add more consumers to the same queue.

---

### Advanced

**Q: What is the difference between RabbitMQ and Kafka?**
RabbitMQ is a message broker — delivers messages and deletes them after consumption.
Kafka is an event log — stores messages on disk for a configurable retention period,
consumers can replay from any point.
Use RabbitMQ for task queues and notifications. Use Kafka for event streaming and analytics.

**Q: How would you prevent message loss in RabbitMQ?**
Three things must be in place:
1. Queue must be durable (survives broker restart)
2. Message must be marked persistent (written to disk)
3. Consumer must use manual acknowledgement (re-queued on crash)

**Q: What is a fanout exchange and when would you use it?**
Sends every message to all bound queues regardless of routing key.
Use it when the same event needs to go to multiple independent services —
e.g. a payment event that needs to trigger email service, analytics service,
and inventory service simultaneously.

**Q: How does the STOMP broker relay work in Spring?**
Spring opens two TCP connections to RabbitMQ's STOMP port (61613):
System connection — used to publish messages.
Client connections — one per connected browser, forwards STOMP frames to RabbitMQ.
When a browser subscribes, RabbitMQ creates a queue and binds it.
When a message is published, RabbitMQ delivers to all matching queues across all instances.

**Q: What is the difference between a durable queue and a persistent message?**
Durable queue — the queue definition survives a broker restart. But messages
in the queue are still lost unless the messages themselves are also persistent.
Persistent message — the message is written to disk. Survives broker restart.
You need BOTH for full message durability.
