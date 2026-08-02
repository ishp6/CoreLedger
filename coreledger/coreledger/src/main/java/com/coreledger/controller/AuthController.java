package com.coreledger.controller;

import com.coreledger.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal demo login endpoint. In a real deployment this would check
 * credentials against a Users table with BCrypt-hashed passwords -- swapped
 * out here for a single hardcoded demo user so the project stays focused on
 * the ledger/transfer logic, which is the interesting part.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if ("admin".equals(username) && "admin123".equals(password)) {
            String token = jwtService.generateToken(username);
            return ResponseEntity.ok(Map.of("token", token, "expiresInMinutes", 60));
        }
        return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
    }
}
