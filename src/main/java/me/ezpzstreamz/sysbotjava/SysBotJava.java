package me.ezpzstreamz.sysbotjava;

import me.ezpzstreamz.sysbotcontroller.SysBotController;
import me.ezpzstreamz.sysbotjava.listeners.SlashCommandListener;
import me.ezpzstreamz.sysbotjava.util.Util;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SysBotJava implements Runnable {

    private final JDA jda;

    private final ArrayDeque<Long> userList;
    private final SysBotController sbc;
    private final Map<String, String> pointers;

    private byte[] pkm;

    private volatile boolean shutdown = false;

    public SysBotJava(String token) {
        SysBotController sbcTemp;
        JDA jdaTemp;
        pointers = new HashMap<>();
        try {
            jdaTemp = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS).addEventListeners(new SlashCommandListener(this)).build();
            sbcTemp = new SysBotController("10.0.0.53", 6000);
            sbcTemp.connect();

            //RegisterSlashCommands.registerSlashCommands(jdaTemp);
            System.out.println(sbcTemp.resolvePointer("[[[[[[main+4e376e8]+18]+b8]+0]+108]+28]+78"));
            System.out.println(sbcTemp.peek("[[[[[[main+4e376e8]+18]+b8]+0]+108]+28]+78", 1));
            pkm = Files.readAllBytes(Paths.get("pkb/pokemon.pb8"));
            try (InputStream resource = SysBotJava.class.getClassLoader().getResourceAsStream("pointers.txt")) {
                assert resource != null;
                List<String> lines = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).lines()
                        .toList();
                for (String s : lines) {
                    String[] args = s.split(":");
                    pointers.put(args[0], args[1]);
                }
            }
        } catch (IOException | InterruptedException | LoginException e) {
            e.printStackTrace();
            jdaTemp = null;
            sbcTemp = null;
        }
        sbc = sbcTemp;
        userList = new ArrayDeque<>();
        userList.add(Long.parseUnsignedLong("152791117488848896"));
        jda = jdaTemp;

    }

    public synchronized void addToQueue(long userID) {
        userList.add(userID);
    }

    public synchronized int getQueueSize() {
        return userList.size();
    }

    public synchronized boolean queueContainsUser(long userID) {
        return userList.contains(userID);
    }

    public synchronized int getQueuePosition(long userID) {
        List<Long> userIDs = userList.stream().toList();
        return userIDs.indexOf(userID);
    }

    public synchronized void removeFromQueue(long userID) {
        userList.removeFirstOccurrence(userID);
    }

    private String generateLinkCode() {
        return String.format("%08d", ThreadLocalRandom.current().nextInt(1, 99999999 + 1));
    }

    @Override
    public void run() {
        while (!shutdown) {
            if (userList.peek() != null) {
                long userID = userList.poll();
                String linkCode = generateLinkCode();
                jda.retrieveUserById(userID).queue(u -> {
                    if (u != null) {
                        u.openPrivateChannel()
                                .queue(s -> s.sendMessage("Waiting in the global room with link code: " + linkCode)
                                        .queue());
                        try {
                            //Join union room
                            sbc.click("Y", 1000);
                            sbc.click("DRIGHT", 1000);
                            sbc.click("A", 1000);
                            sbc.click("A", 1000);
                            sbc.click("A", 1000);
                            sbc.click("DDOWN", 500);
                            sbc.click("DDOWN", 500);
                            sbc.click("A", 1500);
                            sbc.click("A", 1000);

                            //waitForConnection
                            Thread.sleep(4000);

                            sbc.click("A", 1000);
                            sbc.click("A", 1000);
                            sbc.click("A", 8000);

                            System.out.println("Link Code: " + linkCode);
                            sbc.enterCode(linkCode);
                            Thread.sleep(3000);

                            sbc.click("PLUS", 1500);
                            sbc.click("A", 8000);
                            sbc.moveForward(500);
                            Thread.sleep(8000);

                            //Start and wait for trade
                            sbc.click("Y", 1000);
                            sbc.click("A", 1000);
                            sbc.click("DDOWN", 1000);
                            sbc.click("A", 1000);

                            sbc.poke(pointers.get("b1s1"), "0x" + Util.bytesToHex(Util.encryptPb8(pkm)));

                            Thread.sleep(600);

                            while (Integer.parseInt(sbc.peek(pointers.get("onlineState"), 1), 16) != 8) {
                                Thread.sleep(1000);
                            }


                            sbc.click("A", 1000);
                            sbc.click("A", 1000);
                            sbc.click("A", 1000);

                            Thread.sleep(500);
                            if (Integer.parseInt(sbc.peek(pointers.get("onlineState"), 1), 16) == 18) {
                                Thread.sleep(1000);

                                while (Integer.parseInt(sbc.peek(pointers.get("isReceived"), 1), 16) != 1) {
                                    Thread.sleep(500);
                                }
                                sbc.click("A", 1000);
                                sbc.click("A", 1000);

                                sbc.click("A", 300);
                                sbc.click("A", 3000);
                                sbc.click("A", 3000);
                                sbc.click("A", 4000);

                                while(Integer.parseInt(sbc.peek(pointers.get("tradeFlowState"), 1), 16) == 3) {
                                    Thread.sleep(500);
                                }
                                Thread.sleep(2000);
                                sbc.click("B", 1000);
                                sbc.click("DUP", 1000);
                                sbc.click("A", 1000);
                                sbc.click("A", 1000);

                                //leave union room
                                sbc.click("B", 1000);
                                sbc.click("Y", 1000);
                                sbc.click("DDOWN", 1000);
                                sbc.click("A", 5000);

                            }
                        } catch (InterruptedException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }

    public void shutdown() {
        shutdown = true;
    }
}
