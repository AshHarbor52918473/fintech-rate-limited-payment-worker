package fintech.queue;

public final class RiskDecisionTest {
    public static void main(String[] args) {
        RiskDecision decision = RiskDecision.evaluate(new PaymentEvent("evt-1", "acct-1", 150_000, "USD", true));
        if (!"REVIEW".equals(decision.action())) throw new AssertionError(decision);
        System.out.println("risk threshold test passed");
    }
}
