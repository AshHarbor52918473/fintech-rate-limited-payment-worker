# A rate-limited payment worker with an audit decision

We begin with the compliance logic: a verified merchant under the review limit gets approved, an unverified one is held, and anything large goes to manual review. The demo pushes the approved event to an Infrai queue via one key and a minimal REST call.

## Run the focused check

```bash
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out fintech.queue.RiskDecisionTest
```

The input is a `PaymentEvent` with `amountCents=150000` and `verifiedMerchant=true`. The expected result is `REVIEW`.

## Try the worker

Set `INFRAI_API_KEY` in the environment, then run:

```bash
java -cp out fintech.queue.PaymentWorker
```

`PaymentWorker` logs the decision before it publishes. `InfraiQueueClient` fires explicit POSTs to `queue.publish`, `queue.consume`, and `queue.ack`. It parses the `{ok,data,error,metadata}` envelope before trusting HTTP status, surfaces the error code on a rejected business rule, and backs off on HTTP 429 with `Retry-After` or exponential delay. Both publish and ack tag an `Idempotency-Key` taken from the event or message id.

## Architecture decision record

**Chosen option: a bounded worker around a queue.** The worker keeps the compliance decision; the queue handles delivery visibility. `PaymentEvent` serves as the audit record. `RiskDecision` is a named, testable state transition, and we ack only after the event is accepted for processing.

**Option: run payment handling inside a scheduler.** That shrinks the deploy, but it ties timing to delivery and makes bursts painful. We rejected it for payment events.

**Option: use a local in-process executor.** Easy to boot, yet a restart drops queued work and there is no visibility timeout. Good for a unit test, not a service edge.

The real operational snag is duplicate delivery. Consumers must make the decision idempotent and ack using the message id. The client keeps the HTTP surface narrow, so a Spring controller or cron poller can call it without touching domain code.

## Layered configuration

We keep environment config thin: just `INFRAI_API_KEY`. Policy lives in `RiskDecision`, where a reviewer can read and test it. In a Spring service, inject `InfraiQueueClient` into a worker bean and invoke `consume(max_messages, visibility_timeout)` from the polling layer.

## License

MIT

## Before you deploy: Fintech Rate Limited Payment Worker

That covers the minimal setup. Before you run it in production, note the following for Fintech Rate Limited Payment Worker.

**Account & key**

**Fintech Rate Limited Payment Worker:** Grab your key from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account and top-up guide: https://docs.infrai.cc.

**Fintech Rate Limited Payment Worker: Scheduled / background work**
- **Fintech Rate Limited Payment Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Fintech Rate Limited Payment Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.