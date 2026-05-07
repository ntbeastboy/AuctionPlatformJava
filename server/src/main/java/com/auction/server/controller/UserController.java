package com.auction.server.controller;

import com.auction.exception.UnauthorizedActionException;
import com.auction.exception.UserNotFoundException;
import com.auction.model.*;
import com.auction.repository.UserRepository;
import com.auction.security.JwtUtil;
import com.auction.service.UserService;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;

public class UserController {

    private final UserRepository userRepo;
    private final UserService userService;

    public UserController(UserRepository userRepo, UserService userService) {
        this.userRepo = userRepo;
        this.userService = userService;
    }

    public void handleLogin(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        User user = userService.login(body.get("username"), body.get("password"));
        String token = JwtUtil.generateToken(user.getId(), roleOf(user));
        ctx.json(userToMapWithToken(user, token));
    }

    public void handleRegister(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String roleStr = body.getOrDefault("role", "BIDDER").toUpperCase();
        UserService.RegisterRole role = UserService.RegisterRole.valueOf(roleStr);
        User user = userService.register(body.get("username"), body.get("password"), role);
        String token = JwtUtil.generateToken(user.getId(), roleOf(user));
        ctx.status(201).json(userToMapWithToken(user, token));
    }

    public void handleGetUser(Context ctx) {
        String id = ctx.pathParam("id");
        User user = userRepo.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        ctx.json(userToMap(user));
    }

    public void handleGetAllUsers(Context ctx) {
        ctx.json(userRepo.findAll().stream().map(this::userToMap).toList());
    }

    public void handleGetUserByUsername(Context ctx) {
        String username = ctx.pathParam("username");
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        ctx.json(userToMap(user));
    }

    public void handleAddBalance(Context ctx) {
        String id = ctx.pathParam("id");
        double amount = Double.parseDouble(ctx.queryParam("amount"));
        User user = userRepo.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        if (user instanceof Bidder b) b.addFunds(amount);
        else if (user instanceof Seller s) s.addFunds(amount);
        else throw new IllegalStateException("Cannot add balance to this user type.");
        userRepo.save(user);
        ctx.json(userToMap(user));
    }

    public void handleDeductBalance(Context ctx) {
        String id = ctx.pathParam("id");
        double amount = Double.parseDouble(ctx.queryParam("amount"));
        User user = userRepo.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        if (user instanceof Bidder b) b.withdraw(amount);
        else if (user instanceof Seller s) s.withdraw(amount);
        else throw new IllegalStateException("Cannot deduct balance from this user type.");
        userRepo.save(user);
        ctx.json(userToMap(user));
    }

    public void handleBanUser(Context ctx) {
        String role = ctx.attribute("role");
        if (!"ADMIN".equals(role))
            throw new UnauthorizedActionException("Only admins can ban users.");
        String id = ctx.pathParam("id");
        User user = userRepo.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        if (user instanceof BannableUser bu) {
            bu.banPermanent();
            userRepo.save(user);
            ctx.json(userToMap(user));
        } else {
            throw new IllegalStateException("This user type cannot be banned.");
        }
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("role", roleOf(user));
        if (user instanceof Bidder b) map.put("balance", b.getBalance());
        if (user instanceof Seller s) map.put("balance", s.getBalance());
        if (user instanceof BannableUser bu) map.put("banned", bu.isBanned());
        return map;
    }

    private Map<String, Object> userToMapWithToken(User user, String token) {
        Map<String, Object> map = userToMap(user);
        map.put("token", token);
        return map;
    }

    private String roleOf(User user) {
        if (user instanceof Admin) return "ADMIN";
        if (user instanceof Seller) return "SELLER";
        return "BIDDER";
    }
}
