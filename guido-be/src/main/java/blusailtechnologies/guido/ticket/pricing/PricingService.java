package blusailtechnologies.guido.ticket.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class PricingService {

	public record Pricing(BigDecimal inputPerMtok, BigDecimal outputPerMtok,
	                      BigDecimal cacheWritePerMtok, BigDecimal cacheReadPerMtok) {}

	private static final BigDecimal MILLION = new BigDecimal("1000000");

	private static final Pricing DEFAULT_PRICING = new Pricing(
			new BigDecimal("3.00"), new BigDecimal("15.00"),
			new BigDecimal("3.75"), new BigDecimal("0.30"));

	private static final Map<String, Pricing> CATALOG = Map.of(
			"claude-opus-4-7",   new Pricing(new BigDecimal("15.00"), new BigDecimal("75.00"),
					new BigDecimal("18.75"), new BigDecimal("1.50")),
			"claude-opus-4-6",   new Pricing(new BigDecimal("5.00"),  new BigDecimal("25.00"),
					new BigDecimal("6.25"),  new BigDecimal("0.50")),
			"claude-sonnet-4-6", new Pricing(new BigDecimal("3.00"),  new BigDecimal("15.00"),
					new BigDecimal("3.75"),  new BigDecimal("0.30")),
			"claude-haiku-4-5",  new Pricing(new BigDecimal("1.00"),  new BigDecimal("5.00"),
					new BigDecimal("1.25"),  new BigDecimal("0.10"))
	);

	public Pricing pricingFor(String model) {
		if (model == null) {
			return DEFAULT_PRICING;
		}
		Pricing exact = CATALOG.get(model);
		if (exact != null) {
			return exact;
		}
		String normalized = model.toLowerCase();
		for (Map.Entry<String, Pricing> e : CATALOG.entrySet()) {
			if (normalized.startsWith(e.getKey())) {
				return e.getValue();
			}
		}
		return DEFAULT_PRICING;
	}

	public BigDecimal cost(String model, int inputTokens, int outputTokens,
	                      int cacheCreationTokens, int cacheReadTokens) {
		Pricing p = pricingFor(model);
		BigDecimal cost = BigDecimal.ZERO;
		cost = cost.add(perMtok(inputTokens, p.inputPerMtok()));
		cost = cost.add(perMtok(outputTokens, p.outputPerMtok()));
		cost = cost.add(perMtok(cacheCreationTokens, p.cacheWritePerMtok()));
		cost = cost.add(perMtok(cacheReadTokens, p.cacheReadPerMtok()));
		return cost.setScale(6, RoundingMode.HALF_UP);
	}

	private static BigDecimal perMtok(int tokens, BigDecimal rate) {
		if (tokens <= 0) return BigDecimal.ZERO;
		return new BigDecimal(tokens).multiply(rate).divide(MILLION, 8, RoundingMode.HALF_UP);
	}
}
