package webserver.zookeeper.zookeeper_webserver.configuration;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import client.zookeeper.ZookeeperClient;
import jakarta.servlet.http.Cookie;

@Configuration
public class SecurityConfig {

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;
    @Autowired
    private ZookeeperClient zookeeperClient;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(c -> c.configurationSource(corsConfig()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .successHandler((request, response, authentication) -> {

                            System.out.println("Here");

                            // Cast to OAuth2AuthenticationToken
                            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

                            // Cast principal to OidcUser to get the ID token
                            OidcUser oidcUser = (OidcUser) oauthToken.getPrincipal();

                            String email = oidcUser.getEmail();
                            String idToken = oidcUser.getIdToken().getTokenValue();
                            System.out.println(idToken);
                            var result = zookeeperClient.loginOAuth(email, idToken);
                            if (!result.isSuccess()) {
                                result = zookeeperClient.registerOAuth(email, idToken);
                            }

                            String sessionToken = result.getSessionToken().orElse("");

                            Cookie cookie = new Cookie("SESSION_TOKEN",
                                    sessionToken);
                            cookie.setHttpOnly(true);
                            cookie.setPath("/"); // send cookie for all endpoints
                            cookie.setMaxAge(60 * 60); // 1 hour expiration
                            response.addCookie(cookie);

                            // Redirect to React frontend
                            response.sendRedirect("http://localhost:3000/");
                        }));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfig() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
