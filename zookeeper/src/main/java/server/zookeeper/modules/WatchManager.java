package server.zookeeper.modules;

import org.apache.ratis.protocol.Message;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import server.zookeeper.records.WatchKey;
import server.zookeeper.util.ReservedDirectories;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class WatchManager {
    private final Map<WatchKey, List<CompletableFuture<Message>>> watchMap = new ConcurrentHashMap<>();

    public CompletableFuture<Message> addWatch(String key, String directory){
        if(ReservedDirectories.isReserved(directory)){
            String error = ReservedDirectories.getReservedDirectoryError(directory);
            return CompletableFuture.completedFuture(Message.valueOf(error));
        }
        WatchKey watchKey = new WatchKey(key, directory);
        CompletableFuture<Message> future = new CompletableFuture<>();
        watchMap.computeIfAbsent(watchKey, k -> new CopyOnWriteArrayList<>()).add(future);
        return future;
    }
    public void triggerNotify(String key, String directory, byte[] newData){
        WatchKey watchKey = new WatchKey(key, directory);
        List<CompletableFuture<Message>> watchers = watchMap.remove(watchKey);
        if (watchers != null){
            Message msg = Message.valueOf(ByteString.copyFrom(newData));
            for (CompletableFuture<Message> watcher : watchers){
                watcher.complete(msg);
            }
        }
    }
}
