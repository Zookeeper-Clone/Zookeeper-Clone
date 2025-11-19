package com.example;

import java.util.UUID;

import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;

public class Client {
    public static void main(String[] args) {
        String[] ids ={"n1","n2","n3","n4","n5"};
        int[] ports = {6001,6002,6003,6004,6005};
        String groupId = "00000000-0000-0000-0000-000000000001";
        ZookeeperClient client = new ZookeeperClient(ids, ports, groupId);
        System.out.println(client.write("nasr", "1"));
        System.out.println(client.read("nasr"));
        System.out.println(client.write("dee4a", "2"));
        System.out.println(client.readAll());
        System.out.println(client.delete("nasr"));
        System.out.println(client.readAll());
    }
}