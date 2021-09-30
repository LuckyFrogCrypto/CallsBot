package callsbot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

public class CoingeckoApiGrabber {

	private final static String COIN_DATA_URI = "https://api.coingecko.com/api/v3/coins/";
	private final static String COIN_LIST_URI = "https://api.coingecko.com/api/v3/coins/list";
	private static final String COIN_HISTORICAL_URI_FRONT = "https://api.coingecko.com/api/v3/coins/";
	private static final String COIN_HISTORICAL_URI_BACK = "/history?date=";
	private static final String PLATFORM_NAME = "coingecko";
	public static final List<Coin> COINS_LIST = new ArrayList<Coin>();
	public static final Map<String, Coin> PLATFORM_ID_TO_COINS_MAP = new HashMap<String, Coin>();
	public static final Map<String, Coin> ID_TO_COINS_MAP = new HashMap<String, Coin>();
	public static final Map<String, Coin> NAME_TO_COINS_MAP = new HashMap<String, Coin>();
	public static final Map<String, Set<Coin>> TICKER_TO_COINS_MAP = new HashMap<String, Set<Coin>>();
	
	//Rate Limit: 100 requests/minute
//	private static final long WAIT_TIME_REQUESTS_MS = 1900;
//	private static final long WAIT_TIME_FULL_CYCLE = 10000000; //ms
	
	private final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");

	public static void main(String[] args) {
//		CoingeckoApiGrabber grabber = new CoingeckoApiGrabber();
//		grabber.updateCoins();
//		grabber.getPrice(grabber.findCoins("bonk").iterator().next());
//		List<Coin> coins = grabber.getCoinsList();
//		System.out.println(coins.size());
//		System.out.println(grabber.getPriceAtTime(coins.get(314), System.currentTimeMillis()));
//		System.out.println(grabber.getPriceAndMarketcap(coins.get(100)));
	}
	
	public Double getPrice(Coin coin) {
		return getPriceAtTime(coin, System.currentTimeMillis());
	}

	public Double getPriceAtTime(Coin coin, long unixTime) {
		Date date = new Date(unixTime);
		String dateString = sdf.format(date);		
		try {
			String id = coin.platformIdMap.get(PLATFORM_NAME);
			if(id == null) {
				return null;
			}
			JSONObject json = Utils.getJSONHTTP(COIN_HISTORICAL_URI_FRONT + id + COIN_HISTORICAL_URI_BACK + dateString);
			if(json == null) {
				return null;
			}
			if(!json.isNull("market_data")) {
				json = json.getJSONObject("market_data");
				if(!json.isNull("current_price")) {
					json = json.getJSONObject("current_price");
					return json.getDouble("usd");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public Set<Coin> findCoins(String identifier) {
		identifier = identifier.toLowerCase();
		if(TICKER_TO_COINS_MAP.containsKey(identifier)){
			Set<Coin> result =  TICKER_TO_COINS_MAP.get(identifier);
			if(result != null) {
				return result;
			}
		}
		Set<Coin> result = new HashSet<Coin>();
		Coin coin = PLATFORM_ID_TO_COINS_MAP.get(identifier);
		if(coin != null) {
			result.add(coin);
			return result;
		}
		coin = NAME_TO_COINS_MAP.get(identifier);
		if(coin != null) {
			result.add(coin);
			return result;
		}
		return result;
	}

	public boolean updateCoins() {
		try {
			JSONObject json = Utils.getJSONHTTP(COIN_LIST_URI, true);
			for(Object obj : json.getJSONArray("data")) {
				if(obj instanceof JSONObject) {
					JSONObject jObj = (JSONObject) obj;
					String id =  jObj.getString("id");
					if(PLATFORM_ID_TO_COINS_MAP.containsKey(id)){
						continue;
					}
					String name =  jObj.getString("name");
					String ticker =  jObj.getString("symbol");
					Coin coin = new Coin(name, ticker, PLATFORM_NAME, id);
					COINS_LIST.add(coin);
					PLATFORM_ID_TO_COINS_MAP.put(id, coin);
					ID_TO_COINS_MAP.put(coin.id, coin);
					NAME_TO_COINS_MAP.put(name, coin);
					Set<Coin> coinsWithTicker = TICKER_TO_COINS_MAP.get(ticker);
					if(coinsWithTicker == null) {
						coinsWithTicker = new HashSet<Coin>();
						coinsWithTicker.add(coin);
						TICKER_TO_COINS_MAP.put(ticker, coinsWithTicker);
					} else {
						coinsWithTicker.add(coin);
					}
				}
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	//returns array with [price, marketcap, volume]
	public Double[] getPriceMarketcapVolume(Coin coin) {
		try {
			String platformId = coin.platformIdMap.get(PLATFORM_NAME);
			if(platformId == null) {
				return null;
			}
			JSONObject json = Utils.getJSONHTTP(COIN_DATA_URI + platformId);
			if(json != null && json.has("market_data")) {
				Double price = null;
				Double marketcap = null;
				Double volume = null;
				json = json.getJSONObject("market_data");
				if(json != null) {
					if(json.has("current_price")) {
						JSONObject curPr = json.getJSONObject("current_price");
						if(curPr.has("usd")) {
							price = curPr.getDouble("usd");
						}
					}
					if(json.has("market_cap")) {
						JSONObject marCp = json.getJSONObject("market_cap");
						if(marCp.has("usd")) {
							marketcap = marCp.getDouble("usd");
						}
					}
					if(json.has("total_volume")) {
						JSONObject vol = json.getJSONObject("total_volume");
						if(vol.has("usd")) {
							volume = vol.getDouble("usd");
						}
					}
					if(price != null && marketcap != null && volume != null) {
						return new Double[]{price, marketcap, volume};
					}
				}
			}			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
