package callsbot;

import java.util.Iterator;

public class UpdaterThread implements Runnable {
	
	/**
	 * Tasks: check whether some calls resolved, update coingecko list of coins
	 */

	private static final long SLEEP_TIME_CYCLE = 3000000l; //ms
	private volatile boolean running = false;
	private CallsBot callsBot;
	
	public UpdaterThread(CallsBot callBot) {
		this.callsBot = callBot;
	}
	
	@Override
	public void run() {
		running = true;
		while(running) {
			long now = System.currentTimeMillis();
			Iterator<Call> iter = callsBot.getActiveCalls().iterator();
			while(iter.hasNext()) {
				Call call = iter.next();
				if(now > call.resolveTime) {
					if(callsBot.resolveCall(call)) {
						iter.remove();
						callsBot.db().commit();
					}
				}
			}
			Utils.sleep(SLEEP_TIME_CYCLE);
			CallsBot.COINGECKO_GRABBER.updateCoins();
			Utils.sleep(10000);
		}
	}

}
