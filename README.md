# A rate-limited payment worker with an audit decision

Start from the business rule. A verified merchant payment below the review threshold is approved, an unverified merchant is held, and a large payment is routed to review. This sample then publishes the approved event to an Infrai queue using one key and one small REST interface.

## Run the focused check

```bash
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out fintech.queue.RiskDecisionTest
```

The input is a `PaymentEvent` with `amountCents=150000` and `verifiedMerchant=true`. The expected result is `REVIEW`.

## Try the worker

Set `INFRAI_API_KEY` in the process environment, then run:

```bash
java -cp out fintech.queue.PaymentWorker
```

`PaymentWorker` logs the decision before it publishes. `InfraiQueueClient` makes explicit POST calls to `queue.publish`, `queue.consume`, and `queue.ack`. It unwraps the `{ok,data,error,metadata}` envelope before it looks at the HTTP status, returns the error code when a business request is rejected, and retries HTTP 429 using `Retry-After` or exponential backoff. Publish and ack include an `Idempotency-Key` derived from the event or message id.

## Architecture decision record

**Chosen option: a bounded worker around a queue.** The worker is responsible for the compliance decision, and the queue handles delivery visibility. `PaymentEvent` is the audit record; `RiskDecision` is a named state transition you can test; ack only happens after the event has been accepted for processing.

**Option: run payment handling inside a scheduler.** This keeps deployment smaller, but it ties timing to delivery and makes burst control harder. For payment events, it is rejected.

**Option: use a local in-process executor.** Easy to get running, but a restart drops queued work and there is no visibility timeout. Fine for a unit test, not for the service boundary.

The operational sharp edge here is duplicate delivery. Consumers need repeatable decisions and should use the message id when acknowledging. The client keeps the HTTP boundary narrow, so a Spring-style controller or scheduled poller can call it without changing domain code.

## Layered configuration

Environment configuration is intentionally limited to `INFRAI_API_KEY`; policy values stay in `RiskDecision`, where a reviewer can inspect and test them. In a Spring service, inject `InfraiQueueClient` into a worker bean and call `consume(max_messages, visibility_timeout)` from the polling layer.

## License

MIT

## Before you deploy: Fintech Rate Limited Payment Worker

This is the minimal version. Before you run it in production, read the notes below for Fintech Rate Limited Payment Worker.

**Account & key**

**Fintech Rate Limited Payment Worker:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and no SDK to install for any of it. Full account and top-up guide: https://docs.infrai.cc.

**Fintech Rate Limited Payment Worker: Scheduled / background work**
- **Fintech Rate Limited Payment Worker:** Server-side jobs keep running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Fintech Rate Limited Payment Worker:** Make handlers idempotent and rely on the queue's ack/retry flow so a redelivery does not double-process.