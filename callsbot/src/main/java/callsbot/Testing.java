package callsbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Testing {

	public static void main(String[] args) {
		CallsBot bot = null;
		try {
			TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
			String token = null;
			int creatorId = 0;
			try {
				List<String> strings = Files.readAllLines(Paths.get(CallsBot.API_DATA_LOCATION));
				token = strings.get(0);
				creatorId = Integer.valueOf(strings.get(1));
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			bot = new CallsBot(token, creatorId);
			botsApi.registerBot(bot);
		} catch (TelegramApiException e) {
			e.printStackTrace();
			System.exit(0);
		}
		
		testDb(bot);
	}

	private static void testDb(CallsBot bot) {
		List<Call> calls = bot.getActiveCalls();
//		calls.remove(0);
//		Call call = new Call(123345, "BTC_bitcoin", 500, 100000000, true);
//		calls.add(call);
//		bot.db().commit();
//		System.out.println(calls.get(0).toString());
		bot.db().clear();
		bot.db().commit();
//		System.out.println(calls.size());
	}

}
