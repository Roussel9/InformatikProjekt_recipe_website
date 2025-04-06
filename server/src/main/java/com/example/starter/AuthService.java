package com.example.starter;



import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuthService {
  private final Vertx vertx;
  private final JDBCClient dbClient;
  private final JDBCAuth authProvider;

  private String activeSessionId = UUID.randomUUID().toString();
  private int failedAttempts = 0;

  public AuthService(Vertx vertx, JDBCClient dbClient) {
    this.vertx = vertx;
    this.dbClient = dbClient;
    this.authProvider = JDBCAuth.create(vertx, dbClient);
  }


  public String hashPassword(String password) {
    return authProvider.computeHash(password, null);
  }


  /**
   * @api {post} /register Registriert einen neuen Benutzer
   * @apiName RegisterUser
   * @apiGroup Authentication
   *
   * @apiDescription Diese Route registriert einen neuen Benutzer in der Datenbank. Es werden Name, E-Mail und Passwort benötigt.
   *
   * @apiParam {String} name Der Name des Benutzers.
   * @apiParam {String} email Die E-Mail-Adresse des Benutzers.
   * @apiParam {String} password Das Passwort des Benutzers.
   *
   * @apiSuccess {Redirect} 303 Redirect zur Login-Seite nach erfolgreicher Registrierung.
   *
   * @apiError (Bad Request 400) BadRequest Es wurden nicht alle erforderlichen Felder übermittelt.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während der Benutzer registriert wurde.
   */
  public void registerUser(RoutingContext context){
    String name = context.request().getFormAttribute("name");
    String email = context.request().getFormAttribute("email");
    String password = context.request().getFormAttribute("password");

    if (name == null || email == null || password == null) {
      context.response().setStatusCode(400).end("⚠ Alle Felder sind erforderlich!");
      return;
    }

    String hashedPassword = authProvider.computeHash(password, null);
    String query = "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)";

    dbClient.updateWithParams(query, new JsonArray().add(name).add(email).add(hashedPassword), res -> {
      if (res.succeeded()) {
        context.response()
                .setStatusCode(303)
                .putHeader("Location", "/login.html") // Umleitung zur Login-Seite nach Registrierung
                .end();
        System.out.println("okay");

      } else {
        System.out.println("nicht gut ");

        context.response().setStatusCode(500).end("❌ Fehler bei Registrierung");
      }
    });
  }



  /**
   * @api {post} /login Meldet einen Benutzer an
   * @apiName LoginUser
   * @apiGroup Authentication
   *
   * @apiDescription Diese Route meldet einen Benutzer an. Es werden E-Mail und Passwort benötigt. Bei erfolgreicher Anmeldung wird ein Session-Cookie gesetzt und der Benutzer zur Startseite weitergeleitet.
   *
   * @apiParam {String} email Die E-Mail-Adresse des Benutzers.
   * @apiParam {String} password Das Passwort des Benutzers.
   *
   * @apiSuccess {Redirect} 303 Redirect zur Startseite nach erfolgreicher Anmeldung.
   * @apiSuccess {Cookie} session-id Ein Session-Cookie, das die Sitzung des Benutzers verwaltet.
   *
   * @apiError (Bad Request 400) BadRequest Es wurden nicht alle erforderlichen Felder übermittelt.
   * @apiError (Unauthorized 401) Unauthorized Das Passwort ist falsch.
   * @apiError (Not Found 404) NotFound Der Benutzer wurde nicht gefunden.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während der Benutzer angemeldet wurde.
   */
  public void loginUser(RoutingContext context, JWTAuth jwtAuth) {
    String email = context.request().getFormAttribute("email");
    String password = context.request().getFormAttribute("password");

    if (email == null || password == null) {
      context.response().setStatusCode(400).end("⚠ Email und Passwort sind erforderlich!");
      return;
    }

    String query = "SELECT id, password_hash FROM users WHERE email = ?";

    dbClient.queryWithParams(query, new JsonArray().add(email), res -> {
      if (res.failed()) {
        context.response().setStatusCode(500).end("❌ Datenbankfehler!");
        return;
      }

      if (res.result().getRows().isEmpty()) {
        context.response().setStatusCode(404).end("❌ Benutzer nicht gefunden!");
        return;
      }

      JsonObject user = res.result().getRows().get(0);
      int userId = user.getInteger("id");
      String storedHash = user.getString("password_hash");

      if (authProvider.computeHash(password, null).equals(storedHash)) {
        failedAttempts = 0; // Erfolgreicher Login -> Zurücksetzen der fehlgeschlagenen Versuche

        // Neue Session-ID generieren
        String newSessionId = UUID.randomUUID().toString();

        // Session in die Datenbank speichern
        String insertSessionQuery = "INSERT INTO sessions (session_id, user_id) VALUES (?, ?)";

        dbClient.updateWithParams(insertSessionQuery, new JsonArray().add(newSessionId).add(userId), sessionRes -> {
          if (sessionRes.succeeded()) {
            // Session-Cookie setzen
            context.addCookie(Cookie.cookie("session-id", newSessionId)
              .setHttpOnly(true)
              .setSameSite(CookieSameSite.LAX));


            // Weiterleitung zur Startseite
            context.response()
              .setStatusCode(303)
              .putHeader("Location", "/homePage.html")
              .end();
          } else {
            context.response().setStatusCode(500).end("❌ Fehler beim Speichern der Session!");
          }
        });
      } else {
        failedAttempts++;
        context.response().setStatusCode(401).end("❌ Falsches Passwort!");
      }
    });
  }



  public void getUserIdFromSession(RoutingContext context, Handler<AsyncResult<Integer>> resultHandler) {
    String sessionId = context.getCookie("session-id") != null ? context.getCookie("session-id").getValue() : null;

    if (sessionId == null) {
      resultHandler.handle(Future.succeededFuture(-1));
      return;
    }

    String query = "SELECT user_id FROM sessions WHERE session_id = ?";

    dbClient.queryWithParams(query, new JsonArray().add(sessionId), res -> {
      if (res.failed() || res.result().getRows().isEmpty()) {
        resultHandler.handle(Future.succeededFuture(-1));
      } else {
        int userId = res.result().getRows().get(0).getInteger("user_id");
        resultHandler.handle(Future.succeededFuture(userId));
      }
    });
  }


  public void getProfile(RoutingContext context) {
    getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        context.response().setStatusCode(401).end("⚠ Nicht autorisiert! Bitte einloggen.");
        return;
      }

      int userId = userIdResult.result();
      String query = "SELECT title, description, portions, image_url FROM recipes WHERE user_id = ?";
      //String query = "INSERT INTO recipes (user_id, , created_at) VALUES (?, ?, ?, ?, ?, NOW())";

      dbClient.queryWithParams(query, new JsonArray().add(userId), res -> {
        if (res.succeeded()) {
          JsonArray recipes = new JsonArray(res.result().getRows());
          context.response().putHeader("Content-Type", "application/json").end(recipes.encode());
        } else {
          context.response().setStatusCode(500).end("❌ Fehler beim Abrufen der Rezepte");
        }
      });
    });
  }




  /**
   * @api {post} /logout Meldet einen Benutzer ab
   * @apiName LogoutUser
   * @apiGroup Authentication
   *
   * @apiDescription Diese Route meldet einen Benutzer ab, indem das Session-Cookie gelöscht wird. Der Benutzer wird anschließend zur Login-Seite weitergeleitet.
   *
   * @apiSuccess {Redirect} 303 Redirect zur Login-Seite nach erfolgreichem Logout.
   * @apiSuccess {Cookie} session-id Das Session-Cookie wird gelöscht.
   *
   * @apiError (Unauthorized 401) Unauthorized Kein aktiver Login gefunden (kein Session-Cookie vorhanden).
   */
  public void logoutUser(RoutingContext context) {
    Cookie authCookie = context.getCookie("session-id");

    if (authCookie == null) {
      // Kein Token vorhanden, daher kein Logout nötig
      context.response().setStatusCode(401).end("❌ Kein aktiver Login gefunden.");
      return;
    }

    // Lösche das Authentifizierungs-Cookie
    context.response().addCookie(Cookie.cookie("session-id", "")
      .setHttpOnly(true)
      .setSecure(false)  // Falls du HTTPS nutzt, setze `true`
      .setSameSite(CookieSameSite.LAX)
      .setPath("/")
      .setMaxAge(0)); // Sofort löschen

    // Setze die Session-ID zurück
    activeSessionId = UUID.randomUUID().toString();

    // Weiterleitung zur Login-Seite nach dem Logout
    context.response().setStatusCode(303)
      .putHeader("Location", "/login.html")
      .end("erfolgreich ausgeloggt");
  }


}



