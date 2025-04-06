
package com.example.starter;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

  private JDBCClient dbClient;
  private AuthService authService;
  private JWTAuth jwtAuth;
  private UserService userService;
  private RecipeService recipeService;

  public static void main(String[] args) {
    Launcher.executeCommand("run", MainVerticle.class.getName());
  }


  @Override
  public void start(Promise<Void> startPromise) {
    dbClient = DatabaseConfig.connect(vertx);
    ThymeleafTemplateEngine templateEngine;
    authService = new AuthService(vertx, dbClient);
    userService = new UserService(vertx, dbClient);
    recipeService = new RecipeService(vertx, dbClient);
   templateEngine = ThymeleafTemplateEngine.create(vertx);

    String staticPath = System.getProperty("user.dir") + File.separator + "frontend";
    System.out.println("Statische Dateien geladen aus: " + staticPath);



    // JWT-Auth initialisieren
    JWTAuthOptions authOptions = new JWTAuthOptions()
      .addJwk(new JsonObject()
        .put("kty", "oct")
        .put("k", "geheimesSchluessel")
        .put("alg", "HS256")
      );
    jwtAuth = JWTAuth.create(vertx, authOptions);

    // Router einrichten
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());


    // Auth-Routen
    router.post("/register").handler(BodyHandler.create()).handler(this::registerUser);
    router.post("/login").handler(this::loginUser);
    router.post("/logout").handler(authService::logoutUser);
    router.route("/*").handler(StaticHandler.create("frontend"));
    router.get("/meine-rezepte").handler(this::showRecipe);

    // Session-Handler hinzufügen
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

      //dark-mode
    router.post("/toggle-dark-mode").handler(this::toggleDarkMode);


    // User-Routen
    router.put("/users/:id").handler(userService::updateUser); // PUT (Änderung aller Daten)
    //router.patch("/users/:id").handler(userService::partialUpdateUser); // PATCH (Teilweise Änderung)
    router.post("/users/delete").handler(this::deleteUser);
    router.patch("/users/me").handler(this::updateUserProfile);
    router.get("/users/me").handler(this::getUserProfile);
    router.patch("/users/me/credentials").handler(this::updateUserCredentials);

      // Belohnung-Routen
    router.get("/users/me/achievements").handler(this::getUserAchievements);
    router.post("/users/me/check-achievements").handler(this::checkAndGrantAchievements);





    router.get("/users/search").handler(userService::getUserByName);
    router.get("/users/nutzer.html").handler(ctx -> {
      ctx.response().sendFile("frontend/nutzer.html");
    });


    // Recipe-Routen
    router.post("/add-recipe").handler(this::addRecipe);
    router.get("/api/recipe/:id").handler(this::getRecipe);
    router.post("/update-recipe").handler(this::updateRecipe);
    router.delete("/recipes/:recipe_id").handler(this::deleteRecipe);

    // Rezept-Suchroute
    router.get("/search/recipes").handler(recipeService::searchRecipes);

    router.post("/add-ingredient").handler(ctx -> {
      String html = "<div class='input-group mb-3 ingredient-item'>" +
        "<input type='text' class='form-control' name='ingredients[]' placeholder='Zutat'>" +
        "<input type='number' class='form-control' name='ingredient_amount[]' placeholder='Menge'>" +
        "<input type='text' class='form-control' name='ingredient_unit[]' placeholder='Einheit (kg, l ...)'>" +
        "<button type='button' class='remove-btn' onclick='removeIngredient(this)'>" +
        "<i class='bi bi-trash'></i></button></div>";
      ctx.response().putHeader("Content-Type", "text/html").end(html);
    });



    router.get("/users/:user_id/recipes").handler(recipeService::getAllRecipes);
    router.get("/users/:user_id/recipes/:recipe_id").handler(recipeService::getRecipeByUserAndById);
    router.get("/users/:user_id/recipes").handler(recipeService::getRecipesByUser);
    router.post("/recipes").handler(JWTAuthHandler.create(jwtAuth)).handler(recipeService::createRecipe);

    router.get("/recipes/:recipe_id").handler(recipeService::getRecipeById);
    router.put("/users/:user_id/recipes/:recipe_id").handler(JWTAuthHandler.create(jwtAuth)).handler(recipeService::updateRecipePut);
    router.patch("/users/:user_id/recipes/:recipe_id").handler(JWTAuthHandler.create(jwtAuth)).handler(recipeService::updateRecipePatch);
    router.post("/users/:user_id/recipes/:recipe_id").handler(this::handleDeleteRequest);
    router.get("/meine-rezepte").handler(this::handleMeineRezepte);



    //Comment-Routen
    router.get("/comments/:recipeId").handler(this::getComments);
    router.post("/comments/add").handler(this::addComment);
    router.get("/comment/recipeId").handler(this :: getRecipeComments );
    router.post("/comments/update").handler(this::updateComment);
    router.get("/comments").handler(this::getComments);
    router.get("/comments/:recipeId").handler(this::getComments);
    router.delete("/comments/:commentId").handler(this::deleteComment);



    // Vorhandene Routen ergänzen
    router.put("/comments/:commentId").handler(this::updateComment);



    //Favorite-Routen



   router.post("/favorites/:recipe_id").handler(this::addFavorite);
    router.delete("/favorites/:recipe_id").handler(this::deleteFavorite);

    router.get("/favorites").handler(this::getFavorites);
   // router.delete("/favorites/:recipe_id").handler(JWTAuthHandler.create(jwtAuth)).handler(favoriteService::deleteFavorite);
    router.get("/users/favorite.html").handler(ctx -> {
      ctx.response().sendFile("frontend/favorite.html");
    });



    // HTTP-Server starten
    vertx.createHttpServer().requestHandler(router).listen(8888).onComplete(http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("✅ Server läuft auf Port 8888");
      } else {
        startPromise.fail(http.cause());
      }
    });
  }

  private void registerUser(RoutingContext context) {
    authService.registerUser(context);
  }

  private void loginUser(RoutingContext context) {
    authService.loginUser(context, jwtAuth);
  }

  private void showRecipe(RoutingContext ctx) {
    authService.getUserIdFromSession(ctx, userIdResult -> {
      if (userIdResult.succeeded() && userIdResult.result() != -1) {
        int userId = userIdResult.result();

        dbClient.getConnection(ar -> {
          if (ar.succeeded()) {
            SQLConnection connection = ar.result();

            // SQL-Abfrage: Rezepte mit Zutaten abrufen
            String query = "SELECT r.id AS recipe_id, r.title, r.description, r.portions, r.image_url, " +
              "i.name AS ingredient_name, ri.amount AS ingredient_amount, ri.unit AS ingredient_unit " +
              "FROM recipes r " +
              "LEFT JOIN recipe_ingredients ri ON r.id = ri.recipe_id " +
              "LEFT JOIN ingredients i ON ri.ingredient_id = i.id " +
              "WHERE r.user_id = " + userId;

            connection.query(query, res -> {
              if (res.succeeded()) {
                Map<Integer, StringBuilder> recipeMap = new HashMap<>();

                res.result().getRows().forEach(row -> {
                  int recipeId = row.getInteger("recipe_id");
                  String title = row.getString("title");
                  String description = row.getString("description");
                  int portions = row.getInteger("portions");
                  String imageUrl = row.getString("image_url");

                  String ingredientName = row.getString("ingredient_name");
                  String ingredientAmount = row.getValue("ingredient_amount") != null ? row.getValue("ingredient_amount").toString() : "";
                  String ingredientUnit = row.getString("ingredient_unit");

                  System.out.println("Gefundene Zutat für Rezept " + recipeId + ": " + ingredientName);
                  System.out.println("Speichere Rezept mit URL: " + imageUrl);


                  // Falls das Rezept noch nicht in der Map ist, erstelle einen neuen Eintrag
                  recipeMap.putIfAbsent(recipeId, new StringBuilder(
                    "<div class='card shadow-sm'><div class='card-body'>"
                      + "<h5 class='card-title'>" + title + "</h5>"
                      + "<p><strong>Beschreibung :</strong> "  + description + "</p>"
                      + "<p><strong>Portionen :</strong> " + portions + "</p>"
                      + "<p><strong>URL:</strong> <a href='" + imageUrl + "' target='_blank'>" + imageUrl + "</a></p>"
                     // + "<h6>Zutaten:</h6><ul>"
                  ));



                  // Falls eine Zutat existiert, hinzufügen
                  if (ingredientName != null) {
                    recipeMap.get(recipeId).append("<li>").append(ingredientName)
                      .append(": ").append(ingredientAmount).append(" ").append(ingredientUnit)
                      .append("</li>");
                  }

                });

                // Bearbeiten-Button außerhalb der Zutaten-Schleife hinzufügen
                recipeMap.forEach((recipeId, sb) -> {
                  sb.append("</ul>") // Zutatenliste schließen
                    .append("<div style='text-align: right; margin-top: 10px;'>") // Container für Button
                    .append("<a href='/edit-recipe.html?id=" + recipeId + "' class='btn btn-primary'>Bearbeiten</a>")
                    .append("<button onclick='deleteRecipe(" + recipeId + ")' class='btn btn-danger'>Löschen</button>")
                    .append("</div></div>"); // Container und Card schließen
                });

                // Abschluss des HTML für jedes Rezept
                List<String> rezepte = recipeMap.values().stream()
                  .map(sb -> sb.append("</ul></div></div>").toString())
                  .collect(Collectors.toList());

                // HTML-Datei laden und Platzhalter ersetzen
                vertx.fileSystem().readFile("frontend/meine-rezepte.html", file -> {
                  if (file.succeeded()) {
                    String page = file.result().toString().replace("{{REZEPTLISTE}}", String.join("", rezepte));
                    ctx.response().putHeader("Content-Type", "text/html").end(page);
                  } else {
                    ctx.fail(500);
                  }
                });

              } else {
                ctx.fail(500);
              }
              connection.close();
            });

          } else {
            ctx.fail(500);
          }
        });

      } else {
        ctx.response().setStatusCode(401).end("Nicht autorisiert");
      }
    });
  }




  /**
   * @api {post} /users/delete Löscht den angemeldeten Benutzer
   * @apiName DeleteUser
   * @apiGroup Users
   *
   * @apiDescription Diese Route löscht den angemeldeten Benutzer aus der Datenbank. Der Benutzer wird anhand der Sitzungs-ID identifiziert.
   *
   * @apiSuccess {Redirect} 302 Redirect zur Registrierungsseite nach erfolgreichem Löschen.
   *
   * @apiError (Unauthorized 401) Unauthorized Der Benutzer ist nicht angemeldet oder die Sitzung ist ungültig.
   * @apiError (Not Found 404) NotFound Der Benutzer wurde nicht gefunden.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während der Benutzer gelöscht wurde.
   */
  public void deleteUser(RoutingContext context) {
    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        // Benutzer ist nicht authentifiziert, zeige eine Fehlermeldung
        context.response()
          .setStatusCode(401)
          .end("❌ Benutzer nicht authentifiziert!");
        return;
      }

      int userId = userIdResult.result();
      System.out.println("🗑 Benutzer-ID zum Löschen: " + userId); // Debugging

      String query = "DELETE FROM users WHERE id = ?";
      JsonArray params = new JsonArray().add(userId);

      dbClient.updateWithParams(query, params, deleteRes -> {
        if (deleteRes.succeeded()) {
          int deletedRows = deleteRes.result().getUpdated();

          if (deletedRows > 0) {
            // Erfolgreiches Löschen des Benutzers
            System.out.println("✅ Benutzer erfolgreich gelöscht.");

            // Umleitung zur Registrierungsseite
            context.response()
              .setStatusCode(302)  // HTTP 302 - Redirect
              .putHeader("Location", "/registrierung.html")
              .end();
          } else {
            // Benutzer nicht gefunden
            context.response()
              .setStatusCode(404)
              .end("❌ Nutzer nicht gefunden!");
          }
        } else {
          // Fehler beim Löschen
          context.response()
            .setStatusCode(500)
            .end("❌ Fehler beim Löschen: " + deleteRes.cause().getMessage());
        }
      });
    });
  }


  /**
   * @api {post} /users/:user_id/recipes/:recipe_id Löscht ein Rezept (simuliert DELETE über POST)
   * @apiName DeleteRecipe
   * @apiGroup Recipes
   *
   * @apiDescription Diese Route simuliert eine DELETE-Operation über eine POST-Anfrage. Sie löscht ein Rezept, wenn der angemeldete Benutzer der Besitzer des Rezepts ist.
   *
   * @apiParam {Number} user_id Die ID des Benutzers, dem das Rezept gehört.
   * @apiParam {Number} recipe_id Die ID des Rezepts, das gelöscht werden soll.
   * @apiParam {String} _method Der Wert "DELETE" (wird verwendet, um die DELETE-Operation zu simulieren).
   *
   * @apiSuccess {String} 200 Erfolgsmeldung, dass das Rezept erfolgreich gelöscht wurde.
   *
   * @apiError (Bad Request 400) BadRequest Die Anfrage ist ungültig (z. B. fehlendes `_method`-Feld oder falscher Wert).
   * @apiError (Unauthorized 401) Unauthorized Der Benutzer ist nicht angemeldet.
   * @apiError (Forbidden 403) Forbidden Der angemeldete Benutzer ist nicht der Besitzer des Rezepts.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während das Rezept gelöscht wurde.
   */
  private void handleDeleteRequest(RoutingContext context) {
    String method = context.request().getFormAttribute("_method");

    if ("DELETE".equalsIgnoreCase(method)) {
      authService.getUserIdFromSession(context, res -> {
        if (res.succeeded() && res.result() > 0) {
          int sessionUserId = res.result();
          String pathUserId = context.pathParam("user_id");

          if (pathUserId != null && pathUserId.equals(String.valueOf(sessionUserId))) {
            recipeService.deleteRecipe(context);
          } else {
            context.response().setStatusCode(403).end("❌ Zugriff verweigert! Benutzer-IDs stimmen nicht überein.");
          }
        } else {
          context.response().setStatusCode(401).end("❌ Nicht eingeloggt!");
        }
      });
    } else {
      context.response().setStatusCode(400).end("❌ Ungültige Anfrage!");
    }
  }


  private void renderTemplate(RoutingContext context, String filePath) {
    vertx.fileSystem().readFile("frontend/" + filePath, res -> {
      if (res.succeeded()) {
        context.response().putHeader("Content-Type", "text/html").end(res.result());
      } else {
        context.fail(500);
      }
    });
  }


  private void handleMeineRezepte(RoutingContext context) {
    authService.getUserIdFromSession(context, res -> {
      if (res.succeeded() && res.result() > 0) {
        int userId = res.result();
        context.put("user_id", userId); // Speichert die ID für das HTML-Template

        // Rezepte des Benutzers aus der Datenbank holen
        String query = "SELECT * FROM recipes WHERE user_id = ?";
        dbClient.queryWithParams(query, new JsonArray().add(userId), res2 -> {
          if (res2.succeeded()) {
            List<JsonObject> rezepte = res2.result().getRows();
            context.put("REZEPTLISTE", rezepte); // Rezepte an das Template übergeben
            renderTemplate(context, "meinerezepte.html");
          } else {
            context.response().setStatusCode(500).end("Fehler beim Laden der Rezepte.");
          }
        });

      } else {
        context.response().setStatusCode(401).end("❌ Nicht eingeloggt!");
      }
    });
  }

  /**
   * @api {post} /add-recipe Rezept hinzufügen
   * @apiName AddRecipe
   * @apiGroup Recipes
   * @apiDescription Erstellt ein neues Rezept für den angemeldeten Benutzer.
   *
   * @apiBody {String} title Der Titel des Rezepts (Pflichtfeld).
   * @apiBody {String} description Eine Beschreibung des Rezepts (Pflichtfeld).
   * @apiBody {Number} [portions=1] Die Anzahl der Portionen (optional, Standardwert: 1).
   * @apiBody {String} [image_url="default.png"] Die Bild-URL des Rezepts (optional, Standardwert: "default.png").
   *
   * @apiSuccess  {String} message Rezept erfolgreich erstellt, Weiterleitung zum Profil.
   * @apiSuccessExample {json} Rezept erfolgreich erstellt:
   *     HTTP/1.1 303 See Other
   *     Location: /profil.html
   *
   * @apiError (Alternative Case) {String} message ⚠ Alle Felder sind erforderlich!
   * @apiErrorExample {json} Fehlende Felder:
   *     HTTP/1.1 400 Bad Request
   *     {
   *       "message": "⚠ Alle Felder sind erforderlich!"
   *     }
   *
   * @apiError  {String} message ⚠ Nicht autorisiert! Bitte einloggen.
   * @apiErrorExample {json} Nicht eingeloggt:
   *     HTTP/1.1 401 Unauthorized
   *     {
   *       "message": "⚠ Nicht autorisiert! Bitte einloggen."
   *     }
   *
   * @apiError  {String} message ❌ Fehler beim Speichern des Rezepts!
   * @apiErrorExample {json} Datenbankfehler:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "❌ Fehler beim Speichern des Rezepts!"
   *     }
   */
  public void addRecipe(RoutingContext context) {
    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        context.response().setStatusCode(401).end("⚠ Nicht autorisiert! Bitte einloggen.");
        return;
      }

      int userId = userIdResult.result();

      // 📌 Formulardaten auslesen
      String title = context.request().getFormAttribute("title");
      String description = context.request().getFormAttribute("description");
      String portionsStr = context.request().getFormAttribute("portions");
      String imageUrl = context.request().getFormAttribute("image_url");

      // Standardwerte setzen, falls leer
      int portions = (portionsStr != null) ? Integer.parseInt(portionsStr) : 1;
      if (imageUrl == null || imageUrl.isEmpty()) imageUrl = "default.png";

      if (title == null || description == null) {
        context.response().setStatusCode(400).end("⚠ Alle Felder sind erforderlich!");
        return;
      }

      // 📌 Rezept speichern
      String recipeQuery = "INSERT INTO recipes (user_id, title, description, portions, image_url, created_at) VALUES (?, ?, ?, ?, ?, NOW())";

      dbClient.updateWithParams(recipeQuery, new JsonArray().add(userId).add(title).add(description).add(portions).add(imageUrl), res -> {
        if (res.succeeded()) {
          int recipeId = res.result().getKeys().getInteger(0); // ID des neuen Rezepts holen

          // 📌 Zutaten speichern
          saveIngredients(recipeId, context);

          context.response()
            .setStatusCode(303)
            .putHeader("Location", "/profil.html") // Weiterleitung zur Profilseite
            .end();
        } else {
          res.cause().printStackTrace();
          context.response().setStatusCode(500).end("❌ Fehler beim Speichern des Rezepts!");
        }
      });
    });
  }

  private void saveIngredients(int recipeId, RoutingContext context) {
    int ingredientIndex = 1;

    while (true) {
      String name = context.request().getFormAttribute("ingredient_" + ingredientIndex + "_name");
      String amountStr = context.request().getFormAttribute("ingredient_" + ingredientIndex + "_amount");
      String unit = context.request().getFormAttribute("ingredient_" + ingredientIndex + "_unit");

      if (name == null) break; // Keine weiteren Zutaten

      double amount = (amountStr != null) ? Double.parseDouble(amountStr) : 0.0;

      // 1️⃣ Hol die Ingredient ID oder füge die Zutat neu ein
      String getOrCreateIngredientQuery =
        "INSERT INTO ingredients (name) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM ingredients WHERE name = ?)";

      dbClient.updateWithParams(getOrCreateIngredientQuery, new JsonArray().add(name).add(name), res -> {
        if (res.failed()) {
          res.cause().printStackTrace();
          return;
        }

        // 2️⃣ Danach Ingredient ID holen und Rezept speichern
        String ingredientQuery =
          "INSERT INTO recipe_ingredients (recipe_id, ingredient_id, amount, unit) " +
            "VALUES (?, (SELECT id FROM ingredients WHERE name = ? LIMIT 1), ?, ?)";

        JsonArray params = new JsonArray()
          .add(recipeId)
          .add(name)
          .add(amount)
          .add(unit);

        dbClient.updateWithParams(ingredientQuery, params, res2 -> {
          if (res2.succeeded()) {
            System.out.println("✅ Zutat gespeichert: " + name);
          } else {
            res2.cause().printStackTrace();
          }
        });
      });

      ingredientIndex++;
    }
  }




  /**
   * @api {get} /api/recipe/:id Gibt ein Rezept anhand seiner ID zurück
   * @apiName GetRecipe
   * @apiGroup Recipes
   *
   * @apiDescription Diese Route gibt ein Rezept anhand seiner ID zurück, einschließlich Titel, Beschreibung, Portionen, Bild-URL und Zutaten.
   *
   * @apiParam {Number} id Die ID des Rezepts.
   *
   * @apiSuccess {JSON} recipe Das Rezept im JSON-Format.
   * @apiSuccessExample {json} Erfolgsantwort:
   *     {
   *       "recipe_id": 1,
   *       "title": "Spaghetti Carbonara",
   *       "description": "Ein klassisches italienisches Gericht.",
   *       "portions": 4,
   *       "image_url": "https://example.com/spaghetti.jpg",
   *       "ingredients": [
   *         {
   *           "id": 1,
   *           "name": "Spaghetti",
   *           "amount": 400,
   *           "unit": "g"
   *         },
   *         {
   *           "id": 2,
   *           "name": "Eier",
   *           "amount": 4,
   *           "unit": "Stück"
   *         }
   *       ]
   *     }
   *
   * @apiError (Not Found 404) NotFound Das Rezept wurde nicht gefunden.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während das Rezept abgerufen wurde.
   */
  private void getRecipe(RoutingContext ctx) {
    int recipeId = Integer.parseInt(ctx.request().getParam("id"));

    dbClient.getConnection(ar -> {
      if (ar.succeeded()) {
        SQLConnection connection = ar.result();

        String query = "SELECT r.id AS recipe_id, r.title, r.description, r.portions, r.image_url, " +
          "i.id AS ingredient_id, i.name AS ingredient_name, ri.amount AS ingredient_amount, ri.unit AS ingredient_unit " +
          "FROM recipes r " +
          "LEFT JOIN recipe_ingredients ri ON r.id = ri.recipe_id " +
          "LEFT JOIN ingredients i ON ri.ingredient_id = i.id " +
          "WHERE r.id = ?";

        connection.queryWithParams(query, new JsonArray().add(recipeId), res -> {
          if (res.succeeded()) {
            JsonObject recipe = new JsonObject();
            List<JsonObject> ingredients = new ArrayList<>();

            res.result().getRows().forEach(row -> {
              if (!recipe.containsKey("recipe_id")) {
                recipe.put("recipe_id", row.getInteger("recipe_id"));
                recipe.put("title", row.getString("title"));
                recipe.put("description", row.getString("description"));
                recipe.put("portions", row.getInteger("portions"));
                recipe.put("image_url", row.getString("image_url"));
              }

              JsonObject ingredient = new JsonObject()
                .put("id", row.getInteger("ingredient_id"))
                .put("name", row.getString("ingredient_name"))
                .put("amount", row.getValue("ingredient_amount"))
                .put("unit", row.getString("ingredient_unit"));
              ingredients.add(ingredient);
            });

            recipe.put("ingredients", ingredients);
            ctx.response().putHeader("Content-Type", "application/json").end(recipe.encode());
          } else {
            ctx.fail(500);
          }
          connection.close();
        });

      } else {
        ctx.fail(500);
      }
    });
  }


  /**
   * @api {post} /update-recipe Aktualisiert ein Rezept
   * @apiName UpdateRecipe
   * @apiGroup Recipes
   *
   * @apiDescription Diese Route aktualisiert ein bestehendes Rezept in der Datenbank. Es werden Titel, Beschreibung, Portionen und Bild-URL benötigt.
   *
   * @apiParam {Number} recipe_id Die ID des Rezepts, das aktualisiert werden soll.
   * @apiParam {String} title Der neue Titel des Rezepts.
   * @apiParam {String} description Die neue Beschreibung des Rezepts.
   * @apiParam {Number} portions Die neue Anzahl der Portionen.
   * @apiParam {String} image_url Die neue Bild-URL des Rezepts.
   *
   * @apiSuccess {JSON} status Erfolgsmeldung, dass das Rezept erfolgreich aktualisiert wurde.
   * @apiSuccessExample {json} Erfolgsantwort:
   *     {
   *       "status": "ok"
   *     }
   *
   * @apiError (Bad Request 400) BadRequest Es wurden nicht alle erforderlichen Felder übermittelt.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während das Rezept aktualisiert wurde.
   */
  private void updateRecipe(RoutingContext ctx) {

    MultiMap form = ctx.request().formAttributes();

    int recipeId = Integer.parseInt(form.get("recipe_id"));
    String title = form.get("title");
    String description = form.get("description");
    int portions = Integer.parseInt(form.get("portions"));
    String imageUrl = form.get("image_url");

    String updateQuery = "UPDATE recipes SET title = ?, description = ?, portions = ?, image_url = ? WHERE id = ?";
    JsonArray params = new JsonArray().add(title).add(description).add(portions).add(imageUrl).add(recipeId);

    dbClient.updateWithParams(updateQuery, params, res -> {
      if (res.succeeded()) {
        ctx.response().putHeader("Content-Type", "application/json").end("{\"status\":\"ok\"}");
      } else {
        ctx.fail(500);
      }
    });
  };


  /**
   * @api {delete} /recipes/:recipe_id Rezept löschen
   * @apiName DeleteRecipe
   * @apiGroup Recipes
   * @apiDescription Löscht ein Rezept, wenn es dem angemeldeten Benutzer gehört.
   *
   * @apiParam {Number} recipe_id Die ID des zu löschenden Rezepts.
   *
   * @apiSuccess {String} message Rezept erfolgreich gelöscht.
   * @apiSuccessExample {json} Erfolgreiche Löschung:
   *     HTTP/1.1 200 OK
   *     {
   *       "message": "✅ Rezept erfolgreich gelöscht!"
   *     }
   *
   * @apiError (Alternative Case) {String} message ❌ Rezept nicht gefunden!
   * @apiErrorExample {json} Rezept existiert nicht:
   *     HTTP/1.1 404 Not Found
   *     {
   *       "message": "❌ Rezept nicht gefunden!"
   *     }
   *
   * @apiError  {String} message ❌ Benutzer nicht authentifiziert!
   * @apiErrorExample {json} Nicht eingeloggt:
   *     HTTP/1.1 401 Unauthorized
   *     {
   *       "message": "❌ Benutzer nicht authentifiziert!"
   *     }
   *
   * @apiError  {String} message ❌ Zugriff verweigert! Du kannst nur deine eigenen Rezepte löschen.
   * @apiErrorExample {json} Kein Zugriff:
   *     HTTP/1.1 403 Forbidden
   *     {
   *       "message": "❌ Zugriff verweigert! Du kannst nur deine eigenen Rezepte löschen."
   *     }
   *
   * @apiError {String} message ❌ Ungültige Rezept-ID!
   * @apiErrorExample {json} Falsche ID:
   *     HTTP/1.1 400 Bad Request
   *     {
   *       "message": "❌ Ungültige Rezept-ID!"
   *     }
   *
   * @apiError {String} message ❌ Fehler beim Löschen des Rezepts.
   * @apiErrorExample {json} Datenbankfehler:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "❌ Fehler beim Löschen des Rezepts!"
   *     }
   */
  public void deleteRecipe(RoutingContext context) {
    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        // Benutzer ist nicht authentifiziert, Fehlermeldung senden
        context.response()
          .setStatusCode(401)
          .end("❌ Benutzer nicht authentifiziert!");
        return;
      }

      int userId = userIdResult.result();
      int recipeId;

      try {
        recipeId = Integer.parseInt(context.pathParam("recipe_id"));
      } catch (NumberFormatException e) {
        context.response()
          .setStatusCode(400)
          .end("❌ Ungültige Rezept-ID!");
        return;
      }

      // Überprüfen, ob das Rezept dem Benutzer gehört
      String checkQuery = "SELECT user_id FROM recipes WHERE id = ?";
      JsonArray checkParams = new JsonArray().add(recipeId);

      dbClient.queryWithParams(checkQuery, checkParams, checkRes -> {
        if (checkRes.succeeded() && !checkRes.result().getRows().isEmpty()) {
          int recipeOwnerId = checkRes.result().getRows().get(0).getInteger("user_id");

          if (recipeOwnerId != userId) {
            // Der Benutzer darf dieses Rezept nicht löschen
            context.response()
              .setStatusCode(403)
              .end("❌ Zugriff verweigert! Du kannst nur deine eigenen Rezepte löschen.");
            return;
          }

          // Rezept löschen
          String deleteQuery = "DELETE FROM recipes WHERE id = ?";
          dbClient.updateWithParams(deleteQuery, new JsonArray().add(recipeId), deleteRes -> {
            if (deleteRes.succeeded()) {
              context.response()
                .setStatusCode(200)
                .end("✅ Rezept erfolgreich gelöscht!");
            } else {
              System.err.println("❌ Fehler beim Löschen: " + deleteRes.cause().getMessage());

              context.response()
                .setStatusCode(500)
                .end("❌ Fehler beim Löschen des Rezepts: " + deleteRes.cause().getMessage());
              System.err.println("❌ Fehler beim Löschen: " + deleteRes.cause().getMessage());

            }
          });

        } else {
          context.response()
            .setStatusCode(404)
            .end("❌ Rezept nicht gefunden!");
        }
      });
    });
  }



  /**
   * @api {patch} /users/me Aktualisiert das Profil des angemeldeten Benutzers
   * @apiName UpdateUserProfile
   * @apiGroup Users
   *
   * @apiDescription Diese Route aktualisiert das Profil des angemeldeten Benutzers. Es können Name und/oder E-Mail aktualisiert werden.
   *
   * @apiParam {String} [name] Der neue Name des Benutzers (optional).
   * @apiParam {String} [email] Die neue E-Mail-Adresse des Benutzers (optional).
   *
   * @apiSuccess {String} 200 Erfolgsmeldung, dass das Profil erfolgreich aktualisiert wurde.
   *
   * @apiError (Bad Request 400) BadRequest Es wurden keine gültigen Daten zum Aktualisieren übermittelt.
   * @apiError (Unauthorized 401) Unauthorized Der Benutzer ist nicht angemeldet oder die Sitzung ist ungültig.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während das Profil aktualisiert wurde.
   */
  private void updateUserProfile(RoutingContext context) {
    authService.getUserIdFromSession(context, res -> {
      if (res.succeeded() && res.result() != -1) {
        int userId = res.result();
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

        if (params.size() == 0) {
          context.response().setStatusCode(400).end("⚠ Keine gültigen Daten zum Aktualisieren!");
          return;
        }

        query.setLength(query.length() - 2);
        query.append(" WHERE id = ?");
        params.add(userId);

        dbClient.updateWithParams(query.toString(), params, dbRes -> {
          if (dbRes.succeeded()) {
            context.response().setStatusCode(200).end("✅ Profil aktualisiert!");
          } else {
            context.response().setStatusCode(500).end("❌ Fehler beim Aktualisieren!");
          }
        });
      } else {
        context.response().setStatusCode(401).end("⚠ Nicht autorisiert!");
      }
    });
  }

  /**
   * @api {get} /users/me Gibt das Profil des angemeldeten Benutzers zurück
   * @apiName GetUserProfile
   * @apiGroup Users
   *
   * @apiDescription Diese Route gibt das Profil des angemeldeten Benutzers zurück, einschließlich Name und E-Mail.
   *
   * @apiSuccess {JSON} user Das Profil des Benutzers im JSON-Format.
   * @apiSuccessExample {json} Erfolgsantwort:
   *     {
   *       "name": "Max Mustermann",
   *       "email": "max@example.com"
   *     }
   *
   * @apiError (Unauthorized 401) Unauthorized Der Benutzer ist nicht angemeldet oder die Sitzung ist ungültig.
   * @apiError (Not Found 404) NotFound Der Benutzer wurde nicht gefunden.
   */
  private void getUserProfile(RoutingContext context) {
    authService.getUserIdFromSession(context, res -> {
      if (res.succeeded() && res.result() != -1) {
        int userId = res.result();
        String query = "SELECT name, email FROM users WHERE id = ?";

        dbClient.queryWithParams(query, new JsonArray().add(userId), dbRes -> {
          if (dbRes.succeeded() && !dbRes.result().getRows().isEmpty()) {
            JsonObject user = dbRes.result().getRows().get(0);
            context.response()
              .putHeader("Content-Type", "application/json")
              .end(user.encode());
          } else {
            context.response().setStatusCode(404).end("❌ Benutzer nicht gefunden!");
          }
        });
      } else {
        context.response().setStatusCode(401).end("⚠ Nicht autorisiert!");
      }
    });
  }


  /**
   * @api {post} /favorites/:recipe_id Fügt ein Rezept zu den Favoriten des Benutzers hinzu
   * @apiName AddFavorite
   * @apiGroup Favorites
   *
   * @apiDescription Diese Route fügt ein Rezept zu den Favoriten des angemeldeten Benutzers hinzu.
   *
   * @apiParam {Number} recipe_id Die ID des Rezepts, das zu den Favoriten hinzugefügt werden soll.
   *
   * @apiSuccess {JSON} response Ein JSON-Objekt mit einer Erfolgsmeldung.
   * @apiSuccessExample {json} Erfolgsantwort:
   *     {
   *       "message": "Dieses Rezept wurde erfolgreich zu Favoriten hinzugefügt!"
   *     }
   *
   * @apiError (Bad Request 400) BadRequest Die Rezept-ID ist ungültig oder fehlt.
   * @apiError (Unauthorized 401) Unauthorized Der Benutzer ist nicht angemeldet.
   * @apiError (Conflict 409) Conflict Das Rezept ist bereits in den Favoriten enthalten.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während das Rezept zu den Favoriten hinzugefügt wurde.
   */
  public void addFavorite(RoutingContext context) {
    authService.getUserIdFromSession(context, res -> {
      if (res.succeeded() && res.result() != -1) {
        int userId = res.result();

        // Rezept-ID aus der URL extrahieren
        String recipeIdParam = context.pathParam("recipe_id");

        if (recipeIdParam == null) {
          context.response().setStatusCode(400).end("Fehler: Ungültige Rezept-ID.");
          return;
        }

        int recipeId;
        try {
          recipeId = Integer.parseInt(recipeIdParam);
        } catch (NumberFormatException e) {
          context.response().setStatusCode(400).end("Fehler: Rezept-ID muss eine Zahl sein.");
          return;
        }

        // Überprüfen, ob das Rezept bereits in den Favoriten ist
        String checkQuery = "SELECT COUNT(*) FROM favorites WHERE user_id = ? AND recipe_id = ?";
        JsonArray checkParams = new JsonArray().add(userId).add(recipeId);

        dbClient.queryWithParams(checkQuery, checkParams, checkRes -> {
          if (checkRes.succeeded()) {
            int count = checkRes.result().getRows().get(0).getInteger("COUNT(*)");

            if (count > 0) {
              context.response().setStatusCode(409).end("Dieses Rezept ist bereits in Ihren Favoriten.");
            } else {
              // Rezept zu den Favoriten hinzufügen
              String insertQuery = "INSERT INTO favorites (user_id, recipe_id) VALUES (?, ?)";
              JsonArray insertParams = new JsonArray().add(userId).add(recipeId);

              dbClient.updateWithParams(insertQuery, insertParams, insertRes -> {
                if (insertRes.succeeded()) {
                  JsonObject jsonResponse = new JsonObject()
                    .put("Dieses Rezept ", " wurde erfolgreich zu Favoriten hinzugefügt!");
                   // .put("redirectUrl", "/favorite.html");  // Hier die URL zur Favoriten-Seite anpassen

                  context.response().putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(jsonResponse.encode());
                } else {
                  context.response().setStatusCode(500).end("Fehler beim Hinzufügen zu den Favoriten.");
                }
              });
            }
          } else {
            context.response().setStatusCode(500).end("Fehler beim Überprüfen der Favoriten.");
          }
        });

      } else {
        context.response().setStatusCode(401).end("Fehler: Sie müssen eingeloggt sein.");
      }
    });
  }


  /**
   * @api {get} /favorites Favoriten abrufen
   * @apiName GetFavorites
   * @apiGroup Favorites
   * @apiDescription Gibt die Favoriten des aktuell angemeldeten Benutzers zurück. Der Benutzer muss eingeloggt sein, um seine Favoriten abzurufen.
   *
   * @apiSuccess {Object[]} favorites Liste der Favoriten des Benutzers.
   * @apiSuccess {Number} favorites.id ID des Rezepts.
   * @apiSuccess {String} favorites.title Titel des Rezepts.
   * @apiSuccess {String} favorites.description Beschreibung des Rezepts.
   * @apiSuccess {String} favorites.image_url URL des Rezeps-Bildes.
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 200 OK
   *     [
   *       {
   *         "id": 1,
   *         "title": "Spaghetti Carbonara",
   *         "description": "Leckere Pasta mit Ei und Speck.",
   *         "image_url": "carbonara.jpg"
   *       },
   *       {
   *         "id": 2,
   *         "title": "Pizza Margherita",
   *         "description": "Klassische Pizza mit Tomaten und Mozzarella.",
   *         "image_url": "pizza_margherita.jpg"
   *       }
   *     ]
   *
   * @apiError (Unauthorized) {String} message Fehlende Anmeldung.
   * @apiErrorExample {json} Nicht angemeldet:
   *     HTTP/1.1 401 Unauthorized
   *     {
   *       "message": "Fehler: Sie müssen eingeloggt sein."
   *     }
   *
   * @apiError (Server Error) {String} message Fehler beim Abrufen der Favoriten.
   * @apiErrorExample {json} Datenbankfehler:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "Fehler beim Abrufen der Favoriten."
   *     }
   */

  public void getFavorites(RoutingContext context) {
    authService.getUserIdFromSession(context, res -> {
      if (res.succeeded() && res.result() != -1) {
        int userId = res.result();

        // SQL-Abfrage: Holen der gespeicherten Favoriten
        String query = "SELECT r.id, r.title, r.description, r.image_url FROM recipes r " +
          "JOIN favorites f ON r.id = f.recipe_id WHERE f.user_id = ?";
        JsonArray params = new JsonArray().add(userId);

        dbClient.queryWithParams(query, params, queryRes -> {
          if (queryRes.succeeded()) {
            List<JsonObject> favorites = queryRes.result().getRows();
            context.response()
              .putHeader("Content-Type", "application/json")
              .end(new JsonArray(favorites).encode());
          } else {
            context.response().setStatusCode(500).end("Fehler beim Abrufen der Favoriten.");
          }
        });
      } else {
        context.response().setStatusCode(401).end("Fehler: Sie müssen eingeloggt sein.");
      }
    });
  }

  /**
   * @api {post} /comments/add Kommentar hinzufügen
   * @apiName AddComment
   * @apiGroup Comments
   * @apiDescription Fügt einen neuen Kommentar zu einem Rezept hinzu. Der Benutzer muss angemeldet sein.
   *
   * @apiHeader {String} Authorization Bearer-Token zur Authentifizierung.
   *
   * @apiBody {Number} recipeId Die ID des Rezepts, zu dem der Kommentar hinzugefügt werden soll.
   * @apiBody {String} content Der Inhalt des Kommentars.
   *
   * @apiSuccess {String} message Erfolgreich hinzugefügter Kommentar.
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 303 See Other
   *     {
   *       "Location": "/comment.html?recipeId=1"
   *     }
   *
   * @apiError (Unauthorized) {String} message ❌ Nicht authentifiziert! Bitte melden Sie sich an.
   * @apiErrorExample {json} Fehlende Authentifizierung:
   *     HTTP/1.1 403 Forbidden
   *     {
   *       "message": "Nicht authentifiziert! Bitte melden Sie sich an."
   *     }
   *
   * @apiError (Bad Request) {String} message Ungültige Rezept-ID
   * @apiErrorExample {json} Ungültige Rezept-ID:
   *     HTTP/1.1 400 Bad Request
   *     {
   *       "message": "Ungültige Rezept-ID"
   *     }
   *
   * @apiError (Server Error) {String} message Fehler beim Speichern des Kommentars.
   * @apiErrorExample {json} Fehler bei der Speicherung:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "Fehler beim Speichern des Kommentars."
   *     }
   */

  public void addComment(RoutingContext context) {
    JsonObject body = context.getBodyAsJson();

    // Sicherstellen, dass recipeId als Integer geparst werden kann
    Integer recipeId;
    try {
      recipeId = Integer.parseInt(body.getString("recipeId"));
    } catch (NumberFormatException | NullPointerException e) {
      context.response().setStatusCode(400).end("Ungültige Rezept-ID");
      return;
    }

    String content = body.getString("content");

    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.succeeded() && userIdResult.result() != -1) {
        Integer userId = userIdResult.result(); // Benutzer-ID aus der Session holen

        String query = "INSERT INTO comments (user_id, recipe_id, content) VALUES (?, ?, ?)";
        JsonArray params = new JsonArray().add(userId).add(recipeId).add(content);

        dbClient.queryWithParams(query, params, res -> {
          if (res.succeeded()) {
            context.response()
              .setStatusCode(303) // 303 See Other - für Redirects nach POST-Anfragen
              .putHeader("Location", "/comment.html?recipeId=" + recipeId)
              .end();
          } else {
            context.response().setStatusCode(500).end("Fehler beim Speichern des Kommentars.");
          }
        });

      } else {
        context.response().setStatusCode(403).end("Nicht authentifiziert! Bitte melden Sie sich an.");
      }
    });
  }



  /**
   * @api {post} /comments/update Kommentar aktualisieren
   * @apiName UpdateComment
   * @apiGroup Comments
   * @apiDescription Aktualisiert den Inhalt eines Kommentars, wenn der Benutzer der Besitzer des Kommentars ist.
   * Der Benutzer muss angemeldet sein, um einen Kommentar zu bearbeiten.
   *
   * @apiParam {String} commentId Die ID des Kommentars, der aktualisiert werden soll.
   *
   * @apiParam {String} content Neuer Inhalt des Kommentars. Dieser Parameter wird im Request-Body übergeben.
   *
   * @apiSuccess {String} message Erfolgsmeldung, wenn der Kommentar erfolgreich aktualisiert wurde.
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 200 OK
   *     "Kommentar aktualisiert!"
   *
   * @apiError (Unauthorized) {String} message Fehlende Anmeldung oder fehlende Berechtigung zum Bearbeiten des Kommentars.
   * @apiErrorExample {json} Nicht angemeldet:
   *     HTTP/1.1 401 Unauthorized
   *     {
   *       "message": "Nicht angemeldet!"
   *     }
   * @apiError (Forbidden) {String} message Keine Berechtigung, den Kommentar zu bearbeiten.
   * @apiErrorExample {json} Keine Berechtigung:
   *     HTTP/1.1 403 Forbidden
   *     {
   *       "message": "Keine Berechtigung!"
   *     }
   *
   * @apiError (Server Error) {String} message Fehler bei der Datenbankabfrage oder Aktualisierung.
   * @apiErrorExample {json} Datenbankfehler:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "Datenbankfehler"
   *     }
   */

  public void updateComment(RoutingContext context) {
    String commentId = context.pathParam("commentId");

    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        context.response().setStatusCode(401).end("Nicht angemeldet!");
        return;
      }

      int currentUserId = userIdResult.result();
      JsonObject body = context.getBodyAsJson();
      String newContent = body.getString("content");

      // 1. Prüfe ob der Nutzer der Kommentar-Besitzer ist
      String checkOwnership = "SELECT user_id FROM comments WHERE id = ?";

      dbClient.queryWithParams(checkOwnership, new JsonArray().add(commentId), checkRes -> {
        if (checkRes.succeeded() && !checkRes.result().getRows().isEmpty()) {
          int commentOwnerId = checkRes.result().getRows().get(0).getInteger("user_id");

          if (commentOwnerId != currentUserId) {
            context.response().setStatusCode(403).end("Keine Berechtigung!");
            return;
          }

          // 2. Aktualisiere den Kommentar
          String updateQuery = "UPDATE comments SET content = ? WHERE id = ?";
          dbClient.updateWithParams(updateQuery,
            new JsonArray().add(newContent).add(commentId),
            updateRes -> {
              if (updateRes.succeeded()) {
                context.response().end("Kommentar aktualisiert!");
              } else {
                context.response().setStatusCode(500).end("Datenbankfehler");
              }
            });
        }
      });
    });
  }


  /**
   * @api {get} /comments/:recipeId Kommentare für Rezept abrufen
   * @apiName GetComments
   * @apiGroup Comments
   * @apiDescription Ruft alle Kommentare für ein bestimmtes Rezept ab. Der Benutzer muss angemeldet sein, um seine eigenen Kommentare zu bearbeiten oder zu löschen.
   *
   * @apiParam {Number} recipeId Die ID des Rezepts, dessen Kommentare abgerufen werden sollen.
   *
   * @apiSuccess {String} commentsHtml HTML-String der Kommentare. Enthält auch Bearbeitungs- und Lösch-Buttons für den Eigentümer des Kommentars.
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 200 OK
   *     {
   *       "commentsHtml": "<div class='border p-2 my-2' data-id='1'><strong>Max Mustermann</strong><p id='comment-text-1'>Tolles Rezept!</p><small class='text-muted'>2025-03-14</small><button class='btn btn-sm btn-warning' onclick='editComment(1)'>Bearbeiten</button><button class='btn btn-sm btn-danger ms-1' onclick='deleteComment(1)'>Löschen</button></div>"
   *     }
   *
   * @apiError (Not Found) {String} message Rezept nicht gefunden.
   * @apiErrorExample {json} Rezept nicht gefunden:
   *     HTTP/1.1 404 Not Found
   *     {
   *       "message": "Rezept nicht gefunden"
   *     }
   *
   * @apiError (Server Error) {String} message Fehler beim Abrufen der Kommentare.
   * @apiErrorExample {json} Fehler beim Abrufen:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "Fehler beim Abrufen der Kommentare."
   *     }
   */

  public void getComments(RoutingContext context) {
    String recipeId = context.request().getParam("recipeId");

    // Holt die Benutzer-ID aus der Session (vorausgesetzt, der Nutzer ist angemeldet)
    authService.getUserIdFromSession(context, userIdResult -> {
      Integer currentUserId = userIdResult.result(); // -1 wenn nicht angemeldet

      String query = "SELECT c.id, u.id AS user_id, u.name, c.content, c.created_at "
        + "FROM comments c JOIN users u ON c.user_id = u.id WHERE c.recipe_id = ?";

      dbClient.queryWithParams(query, new JsonArray().add(recipeId), res -> {
        if (res.succeeded()) {
          StringBuilder commentsHtml = new StringBuilder();
          for (JsonObject row : res.result().getRows()) {
            Integer commentUserId = row.getInteger("user_id");
            boolean isOwner = currentUserId != -1 && currentUserId.equals(commentUserId);

            // BESTEHENDE INHALTE (Benutzername, Kommentar, Datum)
           /* commentsHtml.append("<div class='border p-2 my-2'>")
              .append("<strong>").append(row.getString("name")).append("</strong>")
              .append("<p>").append(row.getString("content")).append("</p>")*/
            commentsHtml.append("<div class='border p-2 my-2' data-id='").append(row.getInteger("id")).append("'>")
              .append("<strong>").append(row.getString("name")).append("</strong>")
              .append("<p id='comment-text-").append(row.getInteger("id")).append("'>") // ID hinzufügen
              .append(row.getString("content")).append("</p>")
              .append("<small class='text-muted'>").append(row.getString("created_at")).append("</small>");

            // BEARBEITUNGSBUTTON UND LOESCHBUTTON  NUR FÜR DEN BESITZER
            if (isOwner) {
              commentsHtml.append("<button class='btn btn-sm btn-warning' onclick='editComment(")
                .append(row.getInteger("id")).append(")'>Bearbeiten</button>")

                .append("<button class='btn btn-sm btn-danger ms-1' onclick='deleteComment(")
                .append(row.getInteger("id")).append(")'>Löschen</button>");

            }

            commentsHtml.append("</div>");
          }
        //  context.response().end(commentsHtml.toString());
          context.response()
            .putHeader("content-type", "application/json")
            .end(commentsHtml.toString());

        }
      });
    });


  }


  /**
   * @api {delete} /comments/:commentId Löscht einen Kommentar
   * @apiName DeleteComment
   * @apiGroup Comments
   * @apiDescription Diese Route löscht einen Kommentar, wenn der angemeldete Benutzer der Besitzer des Kommentars ist.
   *
   * @apiParam {Number} commentId Die ID des Kommentars, der gelöscht werden soll.
   *
   * @apiSuccess {String} 200 Erfolgsmeldung, dass der Kommentar erfolgreich gelöscht wurde.
   *
   * @apiError (Unauthorized 401) Unauthorized Der Benutzer ist nicht angemeldet.
   * @apiError (Forbidden 403) Forbidden Der angemeldete Benutzer ist nicht der Besitzer des Kommentars.
   * @apiError (Not Found 404) NotFound Der Kommentar wurde nicht gefunden.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während der Kommentar gelöscht wurde.
   */
  // Neue deleteComment-Methode
  public void deleteComment(RoutingContext context) {
    String commentId = context.pathParam("commentId");

    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        context.response().setStatusCode(401).end("Nicht angemeldet!");
        return;
      }

      int currentUserId = userIdResult.result();

      // 1. Prüfe Kommentar-Besitz
      String checkQuery = "SELECT user_id FROM comments WHERE id = ?";
      dbClient.queryWithParams(checkQuery, new JsonArray().add(commentId), checkRes -> {
        if (checkRes.succeeded() && !checkRes.result().getRows().isEmpty()) {
          int commentOwnerId = checkRes.result().getRows().get(0).getInteger("user_id");

          if (commentOwnerId != currentUserId) {
            context.response().setStatusCode(403).end("Keine Berechtigung!");
            return;
          }

          // 2. Lösche Kommentar
          String deleteQuery = "DELETE FROM comments WHERE id = ?";
          dbClient.updateWithParams(deleteQuery, new JsonArray().add(commentId), deleteRes -> {
            if (deleteRes.succeeded()) {
              context.response().setStatusCode(200).end("Kommentar gelöscht");
            } else {
              context.response().setStatusCode(500).end("Datenbankfehler");
            }
          });
        } else {
          context.response().setStatusCode(404).end("Kommentar nicht gefunden");
        }
      });
    });
  }


  /**
   * @api {get} /comment/:recipeId Kommentare für Rezept abrufen
   * @apiName GetRecipeComments
   * @apiGroup Comments
   * @apiDescription Ruft alle Kommentare für ein spezifisches Rezept ab. Der Benutzer muss angemeldet sein, um Kommentare abzurufen.
   *
   * @apiParam {Number} recipeId Die ID des Rezepts, dessen Kommentare abgerufen werden sollen.
   *
   * @apiSuccess {Object[]} comments Liste der Kommentare.
   * @apiSuccess {Number} comments.id ID des Kommentars.
   * @apiSuccess {String} comments.content Inhalt des Kommentars.
   * @apiSuccess {Number} comments.userId ID des Benutzers, der den Kommentar erstellt hat.
   * @apiSuccess {String} comments.username Benutzername des Benutzers.
   * @apiSuccess {Boolean} comments.isOwner Gibt an, ob der authentifizierte Benutzer der Besitzer des Kommentars ist.
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 200 OK
   *     [
   *       {
   *         "id": 1,
   *         "content": "Tolles Rezept!",
   *         "userId": 123,
   *         "username": "MaxMustermann",
   *         "isOwner": true
   *       },
   *       {
   *         "id": 2,
   *         "content": "Sehr lecker, danke!",
   *         "userId": 124,
   *         "username": "ErikaMusterfrau",
   *         "isOwner": false
   *       }
   *     ]
   *
   * @apiError (Bad Request) {String} message Fehlende Rezept-ID.
   * @apiErrorExample {json} Fehlende Rezept-ID:
   *     HTTP/1.1 400 Bad Request
   *     {
   *       "message": "Fehlende Rezept-ID."
   *     }
   *
   * @apiError (Server Error) {String} message Fehler beim Laden der Kommentare.
   * @apiErrorExample {json} Fehler beim Laden:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "Fehler beim Laden der Kommentare."
   *     }
   */


  public void getRecipeComments(RoutingContext context) {
    String recipeId = context.request().getParam("recipeId");
    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.succeeded() && userIdResult.result() != -1) {
        Integer userId = userIdResult.result(); // Benutzer-ID aus der Session holen
        if (recipeId == null) {
          context.response().setStatusCode(400).end("Fehlende Rezept-ID.");
          return;
        }

        String query = "SELECT c.id, c.content, c.user_id, u.username FROM comments c " +
          "JOIN users u ON c.user_id = u.id WHERE c.recipe_id = ?";

        JsonArray params = new JsonArray().add(Integer.parseInt(recipeId));

        dbClient.queryWithParams(query, params, res -> {
          if (res.succeeded()) {
            JsonArray comments = new JsonArray();
            for (JsonObject row : res.result().getRows()) {
              JsonObject comment = new JsonObject()
                .put("id", row.getInteger("id"))
                .put("content", row.getString("content"))
                .put("userId", row.getInteger("user_id"))
                .put("username", row.getString("username"))
                .put("isOwner", row.getInteger("user_id").equals(userId)); // Prüft, ob der Nutzer sein Kommentar bearbeiten kann
              comments.add(comment);
            }
            context.response().putHeader("content-type", "application/json").end(comments.encode());
          } else {
            context.response().setStatusCode(500).end("Fehler beim Laden der Kommentare.");
          }
        });
      }
      });
    }



  /**
   * @api {delete} /favorites/:recipe_id Favorit entfernen
   * @apiName DeleteFavorite
   * @apiGroup Favorites
   * @apiDescription Entfernt ein Rezept aus der Liste der Favoriten eines Benutzers.
   * Der Benutzer muss angemeldet sein, um ein Rezept aus seinen Favoriten zu entfernen.
   *
   * @apiParam {Number} recipe_id Die ID des Rezepts, das aus den Favoriten entfernt werden soll.
   *
   * @apiSuccess {String} status Erfolgreiche Antwort mit dem Status "success", wenn das Rezept entfernt wurde.
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 200 OK
   *     {
   *       "status": "success"
   *     }
   *
   * @apiError (Unauthorized) {String} message Fehlende Anmeldung.
   * @apiErrorExample {json} Nicht angemeldet:
   *     HTTP/1.1 401 Unauthorized
   *     {
   *       "message": "Nicht angemeldet!"
   *     }
   *
   * @apiError (Server Error) {String} message Fehler bei der Datenbankabfrage.
   * @apiErrorExample {json} Datenbankfehler:
   *     HTTP/1.1 500 Internal Server Error
   *     {}
   */

  public void deleteFavorite(RoutingContext context) {
    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        context.response().setStatusCode(401).end("Nicht angemeldet!");
        return;
      }

      int userId = userIdResult.result();
      int recipeId = Integer.parseInt(context.pathParam("recipe_id"));

      String deleteQuery = "DELETE FROM favorites WHERE user_id = ? AND recipe_id = ?";
      dbClient.updateWithParams(deleteQuery,
        new JsonArray().add(userId).add(recipeId),
        res -> {
          if (res.succeeded()) {
            context.response().end(new JsonObject().put("status", "success").encode());
          } else {
            context.response().setStatusCode(500).end();
          }
        }
      );
    });
  }





  /**
   * @api {patch} /users/me/credentials Aktualisiert die Anmeldeinformationen des angemeldeten Benutzers
   * @apiName UpdateUserCredentials
   * @apiGroup Users
   *
   * @apiDescription Diese Route aktualisiert die Anmeldeinformationen (E-Mail und/oder Passwort) des angemeldeten Benutzers.
   *
   * @apiParam {String} [email] Die neue E-Mail-Adresse des Benutzers (optional).
   * @apiParam {String} [password] Das neue Passwort des Benutzers (optional).
   *
   * @apiSuccess {String} 200 Erfolgsmeldung, dass die Anmeldeinformationen erfolgreich aktualisiert wurden.
   *
   * @apiError (Bad Request 400) BadRequest Es wurden keine gültigen Daten zum Aktualisieren übermittelt.
   * @apiError (Unauthorized 401) Unauthorized Der Benutzer ist nicht angemeldet oder die Sitzung ist ungültig.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während die Anmeldeinformationen aktualisiert wurden.
   */
  private void updateUserCredentials(RoutingContext context) {
    authService.getUserIdFromSession(context, res -> {
      if (res.succeeded() && res.result() != -1) {
        int userId = res.result();
        JsonObject body = context.getBodyAsJson();
        StringBuilder query = new StringBuilder("UPDATE users SET ");
        JsonArray params = new JsonArray();

        if (body.containsKey("email")) {
          query.append("email = ?, ");
          params.add(body.getString("email"));
        }
        if (body.containsKey("password")) {
          String hashedPassword = authService.hashPassword(body.getString("password"));
          query.append("password_hash = ?, ");
          params.add(hashedPassword);
        }

        // Wenn keine gültigen Daten übermittelt wurden
        if (params.size() == 0) {
          context.response().setStatusCode(400).end("⚠ Keine gültigen Daten zum Aktualisieren!");
          return;
        }

        // Letztes Komma entfernen und WHERE-Klausel hinzufügen
        query.setLength(query.length() - 2);
        query.append(" WHERE id = ?");
        params.add(userId);

        dbClient.updateWithParams(query.toString(), params, dbRes -> {
          if (dbRes.succeeded()) {
            context.response().setStatusCode(200).end("✅ Anmeldeinformationen aktualisiert!");
          } else {
            context.response().setStatusCode(500).end("❌ Fehler beim Aktualisieren der Anmeldeinformationen!");
          }
        });
      } else {
        context.response().setStatusCode(401).end("⚠ Nicht autorisiert!");
      }
    });
  }


  // In der start-Methode von MainVerticle.java


  /**
   * @api {post} /toggle-dark-mode Schaltet den Dunkelmodus für den angemeldeten Benutzer um
   * @apiName ToggleDarkMode
   * @apiGroup UserSettings
   *
   * @apiDescription Diese Route schaltet den Dunkelmodus für den angemeldeten Benutzer um. Der neue Status wird in der Datenbank gespeichert.
   *
   * @apiSuccess {Empty} 200 Der Dunkelmodus-Status wurde erfolgreich aktualisiert.
   *
   * @apiError (Unauthorized 401) Unauthorized Der Benutzer ist nicht angemeldet oder die Sitzung ist ungültig.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während der Dunkelmodus-Status aktualisiert wurde.
   */
  private void toggleDarkMode(RoutingContext context) {
    HttpServerRequest request = context.request();
    HttpServerResponse response = context.response();

    // Überprüfen, ob der Benutzer authentifiziert ist
    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        response.setStatusCode(401).end("⚠ Nicht autorisiert! Bitte einloggen.");
        return;
      }

      int userId = userIdResult.result();

      // Hole den aktuellen Dunkelmodus-Status aus der Session
      Boolean isDarkMode = context.session().get("darkMode");

      // Toggle den Dunkelmodus
      boolean newDarkModeStatus = isDarkMode == null || !isDarkMode;
      context.session().put("darkMode", newDarkModeStatus);

      // Speichern des Dunkelmodus-Status in der Datenbank
      String query = "UPDATE users SET dark_mode = ? WHERE id = ?";
      dbClient.updateWithParams(query, new JsonArray().add(newDarkModeStatus).add(userId), dbRes -> {
        if (dbRes.succeeded()) {
          System.out.println("Dunkelmodus-Status für Benutzer " + userId + " aktualisiert: " + newDarkModeStatus);
          response.setStatusCode(200).end();
        } else {
          System.err.println("Fehler beim Aktualisieren des Dunkelmodus-Status: " + dbRes.cause().getMessage());
          response.setStatusCode(500).end("❌ Fehler beim Aktualisieren des Dunkelmodus-Status");
        }
      });
    });
  }


  /**
   * @api {post} /users/me/check-achievements Überprüfe und gewähre Erfolge
   * @apiName CheckAndGrantAchievements
   * @apiGroup User
   * @apiDescription Überprüft die Anzahl der erstellten Rezepte eines Benutzers und vergibt gegebenenfalls Erfolge.
   *
   * @apiSuccess  {String} message ✅ Erfolg vergeben: [Achievement Type]
   * @apiSuccessExample {json} Erfolg vergeben:
   *     HTTP/1.1 200 OK
   *     {
   *       "message": "✅ Erfolg vergeben: Meisterkoch"
   *     }
   *
   * @apiSuccess (Alternative Case) {String} message ⚠ Kein neuer Erfolg vergeben.
   * @apiSuccessExample {json} Kein neuer Erfolg:
   *     HTTP/1.1 200 OK
   *     {
   *       "message": "⚠ Kein neuer Erfolg vergeben."
   *     }
   *
   * @apiError {String} message ❌ Nicht authentifiziert!
   * @apiErrorExample {json} Nicht authentifiziert:
   *     HTTP/1.1 401 Unauthorized
   *     {
   *       "message": "❌ Nicht authentifiziert!"
   *     }
   *
   * @apiError {String} message ❌ Fehler beim Abrufen der Rezeptanzahl!
   * @apiErrorExample {json} Fehler beim Abrufen:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "❌ Fehler beim Abrufen der Rezeptanzahl!"
   *     }
   *
   * @apiError {String} message ❌ Fehler beim Vergeben des Erfolgs!
   * @apiErrorExample {json} Fehler beim Vergeben:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "❌ Fehler beim Vergeben des Erfolgs!"
   *     }
   */
  public void checkAndGrantAchievements(RoutingContext context) {
    // Benutzer-ID aus der Session holen
    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        context.response().setStatusCode(401).end("❌ Nicht authentifiziert!");
        return;
      }

      int userId = userIdResult.result();

      // Anzahl der Rezepte des Benutzers abrufen
      String countQuery = "SELECT COUNT(*) AS recipe_count FROM recipes WHERE user_id = ?";
      dbClient.queryWithParams(countQuery, new JsonArray().add(userId), countRes -> {
        if (countRes.succeeded()) {
          int recipeCount = countRes.result().getRows().get(0).getInteger("recipe_count");

          // Erfolg basierend auf der Anzahl der Rezepte vergeben
          String achievementType = getAchievementType(recipeCount);
          if (achievementType != null) {
            String insertAchievementQuery = "INSERT INTO achievements (user_id, achievement_type) VALUES (?, ?)";
            dbClient.updateWithParams(insertAchievementQuery, new JsonArray().add(userId).add(achievementType), insertRes -> {
              if (insertRes.succeeded()) {
                context.response().setStatusCode(200).end("✅ Erfolg vergeben: " + achievementType);
              } else {
                context.response().setStatusCode(500).end("❌ Fehler beim Vergeben des Erfolgs!");
              }
            });
          } else {
            context.response().setStatusCode(200).end("⚠ Kein neuer Erfolg vergeben.");
          }
        } else {
          context.response().setStatusCode(500).end("❌ Fehler beim Abrufen der Rezeptanzahl!");
        }
      });
    });
  }

  public void getUserAchievements(RoutingContext context) {
    // Benutzer-ID aus der Session holen
    authService.getUserIdFromSession(context, userIdResult -> {
      if (userIdResult.failed() || userIdResult.result() == -1) {
        context.response().setStatusCode(401).end("❌ Nicht authentifiziert!");
        return;
      }

      int userId = userIdResult.result();

      // Erfolge des Benutzers abrufen
      String query = "SELECT achievement_type FROM achievements WHERE user_id = ? ORDER BY achieved_at DESC LIMIT 1";
      dbClient.queryWithParams(query, new JsonArray().add(userId), res -> {
        if (res.succeeded() && !res.result().getRows().isEmpty()) {
          String achievement = res.result().getRows().get(0).getString("achievement_type");
          context.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("achievement", achievement).encode());
        } else {
          context.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("achievement", "Keine Erfolge verfügbar.").encode());
        }
      });
    });
  }

  private String getAchievementType(int recipeCount) {
    if (recipeCount >= 1 && recipeCount <= 2) {
      return "Koch-Anfänger";
    } else if (recipeCount >= 3 && recipeCount <= 5) {
      return "Koch-Lehrling";
    } else if (recipeCount >= 6 && recipeCount <= 10) {
      return "Rezept-Experte";
    } else if (recipeCount > 10) {
      return "Goldener Stern-Koch";
    }
    return null;
  }

}



