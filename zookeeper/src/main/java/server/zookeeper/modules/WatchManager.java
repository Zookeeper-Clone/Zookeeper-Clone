package server.zookeeper.modules;

import org.apache.ratis.protocol.Message;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import server.zookeeper.proto.query.QueryResponse;
import server.zookeeper.proto.query.WatchEvent;
import server.zookeeper.proto.query.WatchEventType;
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
        if (ReservedDirectories.isReserved(directory)) {
            String error = ReservedDirectories.getReservedDirectoryError(directory);

            QueryResponse response = QueryResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage(error)
                    .build();

            return CompletableFuture.completedFuture(
                    Message.valueOf(ByteString.copyFrom(response.toByteArray()))
            );
        }
        WatchKey watchKey = new WatchKey(key, directory);
        CompletableFuture<Message> future = new CompletableFuture<>();
        watchMap.computeIfAbsent(watchKey, k -> new CopyOnWriteArrayList<>()).add(future);
        return future;
    }
    public void triggerNotify(String key, String directory, byte[] newData, WatchEventType eventType) {
        WatchKey watchKey = new WatchKey(key, directory);
        List<CompletableFuture<Message>> watchers = watchMap.remove(watchKey);

        if (watchers != null) {
            // Build the structured WatchEvent
            WatchEvent watchEvent = WatchEvent.newBuilder()
                    .setEventType(eventType)
                    .setKey(key)
                    .setColumnFamily(directory)
                    .build();

            // Wrap it in a QueryResponse to match the client's WatcherResult
            QueryResponse response = QueryResponse.newBuilder()
                    .setSuccess(true)
                    .setWatchEvents(watchEvent)
                    .build();

            Message msg = Message.valueOf(ByteString.copyFrom(response.toByteArray()));

            for (CompletableFuture<Message> watcher : watchers) {
                watcher.complete(msg);
            }
        }
    }
}
