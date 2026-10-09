package fintech.queue;

public final class PaymentWorker {
    private final InfraiQueueClient queue;

    public PaymentWorker(InfraiQueueClient queue) { this.queue = queue; }

    public RiskDecision decide(PaymentEvent event) { return RiskDecision.evaluate(event); }

    public void publish(PaymentEvent event) throws Exception {
        RiskDecision decision = decide(event);
        if ("HOLD".equals(decision.action())) throw new IllegalArgumentException("payment held: " + decision.reason());
        queue.publish(event);
    }

    public static void main(String[] args) throws Exception {
        PaymentWorker worker = new PaymentWorker(new InfraiQueueClient());
        PaymentEvent event = new PaymentEvent("evt-demo-001", "acct-42", 4200, "USD", true);
        System.out.println(worker.decide(event));
        worker.publish(event);
    }
}
