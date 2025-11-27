package server.zookeeper.ratis;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
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
        try {
            QueryHandler queryHandler = new QueryHandler(keyValStore);
            QueryHandlerAdapter queryAdapter = new QueryHandlerAdapter(queryHandler);
            this.messageRouter = new MessageRouter(queryAdapter);

            AuthRepository authRepository = new AuthRepository(keyValStore);
            PasswordHasher passwordHasher = PasswordHasher.getInstance();
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList("407408718192.apps.googleusercontent.com"))
                    .build();

            AuthHandler authHandler = new AuthHandler(authRepository, passwordHasher, verifier);

            messageRouter.registerHandler(MessageType.QUERY, queryAdapter);
            messageRouter.registerHandler(MessageType.AUTH, authHandler);
            LOG.info("KVStateMachine initialized with MessageRouter");
        }catch (Exception e){
            LOG.error("Error initialzing KVStateMachine");
            throw new RuntimeException("Error initializing state machine");
        }

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
