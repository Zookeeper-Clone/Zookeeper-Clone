package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import webserver.zookeeper.zookeeper_webserver.dto.auth.*;

@Service
public class AuthService {

    @Autowired
    private final ZookeeperClient zookeeperClient;

    public AuthService(ZookeeperClient zkClient) {
        this.zookeeperClient = zkClient;
    }

    public AuthResult register(String email, String password) {
        var result = zookeeperClient.register(email, password);
        return toAuthResult(result);
    }

    public AuthResult login(String email, String password) {
        var result = zookeeperClient.login(email, password);
        return toAuthResult(result);
    }

    public AuthResult registerOAuth(String email, String token) {
        var result = zookeeperClient.registerOAuth(email, token);
        return toAuthResult(result);
    }

    public AuthResult loginOAuth(String email, String token) {
        var result = zookeeperClient.loginOAuth(email, token);
        return toAuthResult(result);
    }

    // Helper to wrap ZookeeperClient results
    private AuthResult toAuthResult(ZookeeperClient.AuthenticationResult result) {
        return new AuthResult(
                result.isSuccess(),
                result.getMessage(),
                result.getSessionToken().orElse(null)
        );
    }
}