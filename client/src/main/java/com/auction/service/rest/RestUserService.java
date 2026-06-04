package com.auction.service.rest;

import com.auction.exception.UnauthorizedActionException;
import com.auction.exception.UserNotFoundException;
import com.auction.model.User;
import com.auction.repository.UserRepository;
import com.auction.service.UserService;
import com.auction.service.http.HttpClientService;
import com.auction.service.http.JsonMappers;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Network-backed UserService. login/register hit the REST API and pick up the JWT bearer token for
 * subsequent requests; balance helpers post to /users/{id}/balance/{add,deduct} and return the
 * refreshed user.
 *
 * <p>Inherits from UserService only so it slots into the existing AppState without any controller
 * changes — none of the inherited methods are actually invoked because every method below is
 * overridden or new.
 */
public class RestUserService extends UserService {

  private static final Type MAP = new TypeToken<Map<String, Object>>() {}.getType();

  private final HttpClientService http;

  public RestUserService(UserRepository userRepository, HttpClientService http) {
    super(userRepository);
    this.http = http;
  }

  @Override
  public User register(String username, String password, RegisterRole role) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("username", username);
    body.put("password", password);
    body.put("role", role.name());
    return doAuth("/users/register", body, "Registration failed");
  }

  @Override
  public User login(String username, String password) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("username", username);
    body.put("password", password);
    return doAuth("/users/login", body, "Invalid username or password.");
  }

  private User doAuth(String endpoint, Map<String, String> body, String genericError) {
    try {
      String json = http.getGson().toJson(body);
      String response = http.post(endpoint, json);
      Map<String, Object> map = http.getGson().fromJson(response, MAP);
      Object token = map.get("token");
      if (token != null) http.setAuthToken(token.toString());
      return JsonMappers.toUser(map);
    } catch (IOException e) {
      // Surface the server's error message when present, otherwise fall back
      // to the generic one so the UI sees something sensible.
      String msg = e.getMessage();
      throw new UnauthorizedActionException(msg != null && !msg.isBlank() ? msg : genericError);
    }
  }

  /**
   * Adds funds via the server and returns the refreshed user (with the canonical balance from the
   * database).
   */
  public User addBalance(String userId, double amount) {
    try {
      String response = http.post("/users/" + userId + "/balance/add?amount=" + amount, "{}");
      Map<String, Object> map = http.getGson().fromJson(response, MAP);
      return JsonMappers.toUser(map);
    } catch (IOException e) {
      throw new UserNotFoundException(extract(e));
    }
  }

  public User deductBalance(String userId, double amount) {
    try {
      String response = http.post("/users/" + userId + "/balance/deduct?amount=" + amount, "{}");
      Map<String, Object> map = http.getGson().fromJson(response, MAP);
      return JsonMappers.toUser(map);
    } catch (IOException e) {
      throw new IllegalArgumentException(extract(e));
    }
  }

  /** Drop the JWT bearer token, e.g. on logout. */
  public void logout() {
    http.setAuthToken(null);
  }

  /** Re-fetch the user from the server (e.g. after a bid settles). */
  public User refresh(String userId) {
    try {
      String response = http.get("/users/" + userId);
      Map<String, Object> map = http.getGson().fromJson(response, MAP);
      return JsonMappers.toUser(map);
    } catch (IOException e) {
      throw new UserNotFoundException(extract(e));
    }
  }

  public List<User> findAllUsers() {
    try {
      Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
      String response = http.get("/users");
      List<Map<String, Object>> users = http.getGson().fromJson(response, listType);
      return users.stream().map(JsonMappers::toUser).toList();
    } catch (IOException e) {
      throw new UnauthorizedActionException(extract(e));
    }
  }

  public User banUser(String userId, long durationSeconds) {
    return banUser(userId, durationSeconds, false);
  }

  public User banUser(String userId, long durationSeconds, boolean permanent) {
    try {
      String url = "/users/" + userId + "/ban?permanent=" + permanent;
      if (!permanent) {
        url += "&durationSeconds=" + durationSeconds;
      }
      String response = http.post(url, "{}");
      Map<String, Object> map = http.getGson().fromJson(response, MAP);
      return JsonMappers.toUser(map);
    } catch (IOException e) {
      throw new UnauthorizedActionException(extract(e));
    }
  }

  public User unbanUser(String userId) {
    try {
      String response = http.post("/users/" + userId + "/unban", "{}");
      Map<String, Object> map = http.getGson().fromJson(response, MAP);
      return JsonMappers.toUser(map);
    } catch (IOException e) {
      throw new UnauthorizedActionException(extract(e));
    }
  }

  public User changeUsername(String userId, String username) {
    try {
      Map<String, String> body = new LinkedHashMap<>();
      body.put("username", username);
      String response = http.put("/users/" + userId + "/username", http.getGson().toJson(body));
      Map<String, Object> map = http.getGson().fromJson(response, MAP);
      return JsonMappers.toUser(map);
    } catch (IOException e) {
      throw new IllegalArgumentException(extract(e));
    }
  }

  public User changePassword(String userId, String password) {
    try {
      Map<String, String> body = new LinkedHashMap<>();
      body.put("password", password);
      String response = http.put("/users/" + userId + "/password", http.getGson().toJson(body));
      Map<String, Object> map = http.getGson().fromJson(response, MAP);
      return JsonMappers.toUser(map);
    } catch (IOException e) {
      throw new IllegalArgumentException(extract(e));
    }
  }

  public void deleteUser(String userId) {
    try {
      http.delete("/users/" + userId);
    } catch (IOException e) {
      throw new UnauthorizedActionException(extract(e));
    }
  }

  private static String extract(IOException e) {
    String msg = e.getMessage();
    return msg == null ? "Server error" : msg;
  }
}
