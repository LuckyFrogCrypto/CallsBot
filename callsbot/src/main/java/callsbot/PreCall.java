package callsbot;

public class PreCall {

	public final String uuid;
	public final String coinId;
	public final double openPrice;
	public final long resolveTime;
	
	PreCall(String uuid, String coinId, double openPrice, long resolveTime){
		this.uuid = uuid;
		this.coinId = coinId;
		this.openPrice = openPrice;
		this.resolveTime = resolveTime;
	}
	
	public Call toCall(long userId, long chatId, boolean tracked) {
		return new Call(userId, chatId, coinId, openPrice, resolveTime, tracked);
	}
	
}
