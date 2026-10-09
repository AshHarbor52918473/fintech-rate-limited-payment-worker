package fintech.queue;

public record PaymentEvent(String eventId, String accountId, int amountCents, String currency, boolean verifiedMerchant) {
    public String toPayload() {
        return "{\"event_id\":\"" + eventId + "\",\"account_id\":\"" + accountId
                + "\",\"amount_cents\":" + amountCents + ",\"currency\":\"" + currency
                + "\",\"verified_merchant\":" + verifiedMerchant + "}";
    }
}
