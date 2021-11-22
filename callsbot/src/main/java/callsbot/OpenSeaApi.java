package callsbot;

import java.util.HashMap;

import org.json.JSONObject;

public class OpenSeaApi {
	
	private final static String OPENSEA_COLLECTION_API = "https://api.opensea.io/collection/";
	private final static String OPENSEA_COLLECTION_WEB = "https://opensea.io/collection/";	
	private final static HashMap<String, String> REQUEST_PROPERTIES = new HashMap<String, String>();
	static {
		REQUEST_PROPERTIES.put("Accept", "application/json");
	}

	public static void main(String[] args) {
		System.out.println(getCollectionStats("mekaverse"));
	}
	
	public static String getCollectionStats(String slug) {
		try {
			JSONObject json = Utils.getJSONHTTP(OPENSEA_COLLECTION_API + slug, REQUEST_PROPERTIES);
			if(json != null & !json.isNull("collection")) {
				json = json.getJSONObject("collection");
				String name = json.getString("name");
				JSONObject stats = json.getJSONObject("stats");
				if(name != null && stats != null) {
					StringBuilder sb = new StringBuilder("*Opensea NFT collection info* [");
					sb.append(name);
					sb.append("](");
					sb.append(OPENSEA_COLLECTION_WEB + slug);
					sb.append(")");
					sb.append(" (slug ");
					sb.append(slug);
					sb.append(")\n*Floor* ");
					sb.append(stats.get("floor_price"));
					sb.append(", *Marketcap* ");
					sb.append(stats.get("market_cap"));
					sb.append(", *Owners* ");
					sb.append(stats.get("num_owners"));
					sb.append("\n*Volume*(1d, 7d, 30d) ");
					sb.append(stats.get("one_day_volume"));
					sb.append(", ");
					sb.append(stats.get("seven_day_volume"));
					sb.append(", ");
					sb.append(stats.get("thirty_day_volume"));
					sb.append("\n*Average price*(1d, 7d, 30d) ");
					sb.append(stats.get("one_day_average_price"));
					sb.append(", ");
					sb.append(stats.get("seven_day_average_price"));
					sb.append(", ");
					sb.append(stats.get("thirty_day_average_price"));					
					sb.append("\n*Change*(1d, 7d, 30d) ");
					sb.append(stats.get("one_day_change"));
					sb.append(", ");
					sb.append(stats.get("seven_day_change"));
					sb.append(", ");
					sb.append(stats.get("thirty_day_change"));			
					sb.append("\n*Sales*(1d, 7d, 30d) ");
					sb.append(stats.get("one_day_sales"));
					sb.append(", ");
					sb.append(stats.get("seven_day_sales"));
					sb.append(", ");
					sb.append(stats.get("thirty_day_sales"));					
					return sb.toString();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return null;
	}

}