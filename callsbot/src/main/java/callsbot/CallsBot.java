package callsbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.validation.constraints.NotNull;
import org.telegram.abilitybots.api.bot.AbilityBot;
import org.telegram.abilitybots.api.bot.BaseAbilityBot;
import org.telegram.abilitybots.api.objects.Ability;
import org.telegram.abilitybots.api.objects.Flag;
import org.telegram.abilitybots.api.objects.Locality;
import org.telegram.abilitybots.api.objects.MessageContext;
import org.telegram.abilitybots.api.objects.Privacy;
import org.telegram.abilitybots.api.objects.Reply;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberAdministrator;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberOwner;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class CallsBot extends AbilityBot {

	private static final String HELP_TEXT = 
			"----Bot functions----"
			+ "\n/call coin resolvedate(formatted as dd-MM-yyyy, has to be 1 day or more in the future) - start a call for a coin"
			+ "\n/cstats nftcollection - get Opensea stats for an nft collection"
			+ "\n/listactive - get a list of all active calls"
			+ "\n/listmine - get a list of your active calls"
			+ "\n/listresolved - get a list of all resolved calls"
			+ "\n/listresolvedmine - get a list of your resolved calls"
			+ "\n/time - get the local time for the bot"
			+ "\n/help - get this list"
			+ "\n----Admin only----"
			+ "\n/removecalls - remove calls dialog"
			+ "\n----End----";
	private static final String CALLSBOT_NAME = "callsbot";
	private static final String USERS_DB_NAME = "users";
	private static final String ACTIVE_CALLS_DB_NAME = "active";
	private static final String FINISHED_CALLS_DB_NAME = "finished";
	private static final HashMap<Long, Set<Call>> USER_TO_CALL_MAP = new HashMap<Long, Set<Call>>();
	private static final HashMap<Long, Set<ResolvedCall>> USER_TO_RESOLVED_MAP = new HashMap<Long, Set<ResolvedCall>>();
	private static final HashMap<Long, Set<PreCall>> USER_TO_UNFINISHED_CALL_MAP = new HashMap<Long, Set<PreCall>>();
	public static final String API_DATA_LOCATION = "auth/key.txt";
	public static final CoingeckoApiGrabber COINGECKO_GRABBER = new CoingeckoApiGrabber();
	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
	private UpdaterThread callCheckerThread;
	private List<Call> activeCalls;
	private List<ResolvedCall> resolvedCalls;
	private Map<Long, User> userDatabase;
	private TelegramBotsApi botsApi;
	protected String apiKey;
	protected int creatorId;
	protected Logger log = Logger.getLogger(CallsBot.class.getName());
	
	public CallsBot(String token, int creatorId){
		super(token, CALLSBOT_NAME);
		this.creatorId = creatorId;
	}

	private void init() {
		System.out.println("Initializing CallsBot!");
		while(!setupGrabbers()) {
			System.out.println("Something went wrong setting up coin data grabbers, trying again.");
			Utils.sleep(5000);
		}
		System.out.println("Coin data grabbers loaded!");
		activeCalls = db().getList(ACTIVE_CALLS_DB_NAME);
		resolvedCalls = db().getList(FINISHED_CALLS_DB_NAME);
		userDatabase = db().getMap(USERS_DB_NAME);
		for(Call call : activeCalls) {
			Set<Call> addCallHere = USER_TO_CALL_MAP.get(call.userId);
			if(addCallHere == null) {
				addCallHere = new HashSet<Call>();
				USER_TO_CALL_MAP.put(call.userId, addCallHere);
			}
			addCallHere.add(call);
		}
		for(ResolvedCall resolved : resolvedCalls) {
			Set<ResolvedCall> addResolvedHere = USER_TO_RESOLVED_MAP.get(resolved.userId);
			if(addResolvedHere == null) {
				addResolvedHere = new HashSet<ResolvedCall>();
				USER_TO_RESOLVED_MAP.put(resolved.userId, addResolvedHere);
			}
			addResolvedHere.add(resolved);
		}
		System.out.println("DB loaded!");
		try {
			botsApi = new TelegramBotsApi(DefaultBotSession.class);
			botsApi.registerBot(this);
		} catch (TelegramApiException e) {
			e.printStackTrace();
			System.exit(0);
		}
		callCheckerThread = new UpdaterThread(this);
		Thread thread = new Thread(callCheckerThread);
		thread.start();
		System.out.println("Call checker thread started!");
	}

	public static void main(String[] args) {
		String token = null;
		int creatorId = 0;
		try {
			List<String> strings = Files.readAllLines(Paths.get(API_DATA_LOCATION));
			token = strings.get(0);
			creatorId = Integer.valueOf(strings.get(1));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		new CallsBot(token, creatorId).init();
	}
	
	private boolean setupGrabbers() {
		return COINGECKO_GRABBER.updateCoins();
	}
	
	private void addCall(long userId, long chatId, String coinName, double openPrice, long resolveTime, boolean t) {
		Call call = new Call(userId, chatId, coinName, 0d, resolveTime, false);
		addCall(call, userId);
	}
	
	private void addCall(Call call, long userId) {
		activeCalls.add(call);
		Set<Call> addCallHere = USER_TO_CALL_MAP.get(userId);
		if(addCallHere == null) {
			addCallHere = new HashSet<Call>();
			USER_TO_CALL_MAP.put(call.userId, addCallHere);
		}
		addCallHere.add(call);
		db.commit();
	}
	
	public List<Call> getActiveCalls() {
		return activeCalls;
	}

	public List<ResolvedCall> getResolvedCalls() {
		return resolvedCalls;
	}
	
	public Ability helpAbility() {
		return Ability
		      .builder()
		      .name("help")
		      .info("Print bot instructions!")
	          .locality(Locality.ALL)
	          .privacy(Privacy.PUBLIC)
	          .action(ctx -> startHelp(ctx))
	          .build();
	}

	public Ability startCallAbility() {
		return Ability
		      .builder()
		      .name("call")
		      .info("Start a call!")
	          .locality(Locality.ALL)
	          .privacy(Privacy.PUBLIC)
	          .action( ctx -> startCall(ctx))
	          .build();
	}
	
	public Ability listActiveCallsAbility() {
		return Ability
		      .builder()
		      .name("listactive")
		      .info("List active calls!")
	          .locality(Locality.ALL)
	          .privacy(Privacy.PUBLIC)
	          .action(ctx -> listActiveCalls(ctx, false))
	          .build();
	}
	
	public Ability listMyCallsAbility() {
		return Ability
		      .builder()
		      .name("listmine")
		      .info("List active calls for user!")
	          .locality(Locality.ALL)
	          .privacy(Privacy.PUBLIC)
	          .action(ctx -> listActiveCalls(ctx, true))
	          .build();
	}
	
	public Ability removeCallsAbility() {
		return Ability
		      .builder()
		      .name("removecalls")
		      .info("Admin only: remove calls!")
	          .locality(Locality.ALL)
	          .privacy(Privacy.PUBLIC)
	          .action(ctx -> removeCalls(ctx))
	          .build();
	}

	public Ability listResolvedAbility() {
		return Ability
		      .builder()
		      .name("listresolved")
		      .info("List resolved calls!")
	          .locality(Locality.ALL)
	          .privacy(Privacy.PUBLIC)
	          .action(ctx -> listResolved(ctx, false))
	          .build();
	}

	public Ability listResolvedMineAbility() {
		return Ability
		      .builder()
		      .name("listresolvedmine")
		      .info("List resolved calls for user!")
	          .locality(Locality.ALL)
	          .privacy(Privacy.PUBLIC)
	          .action(ctx -> listResolved(ctx, false))
	          .build();
	}
	
	public Ability getTimeAbility() {
		return Ability
		      .builder()
		      .name("time")
		      .info("Get bot local time!")
	          .locality(Locality.ALL)
	          .privacy(Privacy.PUBLIC)
	          .action(ctx -> sendBotTime(ctx))
	          .build();
	}
	
	public Ability getCollectionStatsAbility() {
		return Ability
		      .builder()
		      .name("cstats")
		      .info("Get stats for an Opensea collection!")
	          .locality(Locality.ALL)
	          .privacy(Privacy.PUBLIC)
	          .action(ctx -> getCollectionStats(ctx))
	          .build();
	}

	private void getCollectionStats(MessageContext ctx) {
		String[] args = ctx.arguments();
		User callUser = ctx.user();
		String message = null;
		if(args.length == 1) {
			message = OpenSeaApiGrabber.getCollectionStats(args[0]);
		}
		if(message == null) {
			message = "Couldn't get stats for this slug, " + callUser.getUserName();
		}		
		silent.sendMd(message, ctx.chatId());
	}

	private void sendBotTime(MessageContext ctx) {
		silent.send("My clock currently reads " + getBotTime(), ctx.chatId());
	}

	private String getBotTime() {
		return LocalDateTime.now().format(formatter).toString();
	}
	
	private void listResolved(MessageContext ctx, boolean onlyUser) {
		 String[] args = ctx.arguments();
		 User callUser = ctx.user();
		 int listSize = 10;
		 if(args.length > 0) {
			 String arg = args[1];
			 try {
				 listSize = Integer.valueOf(arg);
			 } catch (NumberFormatException nfe) {
				 //
			 }
		 }
		 int i = 0;
		 int added = 0;
		 StringBuilder sb = new StringBuilder("Resolved calls:\n");
		 for(ResolvedCall resolved : resolvedCalls) {
			 User user = userDatabase.get(resolved.userId);
			 if(onlyUser) {
				 if(!user.getId().equals(callUser.getId())) {
					 i++;
					 continue;
				 }
			 }
			 Coin coin = CoingeckoApiGrabber.ID_TO_COINS_MAP.get(resolved.coinId);
			 LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(resolved.resolveTime), TimeZone.getTimeZone("UTC").toZoneId());
			 sb.append(i+1);
			 if(coin == null) {
				 sb.append(". " + resolved.coinId);
			 } else {
				 sb.append(". " + coin.name + " (" + coin.ticker + ")");
			 }
			 if(resolved.tracked){
				 sb.append(" - tracked call - ");
			 } else {
				 sb.append(" - untracked call - ");
			 }
			 sb.append(user.getFirstName());
			 if(resolved.tracked) {
				 sb.append(" @ $");
				 sb.append(resolved.openPrice);
			 }
			 sb.append(", resolved ");
			 sb.append(dateTime.toString());
			 if(resolved.tracked) {
				 sb.append(". Result was: ");
				 if(resolved.inProfit) {
						sb.append("+");
					}
				 sb.append(resolved.priceChangePercentage);
				 sb.append("%.");
			 }
			 sb.append("\n");
			 i++;
			 added++;
			 if(i > listSize) {
				 break;
			 }
		 }
		 if(onlyUser && added < 1) {
			 silent.send("No resolved calls from you, " + callUser.getFirstName(), ctx.chatId());
		 } else {
			 silent.send(sb.toString(), ctx.chatId());
		 }
	}

	private void listActiveCalls(MessageContext ctx, boolean onlyUser) {
		 String[] args = ctx.arguments();
		 User callUser = ctx.user();
		 int listSize = 10;
		 if(args.length > 0) {
			 String arg = args[1];
			 try {
				 listSize = Integer.valueOf(arg);
			 } catch (NumberFormatException nfe) {
				 //
			 }
		 }
		 int i = 0;
		 int added = 0;
		 StringBuilder sb = new StringBuilder("Active calls:\n");
		 for(Call call : activeCalls) {
			 User user = userDatabase.get(call.userId);
			 if(onlyUser) {
				 if(!user.getId().equals(callUser.getId())) {
					 i++;
					 continue;
				 }
			 }
			 Coin coin = CoingeckoApiGrabber.ID_TO_COINS_MAP.get(call.coinId);
			 LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(call.resolveTime), TimeZone.getTimeZone("UTC").toZoneId());
			 sb.append(i+1);
			 if(coin == null) {
				 sb.append(". " + call.coinId);
			 } else {
				 sb.append(". " + coin.name + " (" + coin.ticker + ")");
			 }
			 if(call.tracked){
				 sb.append(" - tracked call - ");
			 } else {
				 sb.append(" - untracked call - ");
			 }
			 sb.append(user.getFirstName());
			 if(call.tracked) {
				 sb.append(" @ $");
				 sb.append(call.openPrice);
			 }
			 sb.append(", resolves ");
			 sb.append(dateTime.toString());
			 sb.append("\n");
			 i++;
			 added++;
			 if(i > listSize) {
				 break;
			 }
		 }
		 if(onlyUser && added < 1) {
			 silent.send("No calls from you, " + callUser.getFirstName(), ctx.chatId());
		 } else {
			 silent.send(sb.toString(), ctx.chatId());
		 }
	}
		 
	private boolean isChatAdmin(User user, Long chatId) {
		GetChatAdministrators getAdmins = GetChatAdministrators.builder().chatId(chatId + "").build();
	    try {
	    	ArrayList<ChatMember> admins = execute(getAdmins);
	    	for(ChatMember member : admins) {
	    		if(member instanceof ChatMemberAdministrator) {
	    			ChatMemberAdministrator cadmin = (ChatMemberAdministrator)member;
	    			Long id = cadmin.getUser().getId();
	    			if(id != null) {
	    				if(id.equals(user.getId())) {
	    					return true;
	    				}
	    			}
	    		} else if (member instanceof ChatMemberOwner) {
	    			ChatMemberOwner cadmin = (ChatMemberOwner)member;
	    			Long id = cadmin.getUser().getId();
	    			if(id != null) {
	    				if(id.equals(user.getId())) {
	    					return true;
	    				}
	    			}
	    		}
	    	}
	    } catch (TelegramApiException e) {
	      e.printStackTrace();
	    }
	    return false;
	}

	private void startHelp(MessageContext ctx) {
		silent.send(HELP_TEXT + "\nMy clock currently reads " + getBotTime(), ctx.chatId());
	}
		
	private void removeCalls(MessageContext ctx) {
		User user = ctx.user();
		if(!isChatAdmin(ctx.user(), ctx.chatId())) {
			silent.send("This is an admin-only command.", ctx.chatId());
			return;
		}
		long userId = user.getId();
		SendMessage returnMessage = new SendMessage();
		returnMessage.setText("Choose calls to remove, press done when done.");
		returnMessage.setChatId(ctx.chatId() + "");		
		InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        for(Call call : activeCalls) {
        	InlineKeyboardButton button = new InlineKeyboardButton();
	        button.setText(toNeatString(call));
	        button.setCallbackData("/r_c "  + call.userId + call.createTime);
	        rowInline.add(button);
        }
        InlineKeyboardButton cancelButton = new InlineKeyboardButton("Done");
        cancelButton.setCallbackData("/r_d " + userId);
        rowInline.add(cancelButton);
        rowsInline.add(rowInline);
        inlineKeyboard.setKeyboard(rowsInline);
        returnMessage.setReplyMarkup(inlineKeyboard);
        returnMessage.setReplyMarkup(inlineKeyboard);
		try {
		    execute(returnMessage);
		} catch (TelegramApiException e) {
		    e.printStackTrace();
		}
	}
	
	private void startCall(MessageContext ctx) {
		String[] args = ctx.arguments();
		User user = ctx.user();
		String name = user.getFirstName();
		if(!checkCallArgs(args, ctx)) {
			silent.send("Incorrect arguments, " + name + ". Try this ---->\n /call coin resolvedate(formatted as dd-MM-yyyy, has to be 1 day or more in the future)"
					+ "\nMy clock currently reads " + getBotTime(), ctx.chatId());
		}
		userDatabase.put(user.getId(), user);
		db.commit();
	}
	
	public Reply replyToButtons() {
		BiConsumer<BaseAbilityBot, Update> action = (bot, upd) -> replyToButtons(bot, upd);
        return Reply.of(action, Flag.CALLBACK_QUERY);
	}
	
	public void replyToButtons(BaseAbilityBot bot, Update update) {
		//this message has the bot as origin
		Message message = update.getCallbackQuery().getMessage();
		String query = update.getCallbackQuery().getData();
		User fromUser = update.getCallbackQuery().getFrom();
		String[] split = query.split("\\s+");
		if(query.startsWith("/c_u ")) {
			if(split.length > 3) {
				confirmUntrackedCall(message, split[1], split[2], split[3], split[4]);
			}
		} else if (query.startsWith("/c_p ")) {
			if(split.length > 2) {
				try {
					long userId = Long.parseLong(split[1]);
					if(userId == fromUser.getId()) {
						confirmPreCall(update, userId, message.getChatId(), split[2], true);
					} else {
						return;
					}
				} catch (NumberFormatException nfe) {
					nfe.printStackTrace();
				}
			}
		} else if (query.startsWith("/c_c ")) {
			if(split.length > 1) {
				try {
					long userId = Long.parseLong(split[1]);
					if(userId == fromUser.getId()) {
						USER_TO_UNFINISHED_CALL_MAP.remove(userId);
					} else {
						return;
					}
				} catch (NumberFormatException nfe) {
					nfe.printStackTrace();
				}
			}
		} else if (query.startsWith("/r_c ")) {
			if(split.length > 1) {
				int i = 0;
				for(Call call : activeCalls) {
					String compare = "" + call.userId + call.createTime;
					if(compare.equals(split[1])) {
						activeCalls.remove(i);
						Set<Call> calls = USER_TO_CALL_MAP.get(call.userId);
						for (Call compareCall : calls) {
							if(compareCall.userId == call.userId && compareCall.createTime == call.createTime) {
								calls.remove(compareCall);
								break;
							}
						}
						db().commit();
						silent.send(toNeatString(call) + " has been removed.", message.getChatId());
						break;
					}
				}
				return;
			}
		}  else if (query.startsWith("/r_d ")) {
			// jump to delete message
		} else {
			return;
		}
		//only reach this if a valid button has been pressed that should afterwards remove the dialog
		DeleteMessage del = new DeleteMessage(message.getChatId() + "", message.getMessageId());
		try {
			execute(del);
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
	}

	private void confirmPreCall(Update update, long userId, long chatId, String uuid, boolean tracked) {
		 Set<PreCall> precalls = USER_TO_UNFINISHED_CALL_MAP.get(userId);
		 Call call = null;
		 if(precalls != null) {
			 for(PreCall precall : precalls) {
				 if(uuid.equals(precall.uuid)) {
					 call = precall.toCall(userId, chatId, tracked);
					 addCall(call, userId);
					 String name = update.getCallbackQuery().getFrom().getFirstName();
					 LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(call.resolveTime), TimeZone.getTimeZone("UTC").toZoneId());
					 Coin coin = CoingeckoApiGrabber.ID_TO_COINS_MAP.get(call.coinId);					
					 silent.send(name + " started a tracked call for " + coin.toString() + " resolving at " + dateTime.format(formatter) + " UTC, currently at a price of $"
					 		+ call.openPrice + "." , update.getCallbackQuery().getMessage().getChatId());
					 break;
				 }
			 }
		 }
		 //clean all other precalls
		 precalls = new HashSet<PreCall>();
		 USER_TO_UNFINISHED_CALL_MAP.put(userId, precalls);
		 
	}

	private void confirmUntrackedCall(Message message, String coinName, String userName, String userIdString, String timeString) {
		try {
			long userId = Long.valueOf(userIdString);
			long resolveTime = Long.valueOf(timeString);
			addCall(userId, message.getChatId(), coinName, 0d, resolveTime, false);
			SendMessage returnMessage = new SendMessage();
			returnMessage.setChatId(message.getChatId() + "");
			LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(resolveTime), TimeZone.getTimeZone("UTC").toZoneId());  
			returnMessage.setText(userName + " started an untracked call for " + coinName + " resolving at " + dateTime.toString() + " UTC. "
					+ "This means price will not be tracked (DYOR).");
		    execute(returnMessage);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean checkCallArgs(String[] args, MessageContext ctx) {
		if(args.length == 2) {
			try {
				LocalDateTime dateTime = LocalDateTime.parse(args[1] + " 12:00", formatter);
				if(dateTime != null) {
					long unixTimeMs = dateTime.toEpochSecond(ZoneOffset.UTC) * 1000;
					if(unixTimeMs < (System.currentTimeMillis() + Utils.MS_IN_DAY)) {
					    return false;
					}
					getCallCoins(args, unixTimeMs, ctx);
					return true;
				}
			} catch (DateTimeParseException de){
				return false;
			} catch (Exception e){
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	private void getCallCoins(String[] args, long resolveTime, MessageContext ctx) {
		SendMessage returnMessage = new SendMessage();
		returnMessage.setChatId(ctx.chatId() + "");
		Set<Coin> coins = COINGECKO_GRABBER.findCoins(args[0]);
		long userId = ctx.user().getId();
		String userName = ctx.user().getFirstName().replaceAll("\\s+", "_");
		if(coins.size() < 1) {
			String replyText = "Couldn't find a coin with that name on Coingecko, " + userName + ". Do you want to add " + args[0] + " as an untracked call?";
			returnMessage.setText(replyText);
			InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
	        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
	        List<InlineKeyboardButton> rowInline = new ArrayList<>();
	        InlineKeyboardButton yes = new InlineKeyboardButton();
	        yes.setText("Yes");
	        yes.setCallbackData("/c_u " + args[0] + " " + userName + " " + userId + " " + resolveTime);
	        rowInline.add(yes);
	        InlineKeyboardButton no = new InlineKeyboardButton();
	        no.setText("Cancel");
	        no.setCallbackData("/c_c " + userId);
	        rowInline.add(no);
	        rowsInline.add(rowInline);
	        inlineKeyboard.setKeyboard(rowsInline);
	        returnMessage.setReplyMarkup(inlineKeyboard);
		} else {
			String replyText = "Confirm the coin you want to start a call for:";
			returnMessage.setText(replyText);
			InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
	        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
	        List<InlineKeyboardButton> rowInline = new ArrayList<>();
	        HashSet<PreCall> preCallSet = new HashSet<PreCall>();
	        for(Coin coin : coins) {
	        	String uuid = UUID.randomUUID().toString();
	        	InlineKeyboardButton button = new InlineKeyboardButton();
	        	Double price = COINGECKO_GRABBER.getPrice(coin);
				if(price != null) {
		        	button.setText(coin.toString() + "\nat $" + price);
		        	button.setCallbackData("/c_p " + userId + " " + uuid);
		        	rowInline.add(button);
		    		PreCall preCall = new PreCall(uuid, coin.id, price, resolveTime);
		    		preCallSet.add(preCall);
				} else {
					continue;
				}
	        }
	        if(rowInline.size() < 1) {
	        	replyText = "Couldn't find price data on Coingecko, " + userName + ". Do you want to add " + args[0] + " as an untracked call?";
				returnMessage.setText(replyText);
		        InlineKeyboardButton yes = new InlineKeyboardButton();
		        yes.setText("Yes");
		        yes.setCallbackData("/c_u " + args[0] + " " + userName + " " + userId + " " + resolveTime);
		        rowInline.add(yes);
	        } else {
		        USER_TO_UNFINISHED_CALL_MAP.put(userId, preCallSet);
	        }
	        InlineKeyboardButton cancelButton = new InlineKeyboardButton("Cancel");
	        cancelButton.setCallbackData("/c_c " + userId);
	        rowInline.add(cancelButton);
	        rowsInline.add(rowInline);
	        inlineKeyboard.setKeyboard(rowsInline);
	        returnMessage.setReplyMarkup(inlineKeyboard);
	        returnMessage.setReplyMarkup(inlineKeyboard);
		}
		try {
		    execute(returnMessage);
		} catch (TelegramApiException e) {
		    e.printStackTrace();
		}
	}

	@NotNull
    private Predicate<Update> hasMessageWith(String msg) {
        return upd -> upd.getMessage().getText().equalsIgnoreCase(msg);
    }
	public String getBotUsername() {
        return CALLSBOT_NAME;
    }
    
    public Logger getLogger() {
		return log;
	}

	@Override
	public long creatorId() {
		return creatorId;
	}

	//The iterator in UpdaterThread already removes the Call from the underlying list so only remove from the user map set
	public boolean resolveCall(Call call) {
		Double closePrice = 0d;
		if(call.tracked) {
			Coin coin = CoingeckoApiGrabber.ID_TO_COINS_MAP.get(call.coinId);
			closePrice = COINGECKO_GRABBER.getPrice(coin);
			if(closePrice == null) {
				System.out.println("WARNING: Couldn't get price for call " + call.toString() + " coin " + coin.toString());
				return false;
			}
		}
		User user = userDatabase.get(call.userId);
		if(user != null) {
			Set<Call> calls = USER_TO_CALL_MAP.get(call.userId);
			for (Call compareCall : calls) {
				if(compareCall.userId == call.userId && compareCall.createTime == call.createTime) {
					calls.remove(compareCall);
					break;
				}
			}
		}		
		ResolvedCall resolvedCall = call.resolve(closePrice);
		resolvedCalls.add(resolvedCall);
		Set<ResolvedCall> userSet = USER_TO_RESOLVED_MAP.get(user.getId());
		if(userSet == null) {
			userSet = new HashSet<ResolvedCall>();
			USER_TO_RESOLVED_MAP.put(user.getId(), userSet);
		}
		userSet.add(resolvedCall);
		silent.send("*************A call has resolved*************\n" + toResultString(resolvedCall), call.chatId);
		return true;
	}

	private String toNeatString(Call call) {
		User user = userDatabase.get(call.userId);
		String coinName = call.coinId;
		Coin coin = CoingeckoApiGrabber.ID_TO_COINS_MAP.get(call.coinId);
		if(coin != null) {
			coinName = coin.name + " (" + coin.ticker + ")";
		}
		LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(call.createTime), TimeZone.getTimeZone("UTC").toZoneId());
		LocalDateTime endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(call.resolveTime), TimeZone.getTimeZone("UTC").toZoneId());
		StringBuilder sb = new StringBuilder("Call: ");
		sb.append(user.getUserName());
		sb.append(" called ");
		sb.append(coinName);
		sb.append(" starting on ");
		sb.append(startTime.format(formatter) + " UTC ");
		sb.append(" and ending on ");		
		sb.append(endTime.format(formatter) + " UTC ");
		return sb.toString();
	}

	public String toResultString(ResolvedCall resolvedCall) {
		User user = userDatabase.get(resolvedCall.userId);
		String coinName = resolvedCall.coinId;
		Coin coin = CoingeckoApiGrabber.ID_TO_COINS_MAP.get(resolvedCall.coinId);
		if(coin != null) {
			coinName = coin.name + " (" + coin.ticker + ")";
		}
		LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(resolvedCall.createTime), TimeZone.getTimeZone("UTC").toZoneId());
		LocalDateTime endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(resolvedCall.resolveTime), TimeZone.getTimeZone("UTC").toZoneId());
		StringBuilder sb = new StringBuilder("Resolved call: @");
		sb.append(user.getUserName());
		sb.append(" called ");
		sb.append(coinName);
		sb.append(" started on ");
		sb.append(startTime.format(formatter) + " UTC ");
		sb.append(" and ended on ");		
		sb.append(endTime.format(formatter) + " UTC ");
		if(resolvedCall.tracked) {
			sb.append(", end result: ");
			if(resolvedCall.inProfit) {
				sb.append("+");
			}
			sb.append(resolvedCall.priceChangePercentage);
			sb.append("%.");
			return sb.toString();	
		} else {
			sb.append(", untracked -> DYOR");
		}
		return sb.toString();
	}

}
