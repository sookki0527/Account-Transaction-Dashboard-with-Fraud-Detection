package org.example.service;

import org.example.dto.RegisterRequest;
import org.example.dto.RoleDto;
import org.example.entity.Role;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.example.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
public class UserService {


    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
 
    private final UserRepository userRepository;
    public UserService(PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       UserRepository userRepository) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
     
        this.userRepository = userRepository;
    }

    public LoginResult login(String username, String password) {
        System.out.println("login with user:" + username);
        User user = userRepository.findByUsername(username);
        List<RoleDto> roles = user.getRoles().stream().map(role -> new RoleDto(role.getName())).toList();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        boolean isAdmin = roles.stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getRoleName()));
        String token = jwtUtil.generateToken(user.getUsername(), roles);
        return new LoginResult(token, isAdmin);
    }


    public void register(RegisterRequest request) {
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(request.getUsername(), encodedPassword, List.of());
        Role role = Role.builder()
                .name("ROLE_USER")
                .user(user)
                .build();
        user.setRoles(List.of(role));
        userRepository.save(user);
    }

    public record LoginResult(String token, boolean isAdmin) {}

    public void sample() throws InterruptedException, IOException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.example.com/data"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("response:" + response);
    }
}
