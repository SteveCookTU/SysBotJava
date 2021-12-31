package me.ezpzstreamz.sysbotjava;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.common.events.user.PrivateMessageEvent;
import me.ezpzstreamz.sysbotcontroller.SysBotController;
import me.ezpzstreamz.sysbotjava.listeners.SlashCommandListener;
import me.ezpzstreamz.sysbotjava.util.Util;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class SysBotJava implements Runnable {

    private final JDA jda;
    private final TwitchClient twitchClient;

    private final ArrayDeque<TradeEntry<String, String>> userList;
    private final SysBotController sbc;
    private final PKMGeneratorClient pkmGeneratorClient;
    private final Map<String, String> pointers;

    private final Map<String, byte[]> pkmFiles;

    private long startTime;
    private boolean waitingOnTwitch = false;
    private String linkCode = "";
    private TradeEntry<String, String> currentUser;

    private static final List<Short> tradeSpecies = new ArrayList<>() {{
        add((short) 61);
        add((short) 64);
        add((short) 67);
        add((short) 75);
        add((short) 79);
        add((short) 93);
        add((short) 95);
        add((short) 112);
        add((short) 117);
        add((short) 123);
        add((short) 125);
        add((short) 126);
        add((short) 137);
        add((short) 233);
        add((short) 356);
        add((short) 366);
    }};

    private volatile boolean shutdown = false;

    public SysBotJava(String mode, String token) throws IOException, ExecutionException, InterruptedException {
        PKMGeneratorClient pkmGeneratorClient1;
        JDA jdaTemp = null;
        TwitchClient tcTemp = null;
        pointers = new HashMap<>();
        pkmFiles = new HashMap<>();
        try (InputStream resource = SysBotJava.class.getClassLoader().getResourceAsStream("pointers.txt")) {
            assert resource != null;
            List<String> lines = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).lines()
                    .toList();
            for (String s : lines) {
                String[] args = s.split(":");
                pointers.put(args[0], args[1]);
            }
        }
        if (mode.equalsIgnoreCase("discord")) {
            try {
                jdaTemp = JDABuilder.createDefault(token)
                        .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES,
                                GatewayIntent.GUILD_MEMBERS).addEventListeners(new SlashCommandListener(this)).build();
                //RegisterSlashCommands.registerSlashCommands(jdaTemp);
            } catch (LoginException e) {
                e.printStackTrace();
            }
        } else if (mode.equalsIgnoreCase("twitch")) {
            System.out.println("Logging into twitch");
            OAuth2Credential credential = new OAuth2Credential("twitch", token);
            tcTemp = TwitchClientBuilder.builder().withEnableChat(true).withChatAccount(credential).build();
        }
        File pokemonFolder = new File("pkb");
        FileFilter filter = new WildcardFileFilter("*.pb8");
        File[] pokemon = pokemonFolder.listFiles(filter);
        assert pokemon != null;
        for (File f : pokemon) {
            pkmFiles.put(f.getName().substring(0, f.getName().length() - 4).toLowerCase(),
                    Files.readAllBytes(Paths.get(f.getPath())));
        }
        sbc = new SysBotController("10.0.0.53", 6000);
        sbc.connect();
        pkmGeneratorClient1 = new PKMGeneratorClient("127.0.0.1", 7000);
        pkmGeneratorClient1.connect();
        if(!pkmGeneratorClient1.isConnected()) {
            pkmGeneratorClient1 = null;
        }
        pkmGeneratorClient = pkmGeneratorClient1;
        userList = new ArrayDeque<>();
        jda = jdaTemp;
        twitchClient = tcTemp;
        if (twitchClient != null) {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> twitchClient.getChat().sendMessage("EzPzStreamz",
                            "Welcome to the beta testing of the bot! Type \"!q join\" to join and \"!q pos\" to see your current position in the queue. "),
                    1, 5, TimeUnit.MINUTES);

            twitchClient.getChat().joinChannel("EzPzStreamz");
            twitchClient.getChat().getEventManager().onEvent(ChannelMessageEvent.class, event -> {
                String[] command = event.getMessage().split(" ");
                if ((command[0] + " " + command[1]).equalsIgnoreCase("!q join")) {
                    String set = event.getMessage().substring(7).trim();
                    if (!queueContainsUser("twitch", event.getUser().getName())) {
                        byte[] data;
                        if(!set.equalsIgnoreCase("")) {
                            if(pkmGeneratorClient != null && pkmGeneratorClient.isConnected()) {
                                try {
                                    String resp = pkmGeneratorClient.sendShowdownString(set).get();
                                    if(resp.equalsIgnoreCase("invalid") || resp.equalsIgnoreCase("invalidTrade")) {
                                        event.reply(event.getTwitchChat(),
                                                "The requested set is invalid! Please edit and try again.");
                                    } else {
                                        data = HexFormat.of().parseHex(resp);
                                        if(data.length == 0x158) {
                                            if (getQueueSize() == 0) {
                                                event.reply(event.getTwitchChat(),
                                                        "You have joined the queue. You are first in line!");
                                            } else {
                                                event.reply(event.getTwitchChat(),
                                                        "You have joined the queue. There are " + getQueueSize() +
                                                                " users in front of you. I will request a link code when it is your turn.");
                                            }
                                            addToQueue("twitch", event.getUser().getName(), data);
                                        } else {
                                            event.reply(event.getTwitchChat(),
                                                    "The requested set is invalid! Please edit and try again.");
                                        }
                                    }
                                } catch (InterruptedException | ExecutionException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                event.reply(event.getTwitchChat(),
                                        "Custom generation is currently disabled.");
                            }
                        } else {
                            if (getQueueSize() == 0) {
                                event.reply(event.getTwitchChat(),
                                        "You have joined the queue. You are first in line!");
                            } else {
                                event.reply(event.getTwitchChat(),
                                        "You have joined the queue. There are " + getQueueSize() +
                                                " users in front of you. I will request a link code when it is your turn.");
                            }
                            addToQueue("twitch", event.getUser().getName(), new byte[0]);
                        }

                    } else {
                        event.reply(event.getTwitchChat(),
                                "@" + event.getUser().getName() + " is already in the queue.");
                    }
                } else if ((command[0] + " " + command[1]).equalsIgnoreCase("!q pos")) {
                    int pos = getQueuePosition("twitch", event.getUser().getName());
                    if (pos != -1) {
                        event.reply(event.getTwitchChat(), "@" + event.getUser().getName() + " is position " + pos);
                    } else {
                        event.reply(event.getTwitchChat(), "@" + event.getUser().getName() + " is not in the queue.");
                    }
                }
            });

            twitchClient.getChat().getEventManager().onEvent(PrivateMessageEvent.class, event -> {
                if (isWaitingOnTwitch() && event.getUser().getName().equalsIgnoreCase(currentUser.getValue())) {
                    String msg = event.getMessage().replace("-", "").replace(" ", "").trim();
                    if (isValidLinkCode(msg)) {
                        waitingOnTwitch = false;
                        setLinkCode(msg);
                        twitchClient.getChat()
                                .sendMessage("EzPzStreamz", "@" + currentUser.getValue() + " link code received.");
                    } else {
                        twitchClient.getChat()
                                .sendMessage("EzPzStreamz",
                                        "@" + currentUser.getValue() + " invalid link code. Please send another.");
                    }


                }
            });
        }
        sbc.updateFreezeRate().get();
        sbc.freeze(pointers.get("instantText"), "0xFFFF7F7F").get();
        sbc.freeze(pointers.get("fps"), "0x01").get();
        sbc.sendCommand("configure keySleepTime 60").get();
    }

    public void addToQueue(String mode, String userID, byte[] tradeType) {
        userList.add(new TradeEntry<>(mode, userID, tradeType));
    }

    public int getQueueSize() {
        return userList.size();
    }

    public boolean queueContainsUser(String mode, String userID) {
        List<TradeEntry<String, String>> tempList = userList.stream().toList();
        for (TradeEntry<String, String> stringStringEntry : tempList) {
            if (stringStringEntry.getKey().equalsIgnoreCase(mode) &&
                    stringStringEntry.getValue().equalsIgnoreCase(userID))
                return true;
        }
        return false;
    }

    private boolean isValidLinkCode(String code) {
        try {
            int temp = Integer.parseInt(code);
            if (temp < 0 || temp > 120000000)
                return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public int getQueuePosition(String mode, String userID) {
        List<TradeEntry<String, String>> tempList = userList.stream().toList();
        for (int i = 0; i < tempList.size(); i++) {
            if (tempList.get(i).getKey().equalsIgnoreCase(mode) && tempList.get(i).getValue().equalsIgnoreCase(userID))
                return i + 1;
        }
        return -1;
    }

    public boolean hasTimedOut() {
        return TimeUnit.SECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS) - startTime > 120;
    }

    private void generateLinkCode() {
        linkCode = String.format("%08d", ThreadLocalRandom.current().nextInt(1, 99999999 + 1));
    }

    private void hideHider() {
        File hiderImage = new File("hide.png");
        if (hiderImage.exists()) {
            hiderImage.renameTo(new File("show.png"));
        }
    }

    private void showHider() {
        File hiderImage = new File("show.png");
        if (hiderImage.exists()) {
            hiderImage.renameTo(new File("hide.png"));
        }
    }

    public boolean isSameTradePhase(int nextPhase, int currentPhase)
            throws IOException, InterruptedException, ExecutionException {
        int state = Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16);
        while (state < nextPhase) {
            if (checkIfCancelled(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16), currentPhase))
                return true;
            if (hasTimedOut() || checkIfCancelled(state, currentPhase)) {
                System.out.println("Trade timed out.");
                sbc.click("B", 1500).get();
                leaveTrade();
                TimeUnit.MILLISECONDS.sleep(500);
                leaveUnionRoom();
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(500);
            state = Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16);
        }
        return false;
    }

    public void joinUnionRoom() throws InterruptedException, IOException, ExecutionException {
        //Join union room
        sbc.click("Y", 1200).get();
        sbc.click("DRIGHT", 1200).get();
        sbc.click("A", 1200).get();
        sbc.click("A", 1200).get();
        sbc.click("A", 1200).get();
        sbc.click("DDOWN", 500).get();
        sbc.click("DDOWN", 500).get();
        sbc.click("A", 1200).get();
        sbc.click("A", 1200).get();

        //waitForConnection
        TimeUnit.MILLISECONDS.sleep(4000);

        sbc.click("A", 1200).get();
        sbc.click("A", 1200).get();
        showHider();
        sbc.click("A", 9000).get();
        //System.out.println("Link Code: " + linkCode);
        sbc.enterCode(linkCode).get();
        TimeUnit.MILLISECONDS.sleep(3000);

        sbc.click("PLUS", 1500).get();
        sbc.click("A", 3000).get();
        while (Integer.parseInt(sbc.peek(pointers.get("isRunningSession"), 1).get(), 16) != 1) {
            redoLinkCode();
        }
        hideHider();
        while (Integer.parseInt(sbc.peek(pointers.get("isGaming"), 1).get(), 16) != 1) {
            TimeUnit.MILLISECONDS.sleep(500);
        }
        sbc.moveForward(1200).get();
    }

    private void redoLinkCode() throws InterruptedException, ExecutionException {
        deleteLinkCodeEntryHalf();
        deleteLinkCodeEntryHalf();

        sbc.enterCode(linkCode).get();
        TimeUnit.SECONDS.sleep(3);

        sbc.click("PLUS", 1500).get();
        sbc.click("A", 3000).get();
    }

    private void deleteLinkCodeEntryHalf() throws InterruptedException, ExecutionException {
        sbc.sendCommand("key 42").get();
        TimeUnit.MILLISECONDS.sleep(500);
        sbc.sendCommand("key 42").get();
        TimeUnit.MILLISECONDS.sleep(500);
        sbc.sendCommand("key 42").get();
        TimeUnit.MILLISECONDS.sleep(500);
        sbc.sendCommand("key 42").get();
        TimeUnit.MILLISECONDS.sleep(500);
    }

    private void startTradeRecruitment() throws ExecutionException, InterruptedException {

        sbc.click("Y", 1200).get();
        sbc.click("A", 1200).get();
        sbc.click("DDOWN", 1200).get();
        sbc.click("A", 1200).get();

    }

    public void injectCustom() throws ExecutionException, InterruptedException {
        sbc.poke(pointers.get("b1s1"), "0x" + Util.bytesToHex(currentUser.getRequest())).get();
    }

    public void injectDefault() throws ExecutionException, InterruptedException {
        if (pkmFiles.containsKey("default")) {
            sbc.poke(pointers.get("b1s1"), "0x" + Util.bytesToHex(Util.encryptPb8(pkmFiles.get("default")))).get();
        } else {
            List<String> names = pkmFiles.keySet().stream().toList();
            int randomName = ThreadLocalRandom.current().nextInt(0, names.size());
            sbc.poke(pointers.get("b1s1"),
                    "0x" + Util.bytesToHex(Util.encryptPb8(pkmFiles.get(names.get(randomName))))).get();
        }
    }

    public boolean tradeDefault(int currentPhase)
            throws ExecutionException, InterruptedException, IOException {
        String pb8Str = sbc.peek(pointers.get("tradePB8"), 0x158).get();
        byte[] pb8 = Util.decryptEb8(HexFormat.of().parseHex(pb8Str));
        byte[] nickname = Arrays.copyOfRange(pb8, 88, 112);
        String nicknameStr =
                new String(nickname, StandardCharsets.ISO_8859_1).trim().replaceAll("\0", "").toLowerCase(
                        Locale.ROOT);
        if (pkmFiles.containsKey(nicknameStr)) {
            sbc.click("B", 1200).get();
            sbc.poke(pointers.get("b1s1"), "0x" + Util.bytesToHex(Util.encryptPb8(pkmFiles.get(nicknameStr)))).get();
            int state = Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16);
            while (state != 3 && state != 4) {
                sbc.click("A", 1200).get();
                state = Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16);
            }

            currentPhase = 3;
            int nextPhase = 4;

            if (isSameTradePhase(nextPhase, currentPhase)) return false;

            if (checkIfCancelled(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16),
                    currentPhase)) return false;

            currentPhase = nextPhase;

            pb8Str = sbc.peek(pointers.get("tradePB8"), 0x158).get();
        }

        pb8 = Util.decryptEb8(HexFormat.of().parseHex(pb8Str));
        return checkIsEggOrTradeEvo(currentPhase, pb8);

    }

    public boolean tradeCustom(int currentPhase) throws ExecutionException, InterruptedException, IOException {
        String pb8Str = sbc.peek(pointers.get("tradePB8"), 0x158).get();
        byte[] pb8 = Util.decryptEb8(HexFormat.of().parseHex(pb8Str));
        return checkIsEggOrTradeEvo(currentPhase, pb8);
    }

    private boolean checkIsEggOrTradeEvo(int currentPhase, byte[] pb8)
            throws InterruptedException, IOException, ExecutionException {
        ByteBuffer bb = ByteBuffer.wrap(pb8, 8, 10);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        short species = bb.getShort();
        System.out.println(species);
        bb = ByteBuffer.wrap(pb8, 140, 144);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        boolean isEgg = (bb.getInt() >> 30 & 0x1) == 1;

        if (isEgg || tradeSpecies.contains(species)) {
            if (checkIfCancelled(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16),
                    currentPhase)) return false;
            System.out.println("Egg or trade species offered.");
            sbc.click("B", 1500).get();
            leaveTrade();
            TimeUnit.MILLISECONDS.sleep(500);
            //leave union room
            leaveUnionRoom();
            return false;
        }
        if (checkIfCancelled(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16),
                currentPhase))
            return false;
        sbc.click("A", 1300).get();
        return true;
    }

    public void startTradeRoute() {
        try {
            Thread.sleep(2000);
            joinUnionRoom();

            startTradeRecruitment();

            int state = Integer.parseInt(sbc.peek(pointers.get("onlineState"), 1).get(), 16);
            while (state != 4 && state != 8) {
                sbc.click("B", 1200).get();
                sbc.click("B", 1200).get();
                startTradeRecruitment();
                state = Integer.parseInt(sbc.peek(pointers.get("onlineState"), 1).get(), 16);
            }

            if(currentUser.getRequest().length == 0) {
                injectDefault();
            } else {
                System.out.println(Util.bytesToHex(currentUser.getRequest()));
                injectCustom();
            }

            TimeUnit.MILLISECONDS.sleep(600);
            startTime = TimeUnit.SECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
            while (Integer.parseInt(sbc.peek(pointers.get("onlineState"), 1).get(), 16) != 8) {
                if (hasTimedOut()) {
                    System.out.println("Trade timed out.");
                    sbc.click("B", 1200).get();
                    sbc.click("B", 1200).get();
                    sbc.click("B", 1200).get();

                    //leave union room
                    sbc.click("B", 1200).get();
                    sbc.click("Y", 1200).get();
                    sbc.click("DDOWN", 1200).get();
                    sbc.click("A", 5000).get();

                    return;
                }

                TimeUnit.MILLISECONDS.sleep(1000);
            }

            TimeUnit.MILLISECONDS.sleep(1000);

            startTime += 15;
            sbc.click("A", 1200).get();
            sbc.click("A", 1200).get();
            sbc.click("A", 1200).get();

            TimeUnit.MILLISECONDS.sleep(1200);

            if (Integer.parseInt(sbc.peek(pointers.get("onlineState"), 1).get(), 16) == 18) {
                TimeUnit.MILLISECONDS.sleep(1200);

                sbc.click("A", 1300).get();
                sbc.click("A", 1300).get();

                int currentPhase = 3;
                int nextPhase = 4;

                if (isSameTradePhase(nextPhase, currentPhase)) return;

                startTime += 15;

                if (checkIfCancelled(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16),
                        currentPhase))
                    return;
                currentPhase = nextPhase;
                nextPhase = 6;
                TimeUnit.MILLISECONDS.sleep(500);

                if(currentUser.getRequest().length == 0) {
                    if(!tradeDefault(currentPhase)) return;
                } else {
                    if(!tradeCustom(currentPhase)) return;
                }

                if (isSameTradePhase(nextPhase, currentPhase)) return;
                if (checkIfCancelled(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16),
                        currentPhase))
                    return;
                currentPhase = nextPhase;
                nextPhase = 9;

                while (Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16) < nextPhase) {
                    sbc.click("B", 1500).get();
                }

                if (checkIfCancelled(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1).get(), 16),
                        currentPhase))
                    return;

                while (Integer.parseInt(sbc.peek(pointers.get("tradeFlowState"), 1).get(), 16) == 3) {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                TimeUnit.MILLISECONDS.sleep(2000);
                leaveTrade();
                TimeUnit.MILLISECONDS.sleep(500);
                leaveUnionRoom();

            } else {
                leaveUnionRoom();
            }
        } catch (InterruptedException | IOException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private boolean checkIfCancelled(int state, int currentPhase)
            throws InterruptedException, IOException, ExecutionException {
        if (state < currentPhase) {
            System.out.println("Trade cancelled.");
            TimeUnit.MILLISECONDS.sleep(1200);
            sbc.click("B", 1500).get();
            leaveTrade();
            TimeUnit.MILLISECONDS.sleep(500);
            leaveUnionRoom();
            return true;
        }
        if (state == 0xA) {
            System.out.println("Trade cancelled.");
            TimeUnit.MILLISECONDS.sleep(1200);
            sbc.click("B", 1500).get();
            TimeUnit.MILLISECONDS.sleep(500);
            leaveUnionRoom();
            return true;
        }
        if (state == 0xB) {
            System.out.println("Trade cancelled.");
            TimeUnit.MILLISECONDS.sleep(1200);
            sbc.click("B", 1500).get();
            sbc.click("B", 1500).get();
            TimeUnit.MILLISECONDS.sleep(500);
            leaveUnionRoom();
            return true;
        }
        return false;
    }

    public void leaveTrade() throws InterruptedException, ExecutionException {
        sbc.click("B", 1200).get();
        sbc.click("DUP", 1200).get();
        sbc.click("A", 1200).get();
        sbc.click("A", 1200).get();
    }

    public void leaveUnionRoom() throws InterruptedException, ExecutionException {
        sbc.click("B", 1200).get();
        sbc.click("Y", 1200).get();
        sbc.click("DDOWN", 1200).get();
        sbc.click("A", 1200).get();
        Thread.sleep(3000);
    }

    public void runDisc(TradeEntry<String, String> entry) {
        generateLinkCode();
        User user = jda.retrieveUserById(Long.parseUnsignedLong(entry.getValue())).complete();
        if (user != null) {
            user.openPrivateChannel()
                    .queue(s -> s.sendMessage("Waiting in the global room with link code: " + linkCode)
                            .queue());
            startTradeRoute();
        }
    }

    private void setLinkCode(String linkCode) {
        this.linkCode = String.format("%08d", Integer.parseInt(linkCode));
    }

    private boolean isWaitingOnTwitch() {
        return waitingOnTwitch;
    }

    public void runTwitch() throws InterruptedException {
        waitingOnTwitch = true;
        startTime = TimeUnit.SECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
        twitchClient.getChat().sendMessage("EzPzStreamz",
                "@" + currentUser.getValue() + " It's your turn! Whisper me a link code to get started.");
        while (isWaitingOnTwitch()) {
            if (hasTimedOut()) return;
            TimeUnit.MILLISECONDS.sleep(500);
        }
        startTradeRoute();
    }

    @Override
    public void run() {
        while (!shutdown) {
            if (userList.peek() != null) {
                currentUser = userList.poll();
                if (currentUser.getKey().equalsIgnoreCase("Discord")) {
                    runDisc(currentUser);
                } else if (currentUser.getKey().equalsIgnoreCase("Twitch")) {
                    try {
                        runTwitch();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void shutdown() throws IOException {
        shutdown = true;
        sbc.disconnect();
    }
}
