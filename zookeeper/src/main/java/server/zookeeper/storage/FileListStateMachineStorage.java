package server.zookeeper.storage;

import org.apache.ratis.io.MD5Hash;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.storage.FileInfo;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.SnapshotRetentionPolicy;
import org.apache.ratis.statemachine.StateMachineStorage;
import org.apache.ratis.statemachine.impl.FileListSnapshotInfo;
import org.apache.ratis.statemachine.impl.SingleFileSnapshotInfo;
import org.apache.ratis.util.MD5FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FileListStateMachineStorage implements StateMachineStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FileListStateMachineStorage.class);

    public static final Pattern SNAPSHOT_REGEX = Pattern.compile("snapshot\\.(\\d+)_(\\d+)");

    private volatile File stateMachineDir = null;
    private final AtomicReference<FileListSnapshotInfo> latestSnapshot = new AtomicReference<>();

    public void init(RaftStorage storage) {
        this.stateMachineDir = storage.getStorageDir().getStateMachineDir();
        this.getLatestSnapshot();
    }

    public void format() {
    }

    @Override
    public void cleanupOldSnapshots(SnapshotRetentionPolicy snapshotRetentionPolicy) {
        // TODO
    }

    public File getSnapshotDir(long term, long endIndex) {
        final File dir = Objects.requireNonNull(stateMachineDir, "stateMachineDir == null");
        return new File(dir, getSnapshotDirName(term, endIndex));
    }

    static SingleFileSnapshotInfo findLatestSnapshot(Path dir) throws IOException {
        Iterator<SingleFileSnapshotInfo> i = getSingleFileSnapshotInfos(dir).iterator();
        if (!i.hasNext()) {
            return null;
        } else {
            SingleFileSnapshotInfo latest = i.next();

            while(i.hasNext()) {
                SingleFileSnapshotInfo info = i.next();
                if (info.getIndex() > latest.getIndex()) {
                    latest = info;
                }
            }

            Path path = latest.getFile().getPath();
            MD5Hash md5 = MD5FileUtil.readStoredMd5ForFile(path.toFile());
            FileInfo info = new FileInfo(path, md5);
            return new SingleFileSnapshotInfo(info, latest.getTerm(), latest.getIndex());
        }
    }

    static List<SingleFileSnapshotInfo> getSingleFileSnapshotInfos(Path dir) throws IOException {
        List<SingleFileSnapshotInfo> infos = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for(Path path : stream) {
                Path filename = path.getFileName();
                if (filename != null) {
                    Matcher matcher = SNAPSHOT_REGEX.matcher(filename.toString());
                    if (matcher.matches()) {
                        long term = Long.parseLong(matcher.group(1));
                        long index = Long.parseLong(matcher.group(2));
                        FileInfo fileInfo = new FileInfo(path, null);
                        infos.add(new SingleFileSnapshotInfo(fileInfo, term, index));
                    }
                }
            }
        }

        return infos;
    }

    public FileListSnapshotInfo updateLatestSnapshot(FileListSnapshotInfo info) {
        return latestSnapshot.updateAndGet((previous) -> previous != null && info.getIndex() <= previous.getIndex() ? previous : info);
    }

    public static String getSnapshotDirName(long term, long endIndex) {
        return "snapshot." + term + "_" + endIndex;
    }

    @Override
    public FileListSnapshotInfo getLatestSnapshot() {
        SingleFileSnapshotInfo s = (SingleFileSnapshotInfo)this.latestSnapshot.get();
        return s != null ? s : this.loadLatestSnapshot();
    }

    public FileListSnapshotInfo loadLatestSnapshot() {
        File dir = this.stateMachineDir;
        if (dir == null) {
            return null;
        }

        try {
            // 1) Find the latest snapshot directory
            SingleFileSnapshotInfo parentDir = findLatestSnapshot(dir.toPath());
            if (parentDir == null) {
                return null;
            }

            Path snapDir = parentDir.getFile().getPath();

            List<FileInfo> fileInfos = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(snapDir)) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p)) {
                        MD5Hash md5 = MD5FileUtil.readStoredMd5ForFile(p.toFile());
                        fileInfos.add(new FileInfo(p, md5));
                    }
                }
            }

            TermIndex t = TermIndex.valueOf(parentDir.getTerm(), parentDir.getIndex());
            return updateLatestSnapshot(new FileListSnapshotInfo(fileInfos, t));

        } catch (IOException e) {
            LOG.error("Failed loading snapshot", e);
            return null;
        }
    }
}
