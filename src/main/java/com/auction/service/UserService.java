package AuctionPlatformJava.src.main.java.com.auction.service;

import AuctionPlatformJava.src.main.java.com.auction.exception.InvalidInputException;
import AuctionPlatformJava.src.main.java.com.auction.exception.UnauthorizedActionException;
import AuctionPlatformJava.src.main.java.com.auction.model.Bidder;
import AuctionPlatformJava.src.main.java.com.auction.model.Seller;
import AuctionPlatformJava.src.main.java.com.auction.model.User;
import AuctionPlatformJava.src.main.java.com.auction.repository.UserRepository;

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
        if (password == null || password.isBlank())
            throw new InvalidInputException("Password cannot be empty.");
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
