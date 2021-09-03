package callsbot;

public class ResolvedCall extends Call {
	
	private static final long serialVersionUID = 200L;
	final double closePrice;
	final double priceChange;
	final double priceChangePercentage;
	final boolean inProfit;

	public ResolvedCall(Call call, double closePrice) {
		super(call.userId, call.chatId, call.coinId, call.openPrice, call.resolveTime, call.tracked, call.createTime);
		this.closePrice = closePrice;
		if(tracked) {
			priceChange = closePrice - openPrice;
			priceChangePercentage = (priceChange/openPrice) * 100;
			inProfit = Double.compare(priceChange, 0) > 0;
		} else {
			priceChange = 0;
			priceChangePercentage = 0;
			inProfit = false;
		}
	}
	
}

