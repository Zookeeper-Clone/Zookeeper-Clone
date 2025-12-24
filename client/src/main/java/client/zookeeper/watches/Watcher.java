package client.zookeeper.watches;

import server.zookeeper.proto.query.WatchEvent;

public interface Watcher {
    void process(WatchEvent event);
}
