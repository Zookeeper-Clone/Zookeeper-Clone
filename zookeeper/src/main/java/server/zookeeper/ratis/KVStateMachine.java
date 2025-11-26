package server.zookeeper.ratis;

import java.util.concurrent.CompletableFuture;

import org.apache.ratis.protocol.Message;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import server.zookeeper.DB.AuthRepository;
import server.zookeeper.DB.DataBase;
import server.zookeeper.modules.AuthHandler;
import server.zookeeper.modules.MessageRouter;
import server.zookeeper.modules.QueryHandler;
import server.zookeeper.modules.QueryHandlerAdapter;
import server.zookeeper.proto.MessageType;
import server.zookeeper.util.PasswordHasher;

public class KVStateMachine extends BaseStateMachine {

    private final MessageRouter messageRouter;

    public KVStateMachine(DataBase keyValStore) {
        QueryHandler queryHandler = new QueryHandler(keyValStore);
        QueryHandlerAdapter queryAdapter = new QueryHandlerAdapter(queryHandler);
        this.messageRouter = new MessageRouter(queryAdapter);

        AuthRepository authRepository = new AuthRepository(keyValStore);
        PasswordHasher passwordHasher = PasswordHasher.getInstance();
        AuthHandler authHandler = new AuthHandler(authRepository, passwordHasher);

        messageRouter.registerHandler(MessageType.QUERY, queryAdapter);
        messageRouter.registerHandler(MessageType.AUTH, authHandler);
        LOG.info("KVStateMachine initialized with MessageRouter");
    }

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        try {
            byte[] payload = trx.getLogEntry()
                    .getStateMachineLogEntry()
                    .getLogData()
                    .toByteArray();

            LOG.debug("Applying transaction with payload size: {} bytes", payload.length);

            Message response = messageRouter.route(payload, true);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            LOG.error("Error applying transaction", e);
            Message errorMsg = Message.valueOf("ERROR: " + e.getMessage());
            return CompletableFuture.completedFuture(errorMsg);
        }
    }

    @Override
    public CompletableFuture<Message> query(Message request) {
        try {
            byte[] payload = request.getContent().toByteArray();

            LOG.debug("Processing query with payload size: {} bytes", payload.length);

            Message response = messageRouter.route(payload, false);
            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            LOG.error("Error processing query", e);
            Message errorMsg = Message.valueOf("ERROR: " + e.getMessage());
            return CompletableFuture.completedFuture(errorMsg);
        }
    }
}
