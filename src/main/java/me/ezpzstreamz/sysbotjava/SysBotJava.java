package me.ezpzstreamz.sysbotjava;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import me.ezpzstreamz.sysbotcontroller.SysBotController;
import me.ezpzstreamz.sysbotjava.listeners.SlashCommandListener;
import me.ezpzstreamz.sysbotjava.util.Util;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class SysBotJava implements Runnable {

    private final JDA jda;
    private final TwitchClient twitchClient;

    private final ArrayDeque<Map.Entry<String, String>> userList;
    private final SysBotController sbc;
    private final Map<String, String> pointers;

    private final byte[] pkm;
    private long startTime;

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

    public SysBotJava(String mode, String token) throws IOException {
        JDA jdaTemp = null;
        TwitchClient tcTemp = null;
        pointers = new HashMap<>();
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
            tcTemp.getChat().joinChannel("EzPzStreamz");
            tcTemp.getChat().getEventManager().onEvent(ChannelMessageEvent.class, event -> {
                if (event.getMessage().equalsIgnoreCase("!q join")) {
                    if (!queueContainsUser("twitch", event.getUser().getName())) {
                        event.reply(event.getTwitchChat(),
                                "You have joined the queue. There are " + getQueueSize() +
                                        " users in front of you. You will receive a link code when it is your turn.");
                        addToQueue("twitch", event.getUser().getName());
                    } else {
                        event.reply(event.getTwitchChat(),
                                "@" + event.getUser().getName() + " is already in the queue.");
                    }
                } else if (event.getMessage().equalsIgnoreCase("!q pos")) {
                    int pos = getQueuePosition("twitch", event.getUser().getName());
                    if (pos != -1) {
                        event.reply(event.getTwitchChat(), "@" + event.getUser().getName() + " is position " + pos);
                    } else {
                        event.reply(event.getTwitchChat(), "@" + event.getUser().getName() + " is not in the queue.");
                    }
                }
            });
        }
        pkm = Files.readAllBytes(Paths.get("pkb/pokemon.pb8"));
        sbc = new SysBotController("10.0.0.53", 6000);
        sbc.connect();
        userList = new ArrayDeque<>();
        jda = jdaTemp;
        twitchClient = tcTemp;
        sbc.sendCommand("configure keySleepTime 35");
    }

    public synchronized void addToQueue(String mode, String userID) {
        userList.add(new AbstractMap.SimpleEntry<>(mode, userID));
    }

    public synchronized int getQueueSize() {
        return userList.size();
    }

    public synchronized boolean queueContainsUser(String mode, String userID) {
        List<Map.Entry<String, String>> tempList = userList.stream().toList();
        for (Map.Entry<String, String> stringStringEntry : tempList) {
            if (stringStringEntry.getKey().equalsIgnoreCase(mode) &&
                    stringStringEntry.getValue().equalsIgnoreCase(userID))
                return true;
        }
        return false;
    }

    public synchronized int getQueuePosition(String mode, String userID) {
        List<Map.Entry<String, String>> tempList = userList.stream().toList();
        for (int i = 0; i < tempList.size(); i++) {
            if (tempList.get(i).getKey().equalsIgnoreCase(mode) && tempList.get(i).getValue().equalsIgnoreCase(userID))
                return i;
        }
        return -1;
    }

    public synchronized boolean hasTimedOut() {
        return TimeUnit.SECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS) - startTime > 120;
    }

    private String generateLinkCode() {
        return String.format("%08d", ThreadLocalRandom.current().nextInt(1, 99999999 + 1));
    }

    private void hideHider() {
        File hiderImage = new File("hide.png");
        if(hiderImage.exists()) {
            hiderImage.renameTo(new File("show.png"));
        }
    }

    private void showHider() {
        File hiderImage = new File("show.png");
        if(hiderImage.exists()) {
            hiderImage.renameTo(new File("hide.png"));
        }
    }

    public void startTradeRoute(String linkCode) {
        try {
            //Join union room
            sbc.click("Y", 1500);
            sbc.click("DRIGHT", 1500);
            sbc.click("A", 1500);
            sbc.click("A", 1500);
            sbc.click("A", 1500);
            sbc.click("DDOWN", 1000);
            sbc.click("DDOWN", 1000);
            sbc.click("A", 1500);
            sbc.click("A", 1500);

            //waitForConnection
            Thread.sleep(4000);

            sbc.click("A", 1500);
            sbc.click("A", 1500);
            showHider();
            sbc.click("A", 9000);
            //System.out.println("Link Code: " + linkCode);
            sbc.enterCode(linkCode);
            Thread.sleep(3000);

            sbc.click("PLUS", 1500);
            hideHider();
            sbc.click("A", 5000);
            sbc.moveForward(500);
            Thread.sleep(5000);

            //Start and wait for trade
            sbc.click("Y", 1100);
            sbc.click("A", 1100);
            sbc.click("DDOWN", 1100);
            sbc.click("A", 1100);

            sbc.poke(pointers.get("b1s1"), "0x" + Util.bytesToHex(Util.encryptPb8(pkm)));

            Thread.sleep(600);
            startTime = TimeUnit.SECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
            while (Integer.parseInt(sbc.peek(pointers.get("onlineState"), 1), 16) != 8) {
                if (hasTimedOut()) {
                    System.out.println("Trade timed out.");
                    sbc.click("B", 1000);
                    sbc.click("B", 1000);
                    sbc.click("B", 1000);

                    //leave union room
                    sbc.click("B", 1000);
                    sbc.click("Y", 1000);
                    sbc.click("DDOWN", 1000);
                    sbc.click("A", 5000);

                    return;
                }

                Thread.sleep(1000);
            }

            sbc.click("A", 1000);
            sbc.click("A", 1000);
            sbc.click("A", 1000);

            while (Integer.parseInt(sbc.peek(pointers.get("onlineState"), 1), 16) != 18) {
                Thread.sleep(500);
            }

            if (Integer.parseInt(sbc.peek(pointers.get("onlineState"), 1), 16) == 18) {
                Thread.sleep(1000);
                int currentPhase = 3;
                int nextPhase = 4;

                sbc.click("A", 1000);
                sbc.click("A", 1000);

                while (Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1), 16) < nextPhase) {
                    if (checkIfCancelled(currentPhase)) return;
                    if (hasTimedOut()) {
                        System.out.println("Trade timed out.");
                        sbc.click("B", 1500);
                        leaveTrade();
                        Thread.sleep(500);
                        //leave union room
                        leaveUnionRoom();
                        return;
                    }
                    Thread.sleep(500);
                }

                if(checkIfCancelled(currentPhase)) return;
                currentPhase = nextPhase;
                nextPhase = 6;
                Thread.sleep(500);
                String pb8Str = sbc.peek(pointers.get("tradePB8"), 0x158);
                byte[] pb8 = Util.decryptEb8(HexFormat.of().parseHex(pb8Str));
                ByteBuffer bb = ByteBuffer.wrap(pb8, 8, 10);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                short species = bb.getShort();
                System.out.println(species);
                bb = ByteBuffer.wrap(pb8, 140, 144);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                boolean isEgg = (bb.getInt() >> 30 & 0x1) == 1;
                if (isEgg || tradeSpecies.contains(species)) {
                    System.out.println("Egg or trade species offered.");
                    sbc.click("B", 1500);
                    leaveTrade();
                    Thread.sleep(500);
                    //leave union room
                    leaveUnionRoom();
                    return;
                }
                sbc.click("A", 1000);

                while(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1), 16) < nextPhase) {
                    if (checkIfCancelled(currentPhase)) return;
                    if (hasTimedOut()) {
                        System.out.println("Trade timed out.");
                        sbc.click("B", 1500);
                        leaveTrade();
                        Thread.sleep(500);
                        leaveUnionRoom();
                        return;
                    }
                    Thread.sleep(500);
                }
                if(checkIfCancelled(currentPhase)) return;
                currentPhase = nextPhase;
                nextPhase = 9;



                while(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1), 16) < nextPhase) {
                    if (checkIfCancelled(currentPhase)) return;
                    sbc.click("B", 1500);
                }

                while (Integer.parseInt(sbc.peek(pointers.get("tradeFlowState"), 1), 16) == 3) {
                    Thread.sleep(500);
                }
                Thread.sleep(2000);
                leaveTrade();
                Thread.sleep(500);
                leaveUnionRoom();

            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkIfCancelled(int currentPhase) throws InterruptedException, IOException {
        if(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1), 16) < currentPhase) {
            System.out.println("Trade cancelled.");
            Thread.sleep(1000);
            sbc.click("B", 1500);
            leaveTrade();
            Thread.sleep(500);
            leaveUnionRoom();
            return true;
        }
        if(Integer.parseInt(sbc.peek(pointers.get("netTradePhase"), 1), 16) == 0xA) {
            System.out.println("Trade cancelled.");
            Thread.sleep(1000);
            sbc.click("B", 1500);
            Thread.sleep(500);
            leaveUnionRoom();
            return true;
        }
        return false;
    }

    public void leaveTrade() throws InterruptedException {
        sbc.click("B", 1000);
        sbc.click("DUP", 1000);
        sbc.click("A", 1000);
        sbc.click("A", 1000);
    }

    public void leaveUnionRoom() throws InterruptedException {
        sbc.click("B", 1000);
        sbc.click("Y", 1000);
        sbc.click("DDOWN", 1000);
        sbc.click("A", 5000);
    }

    public void runDisc(Map.Entry<String, String> entry) {
        String linkCode = generateLinkCode();
        User user = jda.retrieveUserById(Long.parseUnsignedLong(entry.getValue())).complete();
        if (user != null) {
            user.openPrivateChannel()
                    .queue(s -> s.sendMessage("Waiting in the global room with link code: " + linkCode)
                            .queue());
            startTradeRoute(linkCode);

        }
    }

    public void runTwitch(Map.Entry<String, String> entry) {
        String linkCode = generateLinkCode();
        twitchClient.getChat()
                .sendPrivateMessage(entry.getValue(), "Waiting in the global room with link code: " + linkCode);
        startTradeRoute(linkCode);
    }

    @Override
    public void run() {
        while (!shutdown) {
            if (userList.peek() != null) {
                Map.Entry<String, String> entry = userList.poll();
                if (entry.getKey().equalsIgnoreCase("Discord")) {
                    runDisc(entry);
                } else if (entry.getKey().equalsIgnoreCase("Twitch")) {
                    runTwitch(entry);
                }
            }
        }
    }

    public void shutdown() throws IOException {
        shutdown = true;
        sbc.disconnect();
    }
}
