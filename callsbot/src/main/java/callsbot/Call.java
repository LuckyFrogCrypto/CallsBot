package callsbot;

import java.io.Serializable;

public class Call implements Serializable {

	private static final long serialVersionUID = 100L;
	public final long userId;
	public final long chatId;
	public final String coinId;
	public final double openPrice;
	public final long createTime; //unix ms time
	public final long resolveTime; //unix ms time
	public final boolean tracked;
	public final long runTime;
	
	public Call(long userId, long chatId, String coinId, double openPrice, long resolveTime, boolean tracked){
		this(userId, chatId, coinId, openPrice, resolveTime, tracked, System.currentTimeMillis());
	}
	
	public Call(long userId, long chatId, String coinId, double openPrice, long resolveTime, boolean tracked, long createTime){
		this.userId = userId;
		this.chatId = chatId;
		this.coinId = coinId;
		this.openPrice = openPrice;
		this.resolveTime = resolveTime;
		this.tracked = tracked;
		this.createTime = createTime;
		this.runTime = resolveTime - createTime; 
	}
	
	public ResolvedCall resolve(double closePrice) {
		return new ResolvedCall(this, closePrice);
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder("Call by ");
		sb.append(userId);
		sb.append(" for ");
		sb.append(coinId);
		return sb.toString();
	}
	
}