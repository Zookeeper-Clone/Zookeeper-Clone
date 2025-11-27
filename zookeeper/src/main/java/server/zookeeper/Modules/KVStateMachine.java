package server.zookeeper.Modules;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.ratis.io.MD5Hash;
import org.apache.ratis.protocol.Message;
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
import org.apache.ratis.util.MD5FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.DB.DataBase;
import server.zookeeper.modules.QueryHandler;
import server.zookeeper.storage.FileListStateMachineStorage;

public class KVStateMachine extends BaseStateMachine {

    private static final Logger log = LoggerFactory.getLogger(KVStateMachine.class);

    private final QueryHandler queryHandler;
    private final DataBase db;
    private final FileListStateMachineStorage storage = new FileListStateMachineStorage();

    public KVStateMachine(DataBase dataBase) {
        this.db = dataBase;
        this.queryHandler = new QueryHandler(dataBase);
    }

    @Override
    public void initialize(RaftServer server, RaftGroupId groupId, RaftStorage raftStorage) throws IOException {
        log.info("initialize method called");
        this.storage.init(raftStorage);

        SnapshotInfo snapshot = storage.getLatestSnapshot();
        log.info("snapshot is null ? {} : ", snapshot == null);

        if (snapshot != null) {
            loadSnapshot(snapshot);
        }
    }

    @Override
    public void reinitialize() throws IOException {
        LOG.info("Reinitializing State Machine from latest snapshot");
        loadSnapshot(storage.getLatestSnapshot());
    }

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
}
