package callsbot;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.HashMap;

import org.json.JSONObject;

public class OpenSeaApiGrabber {
	
	private final static String OPENSEA_COLLECTION_API = "https://api.opensea.io/collection/";
	private final static String OPENSEA_COLLECTION_WEB = "https://opensea.io/collection/";
	private final static String NA = "n/a";
	private final static HashMap<String, String> REQUEST_PROPERTIES = new HashMap<String, String>();
	private final static DecimalFormat DF = new DecimalFormat("#.##");
	static {
		REQUEST_PROPERTIES.put("Accept", "application/json");
		DF.setRoundingMode(RoundingMode.CEILING);
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
					Double floor = stats.getDouble("floor_price");
					String floorString = floor == null ? NA : DF.format(floor);
					Double mc = stats.getDouble("market_cap");
					String mcString = mc == null ? NA : DF.format(mc);
					Integer owners = stats.getInt("num_owners");
					String ownersString = owners == null ? NA : owners + "";
					Double vol1d = stats.getDouble("one_day_volume");
					String vol1dString = vol1d == null ? NA : DF.format(vol1d);
					Double vol7d = stats.getDouble("seven_day_volume");
					String vol7dString = vol7d == null ? NA : DF.format(vol7d);
					Double vol30d = stats.getDouble("thirty_day_volume");
					String vol30dString = vol30d == null ? NA : DF.format(vol30d);
					Double avg1d = stats.getDouble("one_day_average_price");
					String avg1dString = avg1d == null ? NA : DF.format(avg1d);
					Double avg7d = stats.getDouble("seven_day_average_price");
					String avg7dString = avg7d == null ? NA : DF.format(avg7d);
					Double avg30d = stats.getDouble("thirty_day_average_price");
					String avg30dString = avg30d == null ? NA : DF.format(avg30d);
					Double change1d = stats.getDouble("one_day_change");
					String change1dString = change1d == null ? NA : DF.format(change1d);
					Double change7d = stats.getDouble("seven_day_change");
					String change7dString = change7d == null ? NA : DF.format(change7d);
					Double change30d = stats.getDouble("thirty_day_change");
					String change30dString = change30d == null ? NA : DF.format(change30d);
					Integer sales1d = stats.getInt("one_day_sales");
					String sales1dString = sales1d == null ? NA : sales1d + "";
					Integer sales7d = stats.getInt("seven_day_sales");
					String sales7dString = sales7d == null ? NA : sales7d + "";
					Integer sales30d = stats.getInt("thirty_day_sales");
					String sales30dString = sales30d == null ? NA : sales30d + "";					
					StringBuilder sb = new StringBuilder("*Opensea NFT collection info* [");
					sb.append(name);
					sb.append("](");
					sb.append(OPENSEA_COLLECTION_WEB + slug);
					sb.append(")");
					sb.append(" (slug ");
					sb.append(slug);
					sb.append(")\n*Floor* ");
					sb.append(floorString);
					sb.append(", *Marketcap* ");
					sb.append(mcString);
					sb.append(", *Owners* ");
					sb.append(ownersString);
					sb.append("\n*Volume(1d, 7d, 30d)* ");
					sb.append(vol1dString);
					sb.append(", ");
					sb.append(vol7dString);
					sb.append(", ");
					sb.append(vol30dString);
					sb.append("\n*Average price(1d, 7d, 30d)* ");
					sb.append(avg1dString);
					sb.append(", ");
					sb.append(avg7dString);
					sb.append(", ");
					sb.append(avg30dString);					
					sb.append("\n*Change(1d, 7d, 30d)* ");
					sb.append(change1dString);
					sb.append(", ");
					sb.append(change7dString);
					sb.append(", ");
					sb.append(change30dString);
					sb.append("\n*Sales(1d, 7d, 30d)* ");
					sb.append(sales1dString);
					sb.append(", ");
					sb.append(sales7dString);
					sb.append(", ");
					sb.append(sales30dString);					
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