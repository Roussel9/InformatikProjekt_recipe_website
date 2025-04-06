package com.example.starter;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.RoutingContext;
import org.mindrot.jbcrypt.BCrypt;
import java.util.List;


public class UserService {
  private final JDBCClient dbClient;
  //private AuthService authService;
  public UserService(Vertx vertx, JDBCClient dbClient) {
    //this.authService = authService;
    this.dbClient = dbClient;
  }


  /**
   * @api {get} /users/search Sucht Benutzer anhand des Namens
   * @apiName GetUserByName
   * @apiGroup Users
   *
   * @apiDescription Diese Route sucht Benutzer anhand des Namens und gibt eine HTML-Seite mit den Suchergebnissen zurück.
   *
   * @apiParam {String} name Der Name des Benutzers, nach dem gesucht werden soll.
   *
   * @apiSuccess {HTML} page Eine HTML-Seite mit den Suchergebnissen.
   *
   * @apiError (Bad Request 400) BadRequest Es wurde kein Name angegeben.
   * @apiError (Not Found 404) NotFound Es wurden keine Benutzer mit dem angegebenen Namen gefunden.
   */
  public void getUserByName(RoutingContext context) {
    String username = context.request().getParam("name");
    if (username == null || username.trim().isEmpty()) {
      context.response().setStatusCode(400).end("⚠ Bitte einen Nutzernamen angeben!");
      return;
    }

    String query = "SELECT id, name, email FROM users WHERE name = ?";

    dbClient.queryWithParams(query, new JsonArray().add(username), res -> {
      if (res.succeeded() && res.result().getRows().size() > 0) {
        List<JsonObject> users = res.result().getRows();

        // HTML-Kopf
        StringBuilder htmlResponse = new StringBuilder("<!DOCTYPE html>");
        htmlResponse.append("<html lang='de'><head>")
          .append("<meta charset='UTF-8'>")
          .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
          .append("<title>Suchergebnis</title>")
          .append("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>")
          .append("<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/bootstrap-icons/font/bootstrap-icons.css'>")
          .append("<link rel='stylesheet' href='favorite.css'>")
          .append("</head><body>");

        // Navigation
        htmlResponse.append("<nav class='navbar navbar-expand-lg navbar-dark bg-success shadow'>")
          .append("<div class='container'>")
          .append("<a class='navbar-brand' href='#'><i class='bi bi-egg-fried'></i> Rezeptseite</a>")
          .append("<button class='navbar-toggler' type='button' data-bs-toggle='collapse' data-bs-target='#navbarNav'>")
          .append("<span class='navbar-toggler-icon'></span></button>")
          .append("<div class='collapse navbar-collapse' id='navbarNav'>")
          .append("<ul class='navbar-nav ms-auto'>")
          .append("<li class='nav-item'><a class='nav-link' href='/homePage.html'><i class='bi bi-house-door'></i> Home</a></li>")
          .append("<li class='nav-item'><a class='nav-link' href='/favorite.html'><i class='bi bi-heart-fill'></i> Favoriten</a></li>")
          .append("<li class='nav-item'><a class='nav-link active' href='/nutzer.html'><i class='bi bi-person-circle'></i> andere Nutzer ansehen</a></li>")
          .append("<li class='nav-item'><a class='nav-link' href='#'><i class='bi bi-box-arrow-right'></i> Abmelden</a></li>")
          .append("</ul></div></div></nav>");

        // Ergebnis-Container
        htmlResponse.append("<div class='container mt-5'>")
          .append("<h2 class='text-success text-center mb-4'><i class='bi bi-person-circle'></i> Suchergebnis</h2>");

        // Nutzerliste erstellen
        for (JsonObject user : users) {
          htmlResponse.append("<div class='card shadow mb-3'>")
            .append("<div class='card-body'>")
            .append("<h5 class='card-title'><i class='bi bi-person'></i> ").append(user.getString("name")).append("</h5>")
            .append("<p class='card-text'><i class='bi bi-envelope'></i> ").append(user.getString("email")).append("</p>")
            .append("<a href='/users/").append(user.getInteger("id")).append("/recipes' class='btn btn-success'><i class='bi bi-book'></i> Alle Rezepte ansehen</a>")

            .append("</div></div>");
        }

        // Footer und Skripte
        htmlResponse.append("</div>")
          .append("<footer class='bg-dark text-white text-center py-4 mt-5 shadow'>")
          .append("<div class='container'>")
          .append("<p>&copy; 2025 Rezeptseite. Alle Rechte vorbehalten.</p>")
          .append("<p><a href='#' class='text-white'>Impressum</a> | <a href='#' class='text-white'>Datenschutz</a> | <a href='#' class='text-white'>AGB</a></p>")
          .append("</div></footer>")
          .append("<script src='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js'></script>")
          .append("</body></html>");

        context.response().putHeader("Content-Type", "text/html").end(htmlResponse.toString());

      } else {
        context.response().setStatusCode(404).end("❌ Nutzer nicht gefunden!");
      }
    });
  }


  public void getUserById(RoutingContext context) {
    int userId = Integer.parseInt(context.pathParam("id"));
    String query = "SELECT id, name, email FROM users WHERE id = ?";

    dbClient.queryWithParams(query, new JsonArray().add(userId), res -> {
      if (res.succeeded() && res.result().getRows().size() > 0) {
        context.response().putHeader("Content-Type", "application/json")
          .end(res.result().getRows().get(0).encode());
      } else {
        context.response().setStatusCode(404).end("❌ Nutzer nicht gefunden!");
      }
    });
  }


  /**
   * @api {put} /users/:id Aktualisiert die Daten eines Benutzers
   * @apiName UpdateUser
   * @apiGroup Users
   *
   * @apiDescription Diese Route aktualisiert die Daten eines Benutzers. Es werden Name, E-Mail und Passwort benötigt.
   *
   * @apiParam {Number} id Die ID des Benutzers, der aktualisiert werden soll.
   * @apiParam {String} name Der neue Name des Benutzers.
   * @apiParam {String} email Die neue E-Mail-Adresse des Benutzers.
   * @apiParam {String} password Das neue Passwort des Benutzers.
   *
   * @apiSuccess {String} 200 Erfolgsmeldung, dass der Benutzer erfolgreich aktualisiert wurde.
   *
   * @apiError (Bad Request 400) BadRequest Es wurden nicht alle erforderlichen Felder übermittelt.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während der Benutzer aktualisiert wurde.
   */
  public void updateUser(RoutingContext context) {
    int userId = Integer.parseInt(context.pathParam("id"));
    JsonObject body = context.getBodyAsJson();
    String name = body.getString("name");
    String email = body.getString("email");
    String password = body.getString("password");

    // Überprüfen, ob alle Felder vorhanden sind
    if (name == null || email == null || password == null) {
      context.response().setStatusCode(400).end("⚠ Name, E-Mail und Passwort sind erforderlich!");
      return;
    }

    // Passwort hashen
    String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

    String query = "UPDATE users SET name = ?, email = ?, password_hash = ? WHERE id = ?";

    dbClient.updateWithParams(query, new JsonArray().add(name).add(email).add(hashedPassword).add(userId), res -> {
      if (res.succeeded()) {
        context.response().setStatusCode(200).end("✅ Nutzer erfolgreich aktualisiert!");
      } else {
        context.response().setStatusCode(500).end("❌ Fehler beim Aktualisieren des Nutzers!");
      }
    });
  }

  // 🔹 Nutzer teilweise aktualisieren (PATCH - NUR EINZELNE FELDER)
  public void partialUpdateUser(RoutingContext context) {
    int userId = Integer.parseInt(context.pathParam("id"));
    JsonObject body = context.getBodyAsJson();

    StringBuilder query = new StringBuilder("UPDATE users SET ");
    JsonArray params = new JsonArray();

    if (body.containsKey("name")) {
      query.append("name = ?, ");
      params.add(body.getString("name"));
    }
    if (body.containsKey("email")) {
      query.append("email = ?, ");
      params.add(body.getString("email"));
    }
    if (body.containsKey("password")) {
      // Passwort hashen, bevor es in die Datenbank kommt
      String hashedPassword = BCrypt.hashpw(body.getString("password"), BCrypt.gensalt());
      query.append("password_hash = ?, ");
      params.add(hashedPassword);
    }

    // Falls keine Daten übermittelt wurden, Fehler zurückgeben
    if (params.size() == 0) {
      context.response().setStatusCode(400).end("⚠ Keine gültigen Daten zum Aktualisieren!");
      return;
    }

    // Letztes Komma entfernen und WHERE-Klausel hinzufügen
    query.setLength(query.length() - 2);
    query.append(" WHERE id = ?");
    params.add(userId);

    dbClient.updateWithParams(query.toString(), params, res -> {
      if (res.succeeded()) {
        context.response().setStatusCode(200).end("✅ Nutzer teilweise aktualisiert!");
      } else {
        context.response().setStatusCode(500).end("❌ Fehler beim Teil-Update des Nutzers!");
      }
    });
  }


  public void getAllUsers(RoutingContext context) {


    String query = "SELECT id, name, email FROM users";

    dbClient.query(query, res -> {
      if (res.succeeded()) {

        context.response().putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("users", res.result().getRows()).encode());
      } else {
        System.out.println("❌ Fehler: " + res.cause().getMessage());
        context.response().setStatusCode(500).end("❌ Fehler beim Abrufen der Nutzer!");
   }
});
}





}
