package me.ezpzstreamz.sysbotjava.struct;

import java.util.ArrayList;
import java.util.List;

public class TradeQueue {

    private final List<TradeEntry<String, String>> queueList;

    public TradeQueue() {
        queueList = new ArrayList<>();
    }

    public void addToQueue(String mode, String userID, byte[] request) {
        queueList.add(new TradeEntry<>(mode, userID, request));
    }

    public void removeFromQueue(String mode, String userID) {
        synchronized (queueList) {
            for(int i = 0; i < queueList.size(); i++) {
                if(queueList.get(i).getKey().equalsIgnoreCase(mode) && queueList.get(i).getValue().equalsIgnoreCase(userID)) {
                    queueList.remove(i);
                    break;
                }
            }
            queueList.notify();
        }
    }

    public void updateQueueEntry(String mode, String userID, byte[] request) {
        for (TradeEntry<String, String> stringStringTradeEntry : queueList) {
            if (stringStringTradeEntry.getKey().equalsIgnoreCase(mode) &&
                    stringStringTradeEntry.getValue().equalsIgnoreCase(userID)) {
                stringStringTradeEntry.setRequest(request);
                break;
            }
        }
    }

    public boolean isInQueue(String mode, String userID) {
        for (TradeEntry<String, String> stringStringTradeEntry : queueList) {
            if (stringStringTradeEntry.getKey().equalsIgnoreCase(mode) &&
                    stringStringTradeEntry.getValue().equalsIgnoreCase(userID)) {
                return true;
            }
        }
        return false;
    }

    public TradeEntry<String, String> peek() {
        return queueList.size() > 0 ?  queueList.get(0) : null;
    }

    public TradeEntry<String, String> poll() {
        return queueList.size() > 0 ? queueList.remove(0) : null;
    }

    public int getQueuePosition(String mode, String userID) {
        for (int i = 0; i < queueList.size(); i++) {
            if (queueList.get(i).getKey().equalsIgnoreCase(mode) && queueList.get(i).getValue().equalsIgnoreCase(userID))
                return i + 1;
        }
        return -1;
    }

    public int getSize() {
        return queueList.size();
    }

}
