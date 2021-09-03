package callsbot;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class Utils {
		
	private static final Integer STANDARD_CONNECTION_TIMEOUT = 5000;
	private static final Integer STANDARD_READ_TIMEOUT = 3000;
	private static final int TIMEOUT_FILE_TRANSFER = 6000;
//	private static final int MS_IN_YEAR = 6000;
//	private static final int MS_IN_MONTH = 6000;
//	private static final int MS_IN_HOUR = 6000;
	public static final long MS_IN_DAY = 86400000l;

	public static boolean isDouble(String str) {
	    try {
	        Double.parseDouble(str);
	        return true;
	    } catch (NumberFormatException e) {
	        return false;
	    }
	}
	
	public static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
	    int cp;
	    while ((cp = rd.read()) != -1) {
	      sb.append((char) cp);
	    }
	    rd.close();
	    return sb.toString();
	}
	
	public static void sleep(long ms){
		sleep(ms, null);
	}
	
	/**
	 * @param log can be null, in that case errors will be printed to standard out
	 */
	public static void sleep(long ms, Logger logger){
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			if (logger != null){
				logger.log(Level.WARNING, e.getStackTrace().toString());
			} else {
				e.printStackTrace();				
			}
		}
	}

	public static JSONObject getJSONHTTP(String urlString, Integer connectionTimeout, Integer readTimeout) throws Exception {
		return getJSONHTTP(urlString, false, false, null, connectionTimeout, readTimeout);
	}
	
	public static JSONObject getJSONHTTP(String urlString) throws Exception {
		return getJSONHTTP(urlString, false, false, null, null, null);
	}
	
	public static JSONObject getJSONHTTP(String urlString, boolean readJSONArray) throws Exception {
		return getJSONHTTP(urlString, readJSONArray, false, null, null, null);
	}
	
	public static JSONObject getJSONHTTP(String urlString, boolean readJSONArray, boolean useGZIP,
			Map<String, String> extraProperties, Integer connectionTimeout, Integer readTimeout) throws Exception {
		//long initTime = System.currentTimeMillis();
		JSONObject json = null;
		InputStreamReader isr = null;
		BufferedReader in = null;
		HttpURLConnection con = null;
		URL urlObj;
		try {
			urlObj = new URL(urlString);		
			con = (HttpURLConnection) urlObj.openConnection();
			if(connectionTimeout == null){
				connectionTimeout = STANDARD_CONNECTION_TIMEOUT;
			}
			if(readTimeout == null){
				readTimeout = STANDARD_READ_TIMEOUT;
			}
			con.setConnectTimeout(connectionTimeout);
			con.setReadTimeout(readTimeout);
			//con.setUseCaches(false);
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "Mozilla/5.0");
			if(extraProperties != null){
				for(Map.Entry<String, String> entry : extraProperties.entrySet()){
					String name = entry.getKey();
					String value = entry.getValue();
					if (value != null){
						con.setRequestProperty(name, value);
					}
				}
			}
			int responseCode = con.getResponseCode();
//			System.out.println(responseCode);
//			System.out.println(con.getResponseMessage());
			if (responseCode == HttpURLConnection.HTTP_OK) {
				if (useGZIP){
					isr = new InputStreamReader(new GZIPInputStream(con.getInputStream()));
				} else {
					isr = new InputStreamReader(con.getInputStream());
				}
				in = new BufferedReader(isr);
				String jsonString = readAll(in);
				if(readJSONArray){
					JSONArray jsonArray = new JSONArray(jsonString);
					json = new JSONObject();
					json.put("data", jsonArray);
				} else {
			        json = new JSONObject(jsonString);
				}
		        return json;
			}
			return null;
		} catch (SocketTimeoutException e) {
			return null;
		} finally {
			//System.out.println("Request took " + (System.currentTimeMillis() - initTime) + " ms.");
			if (isr != null){
				isr.close();
			}
			if (in != null){
				in.close();
			}
			if (con != null){
				con.disconnect();
			}
		}
	}
	
	
	public static org.w3c.dom.Document getXMLDoc(String urlString) throws Exception {
		return getXMLDoc(urlString, null);
	}
	
	public static org.w3c.dom.Document getXMLDoc(String urlString, Map<String, String> extraProperties) throws Exception {
		HttpURLConnection con = null;
		URL urlObj;
		try {
			urlObj = new URL(urlString);		
			con = (HttpURLConnection) urlObj.openConnection();
		con.setConnectTimeout(STANDARD_CONNECTION_TIMEOUT);
			con.setReadTimeout(STANDARD_READ_TIMEOUT);
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "Mozilla/5.0");
			if(extraProperties != null){
				for(Map.Entry<String, String> entry : extraProperties.entrySet()){
					String name = entry.getKey();
					String value = entry.getValue();
					if (value != null){
						con.setRequestProperty(name, value);
					}
				}
			}
			int responseCode = con.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			    DocumentBuilder builder = factory.newDocumentBuilder();
			    return builder.parse(con.getInputStream());
			}
			return null;
		} catch (SocketTimeoutException e) {
			return null;
		} finally {
			if (con != null){
				con.disconnect();
			}
		}
	}
	
	public static File getFileFromURL(String urlString, String filename) throws Exception {
		File file = new File(filename);
		FileUtils.copyURLToFile(new URL(urlString), file, TIMEOUT_FILE_TRANSFER, TIMEOUT_FILE_TRANSFER);
		return file;
	}
	
}
