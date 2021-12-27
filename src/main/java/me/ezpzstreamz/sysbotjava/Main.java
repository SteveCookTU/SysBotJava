package me.ezpzstreamz.sysbotjava;

import java.io.*;

public class Main {

    public static void main(String[] args) throws IOException {
        if(args.length < 2) {
            System.out.println("Mode and token required in launch arguments.");
            return;
        }
        SysBotJava sjb = new SysBotJava(args[0], args[1]);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutting down");
            try {
                sjb.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        sjb.run();


    }
}
