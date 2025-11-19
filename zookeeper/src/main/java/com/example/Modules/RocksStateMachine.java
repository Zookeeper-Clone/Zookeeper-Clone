package com.example.Modules;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import org.apache.ratis.protocol.Message;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;

public class RocksStateMachine extends BaseStateMachine{
    private final HashMap<String,String> keyValStore = new HashMap<>();

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx)
    {
       String msg = trx.getLogEntry()
        .getStateMachineLogEntry()
        .getLogData()
        .toStringUtf8();
        String response;
        if (msg.startsWith("PUT "))
        {
            String payload = msg.substring(4);
            String[] parts = payload.split("=",2);
            if(parts.length != 2)
            {
                response = "ERROR INVALID MESSAGE";
            }
            else
            {
                String key = parts[0];
                String value = parts[1];
                if(keyValStore.get(key)!=null)
                    response ="OK ENTRY UPDATED";
                else
                    response ="OK ENTRY ADDED";
                    try {
                    keyValStore.put(key, value);  
                    } catch (Exception e) {
                    response = "ERROR";
                    }
            }
        }
        else if(msg.startsWith("DELETE "))
        {
            String key = msg.substring(7).trim();
            if(keyValStore.remove(key) != null)
            {
                response = "OK ENTRY DELETED";
            }
            else
            {
                response = "ERROR CAN'T DELETE";
            }
        }
        else
        {
            response = "INVALID QUERY";
        }
        return CompletableFuture.completedFuture(Message.valueOf(response));
    }
    @Override
    public CompletableFuture<Message> query(Message request)
    {
        String payload = request.getContent().toStringUtf8();
        String response = "";
        if (payload.equals("READALL"))
        {
            for(String key : keyValStore.keySet())
            {
                response+=key;
                response+=" : ";
                response+= keyValStore.get(key);
                response+="\n";
            }
        }
        else if(payload.startsWith("GET "))
        {
            String key = payload.substring(4).trim();
            if(keyValStore.get(key)!=null)
            {
                response = keyValStore.get(key);
            }
            else
            {
                response = "KEY DOESN'T EXIST";
            }
        }
        else
        {
            response = "INVALID QUERY";
        }
        return CompletableFuture.completedFuture(Message.valueOf(response));
    }
}
