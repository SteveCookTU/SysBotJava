package me.ezpzstreamz.sysbotjava;

import java.util.Map;

public class TradeEntry<K, V> implements Map.Entry<K, V> {

    private final K key;
    private V userID;
    private byte[] tradeBytes;

    public TradeEntry(K key, V userID, byte[] tradeBytes) {
        this.key = key;
        this.userID = userID;
        this.tradeBytes = tradeBytes;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return userID;
    }

    @Override
    public V setValue(V value) {
        V old = userID;
        userID = value;
        return old;
    }

    public byte[] setRequest(byte[] request) {
        byte[]  old = this.tradeBytes;
        this.tradeBytes = request;
        return old;
    }

    public byte[] getRequest() {
        return tradeBytes;
    }

}
