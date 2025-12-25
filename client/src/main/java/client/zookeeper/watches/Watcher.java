package client.zookeeper.watches;

import server.zookeeper.proto.query.WatchEvent;

// **
// * Watcher interface for processing watch events.
// * implement this interface and pass it to ZookeeperClient to handle watch notifications.
// * this process method will be called asynchronously when a watch event is received.
// */

public interface Watcher {
    void process(WatchEvent event);
}
