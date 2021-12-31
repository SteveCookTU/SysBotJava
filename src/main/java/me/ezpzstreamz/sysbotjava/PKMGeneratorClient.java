package me.ezpzstreamz.sysbotjava;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PKMGeneratorClient {

    protected final String ip;
    protected final int port;
    protected Socket conn;
    protected PrintWriter out;
    protected BufferedReader in;
    protected ExecutorService pool;

    public PKMGeneratorClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.pool = Executors.newCachedThreadPool((r) -> {
            Thread t = new Thread(r);
            t.setDaemon(false);
            return t;
        });
    }

    public void connect() {
        try {
            this.conn = new Socket(this.ip, this.port);
            if (this.conn.isConnected()) {
                System.out.println("Connected");
            }

            this.out = new PrintWriter(this.conn.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(this.conn.getInputStream()));
        } catch (IOException var2) {
            System.out.println("Error connecting to the socket.");
        }
    }

    public void disconnect() throws IOException {
        this.conn.close();
        this.conn = null;
        this.out.close();
        this.out = null;
        this.in.close();
        this.in = null;
    }

    public boolean isConnected() {
        return conn != null && conn.isConnected();
    }

    public CompletableFuture<String> sendShowdownString(String set) {
        return CompletableFuture.runAsync(() -> out.println(set), pool).thenCompose(v -> CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(500);
                return in.readLine();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }, pool));

    }
}
