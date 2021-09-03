package callsbot;

import java.util.HashMap;
import java.util.Map;

public class Coin implements Comparable<Coin> {

	public final Map<String, String> platformIdMap = new HashMap<String, String>();
	public final String name;
	public final String ticker;
	public final String id;
	
	public Coin(String name, String ticker) {
		this.name = name.toLowerCase();
		this.ticker = ticker.toUpperCase();
		name = name.replaceAll("\\s+","-");
		name = name.replaceAll("_","-");
		id = name + ticker;
	}
	
	public Coin(String name, String ticker, String platform, String platformId) {
		this(name, ticker);
		platformIdMap.put(platform, platformId);
	}

	public int compareTo(Coin otherCoin) {
		return (this.id).compareTo(otherCoin.id);
	}
	
	@Override
	public String toString() {
		return ticker.toUpperCase() + " (" + name + ")";
	}
	
	public String toStringWithPriceData() {
		return ticker + " " + name;
	
	}
	
}
