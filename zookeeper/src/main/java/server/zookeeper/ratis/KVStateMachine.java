package server.zookeeper.ratis;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.apache.ratis.io.MD5Hash;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.storage.FileInfo;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.SnapshotInfo;
import org.apache.ratis.statemachine.StateMachineStorage;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.FileListSnapshotInfo;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.apache.ratis.util.MD5FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.DB.AuthRepository;
import server.zookeeper.DB.DataBase;
import server.zookeeper.DB.SessionRepository;
import server.zookeeper.modules.*;
import server.zookeeper.proto.MessageType;
import server.zookeeper.proto.MessageWrapper;
import server.zookeeper.proto.auth.AuthOperationType;
import server.zookeeper.proto.auth.AuthRequest;
import server.zookeeper.storage.FileListStateMachineStorage;
import server.zookeeper.util.PasswordHasher;

public class KVStateMachine extends BaseStateMachine {

    private static final Logger log = LoggerFactory.getLogger(KVStateMachine.class);
    private final MessageRouter messageRouter;
    private final DataBase db;
    private final FileListStateMachineStorage storage = new FileListStateMachineStorage();
    private final SessionCleanupWorker sessionCleanupWorker;


    public KVStateMachine(DataBase keyValStore) {
        try {
            SessionRepository sessionRepository = new SessionRepository(keyValStore);
            SessionManager sessionManager = new SessionManager(sessionRepository);
            QueryHandler queryHandler = new QueryHandler(keyValStore);
            this.messageRouter = new MessageRouter(queryHandler, sessionManager);
            this.db = keyValStore;

            this.sessionCleanupWorker = new SessionCleanupWorker(sessionManager);

            AuthRepository authRepository = new AuthRepository(keyValStore);
            PasswordHasher passwordHasher = PasswordHasher.getInstance();
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList("262245405443-qbbcc9te4oh15fmro35jghko6ho9r9ap.apps.googleusercontent.com"))
                    .build();

            AuthHandler authHandler = new AuthHandler(authRepository, passwordHasher, verifier, sessionManager);
            AuthzHandler authzHandler = new AuthzHandler(sessionRepository, authRepository);

            messageRouter.registerHandler(MessageType.QUERY, queryHandler);
            messageRouter.registerHandler(MessageType.AUTH, authHandler);
            messageRouter.registerHandler(MessageType.PERMISSIONS, authzHandler);
            LOG.info("KVStateMachine initialized with MessageRouter");
        } catch (Exception e) {
            LOG.error("Error initialzing KVStateMachine");
            throw new RuntimeException("Error initializing state machine");
        }

    }

    @Override
    public void initialize(RaftServer server, RaftGroupId groupId, RaftStorage raftStorage) throws IOException {
        log.info("initialize method called");
        this.storage.init(raftStorage);
        this.sessionCleanupWorker.setRaftInfo(server, groupId);
        SnapshotInfo snapshot = storage.getLatestSnapshot();
        log.info("snapshot is null ? {} : ", snapshot == null);

        if (snapshot != null) {
            loadSnapshot(snapshot);
        }
    }

    @Override
    public LeaderEventApi leaderEvent() {
        return new LeaderEventApi() {
            @Override
            public void notifyLeaderReady() {
                LOG.debug("Node is now leader, starting session cleanup worker");
                sessionCleanupWorker.start();
            }

            @Override
            public void notifyNotLeader(Collection<TransactionContext> pendingEntries) throws IOException {
                LOG.debug("Node is no longer leader, stopping session cleanup worker");
                sessionCleanupWorker.close();
            }
        };
    }

    @Override
    public void reinitialize() throws IOException {
        LOG.info("Reinitializing State Machine from latest snapshot");
        loadSnapshot(storage.getLatestSnapshot());
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
    public TransactionContext startTransaction(RaftClientRequest request) throws IOException {
        ByteString content = request.getMessage().getContent();
        try {
            MessageWrapper wrapper = MessageWrapper.parseFrom(content.toByteArray());
            if (wrapper.getType() != MessageType.AUTH) {
                LOG.debug("Non-AUTH message received, passing through unmodified");
                return super.startTransaction(request);
            }

            AuthRequest authRequest = AuthRequest.parseFrom(wrapper.getPayload());
            if (!(authRequest.getOperation() == AuthOperationType.LOGIN ||
                    authRequest.getOperation() == AuthOperationType.LOGIN_OAUTH)) {
                LOG.debug("AUTH operation is not LOGIN, passing through unmodified");
                return super.startTransaction(request);
            }

            // Intercept LOGIN operations to inject a deterministic Session Token
            String preGeneratedToken = UUID.randomUUID().toString();
            LOG.debug("Leader generating session token: {}", preGeneratedToken);

            AuthRequest.Builder authBuilder = authRequest.toBuilder()
                    .setSessionToken(preGeneratedToken);

            MessageWrapper newWrapper = wrapper.toBuilder()
                    .setPayload(authBuilder.build().toByteString())
                    .build();

            return TransactionContext.newBuilder()
                    .setStateMachine(this)
                    .setClientRequest(request)
                    .setLogData(ByteString.copyFrom(newWrapper.toByteArray()))
                    .build();
        } catch (Exception e) {
            LOG.warn("Failed to inspect/modify transaction in startTransaction", e);
        }
        return super.startTransaction(request);
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


    @Override
    public long takeSnapshot() {

        log.info("taking snapshot");
        final TermIndex lastApplied = getLastAppliedTermIndex();
        if (lastApplied.getTerm() <= 0) {
            log.info("term <= 0 return");
            return -1;
        }

        File snapshotDir = storage.getSnapshotDir(lastApplied.getTerm(), lastApplied.getIndex());
        log.info("taking snapshot at {}", snapshotDir.getAbsolutePath());
        db.takeSnapshot(snapshotDir.getAbsolutePath());

        List<FileInfo> fileList = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(snapshotDir.toPath())) {
            for (Path entry : stream) {
                File f = entry.toFile();
                if (f.isFile()) {
                    MD5Hash md5 = MD5FileUtil.computeMd5ForFile(f);
                    fileList.add(new FileInfo(entry.getFileName(), md5));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        FileListSnapshotInfo snapshotInfo = new FileListSnapshotInfo(
                fileList,
                lastApplied.getTerm(),
                lastApplied.getIndex()
        );

        storage.updateLatestSnapshot(snapshotInfo);
        return lastApplied.getIndex();
    }

    public void loadSnapshot(SnapshotInfo snapshotInfo) {
        log.info("loading snapshot in state machine");
        if (snapshotInfo == null) {
            log.info("snapshot info is null");
            return;
        }

        FileListSnapshotInfo fileListInfo = (FileListSnapshotInfo) snapshotInfo;
        Path snapshotDir = fileListInfo.getFiles().get(0).getPath().getParent();
        log.info("loading snapshot from {}", snapshotDir.toAbsolutePath());

        db.loadSnapshot(snapshotDir.toAbsolutePath().toString());

        this.setLastAppliedTermIndex(snapshotInfo.getTermIndex());
    }

    @Override
    public SnapshotInfo getLatestSnapshot() {
        return this.storage.getLatestSnapshot();
    }

    @Override
    public StateMachineStorage getStateMachineStorage() {
        return storage;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (sessionCleanupWorker != null) {
            sessionCleanupWorker.close();
        }
    }
}
