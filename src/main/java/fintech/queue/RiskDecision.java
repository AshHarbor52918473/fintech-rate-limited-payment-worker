package fintech.queue;

public record RiskDecision(String action, String reason) {
    public static RiskDecision evaluate(PaymentEvent event) {
        if (!event.verifiedMerchant()) return new RiskDecision("HOLD", "merchant_not_verified");
        if (event.amountCents() > 100_000) return new RiskDecision("REVIEW", "amount_threshold");
        return new RiskDecision("APPROVE", "within_policy");
    }
}
