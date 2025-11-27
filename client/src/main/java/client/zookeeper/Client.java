package client.zookeeper;


import org.apache.ratis.client.RaftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    public static void main(String[] args) {
        String[] ids ={"n1","n2","n3","n4","n5"};
        int[] ports = {6001,6002,6003,6004,6005};
        String groupId = "00000000-0000-0000-0000-000000000001";
        RaftClient raftClient = new RaftClientBuilder()
                                    .setPeers(ids, ports)
                                    .setGroupId(groupId)
                                    .build();
        ZookeeperClient client = new ZookeeperClient(raftClient);
        // TODO: move these to proper E2E tests
//       System.out.println(client.write("sabry", "aw3a tktb comment"));
//       System.out.println(client.write("hutho", "mlk4 d3wa"));
//       System.out.println(client.write("nasr", "gamed 3ady"));
//       System.out.println(client.write("nasr", "gamed", "column 1"));
//       System.out.println(client.write("nasr", "gamed awy", "column 2"));
////        System.out.println(client.read("nasr"));
////        System.out.println(client.write("dee4a", "2"));
////        System.out.println(client.read("nasr"));
////        System.out.println(client.read("dee4a"));
////        System.out.println(client.delete("nasr"));
//        System.out.println(client.read("sabry"));
//        System.out.println(client.read("hutho"));
//        System.out.println(client.read("nasr"));
//        System.out.println(client.read("nasr","column 1"));
//        System.out.println(client.read("nasr","column 2"));

        // ============================================================================
        // Test 1: User Registration
        // ============================================================================
        System.out.println("TEST 1: User Registration");
        System.out.println("-".repeat(80));

        ZookeeperClient.AuthenticationResult registerResult1 = client.register("alice@example.com", "password123");
        System.out.println("Register alice@example.com: " + registerResult1);
        System.out.println("✓ Registration " + (registerResult1.isSuccess() ? "succeeded" : "failed"));

        // Test duplicate registration
        ZookeeperClient.AuthenticationResult duplicateRegister = client.register("alice@example.com", "differentpass");
        System.out.println("\nAttempt duplicate registration: " + duplicateRegister);
        System.out.println("✓ Duplicate registration properly " + (duplicateRegister.isSuccess() ? "allowed (unexpected!)" : "rejected"));
        System.out.println();


        // ============================================================================
        // Test 2: User Login
        // ============================================================================
        System.out.println("TEST 2: User Login");
        System.out.println("-".repeat(80));

        // Successful login
        ZookeeperClient.AuthenticationResult loginResult = client.login("alice@example.com", "password123");
        System.out.println("Login alice@example.com: " + loginResult);
        System.out.println("Authenticated: " + client.isAuthenticated());
        if (client.getSessionToken().isPresent()) {
            System.out.println("Session Token: " + client.getSessionToken().get().substring(0, 20) + "...");
        }

        // Failed login - wrong password
        ZookeeperClient client2 = new ZookeeperClient(raftClient);
        ZookeeperClient.AuthenticationResult wrongPasswordLogin = client2.login("alice@example.com", "wrongpassword");
        System.out.println("\nLogin with wrong password: " + wrongPasswordLogin);
        System.out.println("✓ Wrong password properly " + (wrongPasswordLogin.isSuccess() ? "accepted (BUG!)" : "rejected"));

        // Failed login - non-existent user
        ZookeeperClient.AuthenticationResult nonExistentLogin = client2.login("charlie@example.com", "anypassword");
        System.out.println("\nLogin non-existent user: " + nonExistentLogin);
        System.out.println("✓ Non-existent user properly " + (nonExistentLogin.isSuccess() ? "accepted (BUG!)" : "rejected"));
        System.out.println();
        // ============================================================================
        // Test 3: OAuth User Registration
        // ============================================================================
        ZookeeperClient client3 = new ZookeeperClient(raftClient);
        System.out.println("TEST 3: User Registration");
        System.out.println("-".repeat(80));

        String token = "";
        String email = "abdoahlawy6161@gmail.com";
        ZookeeperClient.AuthenticationResult registerResult2 = client3.registerOAuth(email,token);
        System.out.println("Register abdoahlawy6161@gmail.com: " + registerResult2);
        System.out.println("✓ Registration " + (registerResult2.isSuccess() ? "succeeded" : "failed"));

        // Test duplicate registration
        ZookeeperClient.AuthenticationResult OAuthduplicateRegister = client3.registerOAuth(email,token);
        System.out.println("\nAttempt duplicate registration: " + OAuthduplicateRegister);
        System.out.println("✓ Duplicate registration properly " + (OAuthduplicateRegister.isSuccess() ? "allowed (unexpected!)" : "rejected"));
        System.out.println();
        // ============================================================================
        // Test 4: OAuth User Login
        // ============================================================================
        System.out.println("TEST 2: User Login");
        System.out.println("-".repeat(80));
        // Successful login
        ZookeeperClient.AuthenticationResult loginResult2 = client3.loginOAuth(email,token);
        System.out.println("Login alice@example.com: " + loginResult2);
        System.out.println("Authenticated: " + client3.isAuthenticated());
        if (client3.getSessionToken().isPresent()) {
            System.out.println("Session Token: " + client3.getSessionToken().get().substring(0, 20) + "...");
        }

        // Failed login - wrong token
        ZookeeperClient client4 = new ZookeeperClient(raftClient);
        ZookeeperClient.AuthenticationResult wrongTokenLogin = client4.loginOAuth(email, "wrong token");
        System.out.println("\nLogin with wrong token: " + wrongTokenLogin);
        System.out.println("✓ Wrong token properly " + (wrongTokenLogin.isSuccess() ? "accepted (BUG!)" : "rejected"));

        // Failed login - non-existent user
        ZookeeperClient.AuthenticationResult nonExistentLogin2 = client4.loginOAuth("charlie@example.com", "anypassword");
        System.out.println("\nLogin non-existent user: " + nonExistentLogin2);
        System.out.println("✓ Non-existent user properly " + (nonExistentLogin2.isSuccess() ? "accepted (BUG!)" : "rejected"));
        System.out.println();

    }
}
