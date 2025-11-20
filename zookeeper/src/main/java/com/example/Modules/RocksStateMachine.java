package com.example.Modules;

import java.util.concurrent.CompletableFuture;

import org.apache.ratis.protocol.Message;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;

public class RocksStateMachine extends BaseStateMachine {

    private final QueryHandler queryHandler = new QueryHandler();

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        String msg = trx.getLogEntry()
                .getStateMachineLogEntry()
                .getLogData()
                .toStringUtf8();

        return CompletableFuture.completedFuture(queryHandler.handleMutation(msg));
    }

    @Override
    public CompletableFuture<Message> query(Message request) {
        String payload = request.getContent().toStringUtf8();
        return CompletableFuture.completedFuture(queryHandler.handleQuery(payload));
    }
}
