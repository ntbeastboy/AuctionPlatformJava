package com.auction.service;

import com.auction.exception.InvalidInputException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.UserRepository;

import java.util.UUID;

public class UserService {

    public enum RegisterRole { BIDDER, SELLER }

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String password, RegisterRole role) {
        if (username == null || username.isBlank())
            throw new InvalidInputException("Username cannot be empty.");
        else if(username.contains(" "))
            throw new InvalidInputException("Usernames cannot contain spaces.");
        if (password == null || password.isBlank())
            throw new InvalidInputException("Password cannot be empty.");
        else if(username.contains(" "))
            throw new InvalidInputException("Password cannot contain spaces.");
        if (userRepository.existsByUsername(username))
            throw new InvalidInputException("Username already taken.");

        String id = UUID.randomUUID().toString();
        User user = switch (role) {
            case BIDDER -> new Bidder(id, username, password);
            case SELLER -> new Seller(id, username, password);
        };

        userRepository.save(user);
        return user;
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedActionException("Invalid username or password."));

        if (!user.getPassword().equals(password))
            throw new UnauthorizedActionException("Invalid username or password.");

        return user;
    }
}
