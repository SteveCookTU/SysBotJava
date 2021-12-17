package me.ezpzstreamz.sysbotjava;

public class Main {

    public static void main(String[] args) {
        if(args.length < 1) {
            System.out.println("Token required in launch arguments.");
            return;
        }
        SysBotJava sjb = new SysBotJava(args[0]);
        sjb.run();
    }
}
