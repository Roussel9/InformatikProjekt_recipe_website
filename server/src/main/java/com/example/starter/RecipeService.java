package com.example.starter;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.Promise;


import java.util.ArrayList;
import java.util.List;

public class RecipeService {
  private final JDBCClient dbClient;

  public RecipeService(Vertx vertx, JDBCClient dbClient) {
    this.dbClient = dbClient;
  }


  /**
   * @api {get} /search/recipes Sucht Rezepte anhand des Namens
   * @apiName SearchRecipes
   * @apiGroup Recipes
   *
   * @apiDescription Diese Route sucht Rezepte anhand des Namens und gibt eine HTML-Seite mit den Suchergebnissen zurück.
   *
   * @apiParam {String} name Der Name des Rezepts, nach dem gesucht werden soll.
   *
   * @apiSuccess {HTML} page Eine HTML-Seite mit den Suchergebnissen.
   *
   * @apiError (Bad Request 400) BadRequest Es wurde kein Suchbegriff angegeben.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während die Rezepte gesucht wurden.
   */
  public void searchRecipes(RoutingContext context) {
    String recipeName = context.request().getParam("name");

    if (recipeName == null || recipeName.isEmpty()) {
      context.response().setStatusCode(400).end("<div class='alert alert-danger text-center'>Bitte geben Sie einen Rezeptnamen ein.</div>");
      return;
    }

    // SQL-Abfrage mit Zutaten und Portionen

    String query = "SELECT r.id AS id, r.title, r.description, r.image_url, r.portions, " +
      "GROUP_CONCAT(CONCAT(i.name, ' (', ri.amount, ' ', ri.unit, ')') SEPARATOR '|') AS ingredients " +
      "FROM recipes r " +
      "LEFT JOIN recipe_ingredients ri ON r.id = ri.recipe_id " +
      "LEFT JOIN ingredients i ON ri.ingredient_id = i.id " +
      "WHERE r.title LIKE ? " +
      "GROUP BY r.id";


    JsonArray params = new JsonArray().add("%" + recipeName + "%");

    dbClient.queryWithParams(query, params, res -> {
      if (res.succeeded()) {
        List<JsonObject> recipes = res.result().getRows();
        context.response().putHeader("content-type", "text/html; charset=UTF-8");

        StringBuilder htmlResponse = new StringBuilder();

        // Navigation + Header
        htmlResponse.append("<!DOCTYPE html><html lang='de'><head>")
          .append("<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>")
          .append("<title>Suchergebnisse</title>")
          .append("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>")
          .append("</head><body>")
          .append("<nav class='navbar navbar-expand-lg navbar-dark bg-success shadow'><div class='container'>")
          .append("<a class='navbar-brand' href='/'>Rezeptseite</a>")
          .append("<a class='btn btn-light' href='/homePage.html'>Zurück zur Startseite</a>")
          .append("</div></nav>")
          .append("<div class='container mt-5'><h2 class='text-success text-center'>Suchergebnisse für: <span class='fw-bold'>" + recipeName + "</span></h2>");

        if (recipes.isEmpty()) {
          htmlResponse.append("<div class='alert alert-warning text-center' role='alert'><h3>Kein passendes Rezept gefunden.</h3>")
            .append("<p>Versuchen Sie es mit einem anderen Suchbegriff.</p></div>");
        } else {
          htmlResponse.append("<div class='row'>");
          for (JsonObject recipe : recipes) {
            htmlResponse.append("<div class='col-md-4'>")
              .append("<div class='card shadow-lg mb-4 border-0'>")
              .append("<img src='").append(recipe.getString("image_url") != null ? recipe.getString("image_url") : "placeholder.jpg")
              .append("' class='card-img-top rounded-top' alt='Rezeptbild' style='height: 200px; object-fit: cover;'>")
              .append("<div class='card-body'>")
              .append("<h5 class='card-title text-success fw-bold'>").append(recipe.getString("title")).append("</h5>")
              .append("<p class='card-text text-muted'>").append(recipe.getString("description")).append("</p>");

            // Portionen anzeigen
            htmlResponse.append("<p><strong>Portionen:</strong> ").append(recipe.getInteger("portions")).append("</p>");

            // Zutatenliste als UL
            String ingredients = recipe.getString("ingredients");
            if (ingredients != null && !ingredients.isEmpty()) {
              htmlResponse.append("<h6 class='text-success fw-bold mt-3'>Zutaten:</h6><ul class='text-muted'>");
              for (String ingredient : ingredients.split("\\|")) {
                htmlResponse.append("<li>").append(ingredient).append("</li>");
              }
              htmlResponse.append("</ul>");
            }

            // Buttons (Einkaufsliste + Favoriten + Kommentare mit Fetch)
            htmlResponse.append("<a href='/recipes/").append(recipe.getInteger("id")).append("' class='btn btn-outline-success w-100 mb-2'>📖 Zur Einkaufsliste hinzufügen</a>")
              .append("<button class='btn btn-success w-100 favorite-btn' data-recipe-id='").append(recipe.getInteger("id")).append("'>❤️ Zu Favoriten hinzufügen</button>")
              // Im Suchmethode, beim Generieren des Kommentar-Buttons:
              .append("<button class='btn btn-primary w-100 comment-btn' data-recipe-id='" + recipe.getInteger("id") + "'>💬 Kommentare </button>")
              //.append("<button class='btn btn-primary w-100 comment-btn' data-recipe-id='" + recipe.getString("id") + "'>💬 Kommentare </button>")
              .append("<div class='comments-container' id='comments-" + recipe.getString("id") + "' style='display: none; margin-top: 10px;'></div>")
              // .append("<div class='comments-list' id='comments-" + recipe.getInteger("id") + "'></div>")
              //.append("<textarea class='form-control mt-2' placeholder='Schreiben Sie einen Kommentar'></textarea>")
              //.append("<button class='btn btn-sm btn-primary mt-1 submit-comment' data-recipe-id='" + recipe.getInteger("id") + "'>Posten</button>")
              .append("</div>")
              .append("</div></div>");
            //.append("</div></div></div>");
          }
          htmlResponse.append("</div>");
        }

        // Footer
        htmlResponse.append("</div>")
          .append("<footer class='bg-dark text-white text-center py-4 mt-5'>")
          .append("<p>&copy; 2025 Rezeptseite. Alle Rechte vorbehalten.</p>")
          .append("</footer>")
          .append("<script>")
          .append("document.querySelectorAll('.favorite-btn').forEach(button => {")
          .append("  button.addEventListener('click', function(event) {")
          .append("    event.preventDefault();")
          .append("    let recipeId = this.getAttribute('data-recipe-id');")
          .append("    fetch(`/favorites/` + recipeId, { method: 'POST', credentials: 'include' })")
          .append("      .then(response => response.text())")
          .append("      .then(data => alert(data))")
          .append("      .catch(err => console.error('Fehler:', err));")
          .append("  });")
          .append("});");


        htmlResponse.append("document.querySelectorAll('.comment-btn').forEach(button => {")
          .append("  button.addEventListener('click', function(event) {")
          .append("    event.preventDefault();")
          .append("    let recipeId = this.getAttribute('data-recipe-id');")
          .append("    let commentSection = document.getElementById('comments-' + recipeId);")
          .append("    if (commentSection.style.display === 'none') {")
          .append("    }")
          .append("  });")
          .append("});")
          .append("</script>");

        htmlResponse.append("<script>")
          .append("document.querySelectorAll('.comment-btn').forEach(button => {")
          .append("  button.addEventListener('click', function(event) {")
          .append("    event.preventDefault();")
          .append("    let recipeId = this.getAttribute('data-recipe-id');")
          .append("    window.location.href = '/comment.html?recipeId=' + recipeId;")
          .append("  });")
          .append("});")
          .append("</script>")
          .append("<script>")
          .append("document.querySelectorAll('.comment-btn').forEach(button => {")
          .append("  button.addEventListener('click', function() {")
          .append("    let recipeId = this.getAttribute('data-recipe-id');")
          .append("    let commentSection = document.getElementById('comments-' + recipeId);")
          .append("    if (commentSection.innerHTML === '') {")
          .append("      fetch('/comments?recipeId=' + recipeId)")
          .append("        .then(response => response.json())")
          .append("        .then(comments => {")
          .append("          comments.forEach(comment => {")
          .append("            let commentHtml = `<div class='comment-item'><p><strong>${comment.username}:</strong> ${comment.content}</p>`;")
          .append("            if (comment.isOwner) {")
          .append("              commentHtml += `<button class='btn btn-sm btn-warning edit-comment' data-comment-id='${comment.id}'>✏️ Bearbeiten</button>`;")
          .append("              commentHtml += `<button class='btn btn-sm btn-danger delete-comment' data-comment-id='${comment.id}'>🗑️ Löschen</button>`;")
          .append("              commentHtml += `<div class='edit-form' id='edit-${comment.id}' style='display:none;'>")
          .append("                                <textarea class='form-control edit-content'>${comment.content}</textarea>")
          .append("                                <button class='btn btn-sm btn-success save-comment' data-comment-id='${comment.id}'>💾 Speichern</button>")
          .append("                              </div>`;")
          .append("            }")
          .append("            commentHtml += `</div>`;")
          .append("            commentSection.innerHTML += commentHtml;")
          .append("          });")
          .append("          commentSection.style.display = 'block';")
          .append("        });")
          .append("    } else {")
          .append("      commentSection.style.display = (commentSection.style.display === 'none') ? 'block' : 'none';")
          .append("    }")
          .append("  });")
          .append("});")
          .append("</script>");

        htmlResponse.append("<script>")
          .append("document.body.addEventListener('click', function(event) {")
          .append("  if (event.target.classList.contains('edit-comment')) {")
          .append("    let commentId = event.target.getAttribute('data-comment-id');")
          .append("    let editForm = document.getElementById(`edit-${commentId}`);")
          .append("    editForm.style.display = editForm.style.display === 'none' ? 'block' : 'none';")
          .append("  }")
          .append("  if (event.target.classList.contains('save-comment')) {")
          .append("    let commentId = event.target.getAttribute('data-comment-id');")
          .append("    let newContent = document.querySelector(`#edit-${commentId} .edit-content`).value;")
          .append("    fetch(`/comments/${commentId}`, {")
          .append("      method: 'PUT',")
          .append("      headers: { 'Content-Type': 'application/json' },")
          .append("      credentials: 'include',")
          .append("      body: JSON.stringify({ content: newContent })")
          .append("    })")
          .append("    .then(response => response.json())")
          .append("    .then(data => {")
          .append("      if (data.success) {")
          .append("        document.querySelector(`.comment-item[data-comment-id='${commentId}'] p`).innerHTML = `<strong>${data.username}:</strong> ${data.content}`;")
          .append("        document.getElementById(`edit-${commentId}`).style.display = 'none';")
          .append("      } else { alert('Fehler: ' + data.error); }")
          .append("    })")
          .append("    .catch(error => console.error('Fehler:', error));")
          .append("  }")
          .append("});")

          .append("document.querySelectorAll('.comment-btn').forEach(button => {")
          .append("  button.addEventListener('click', function(event) {")
          .append("    event.preventDefault();")
          .append("    let recipeId = this.getAttribute('data-recipe-id');")
          .append("    let commentSection = document.getElementById('comments-' + recipeId);")
          .append("    if (commentSection.innerHTML === '') {")
          .append("      fetch('/comments?recipeId=' + recipeId)")
          .append("        .then(response => response.json())")
          .append("        .then(comments => {")
          .append("          comments.forEach(comment => {")
          .append("            let commentHtml = `<div class='comment-item' data-comment-id='${comment.id}'>")
          .append("              <p><strong>${comment.username}:</strong> ${comment.content}</p>`;")
          .append("            if (comment.isOwner) {")
          .append("              commentHtml += `<button class='btn btn-sm btn-warning edit-comment' data-comment-id='${comment.id}'>✏️ Bearbeiten</button>`;")
          .append("              commentHtml += `<button class='btn btn-sm btn-danger delete-comment' data-comment-id='${comment.id}'>🗑️ Löschen</button>`;")
          .append("            }")
          .append("            commentHtml += `</div>`;")
          .append("            commentSection.innerHTML += commentHtml;")
          .append("          });")
          .append("          commentSection.style.display = 'block';")
          .append("        });")
          .append("    }")
          .append("  });")
          .append("});")
          .append("</script>");



        htmlResponse.append("<script>")
          .append("document.querySelectorAll('.edit-comment').forEach(button => {")
          .append("  button.addEventListener('click', function() {")
          .append("    let commentId = this.getAttribute('data-comment-id');")
          .append("    let editForm = document.getElementById('edit-' + commentId);")
          .append("    editForm.style.display = (editForm.style.display === 'none') ? 'block' : 'none';")
          .append("  });")
          .append("});")

          .append("document.querySelectorAll('.save-comment').forEach(button => {")
          .append("  button.addEventListener('click', function() {")
          .append("    let commentId = this.getAttribute('data-comment-id');")
          .append("    let newContent = document.querySelector('#edit-' + commentId + ' .edit-content').value;")
          .append("    fetch('/comments/' + commentId, {")
          .append("      method: 'PUT',")
          .append("      headers: { 'Content-Type': 'application/json' },")
          .append("      body: JSON.stringify({ content: newContent })")
          .append("    })")
          .append("    .then(response => response.json())")
          .append("    .then(data => {")
          .append("      if (data.success) {")
          .append("        alert('Kommentar erfolgreich aktualisiert!');")
          .append("        document.querySelector('.comment-item[data-comment-id=\"' + commentId + '\"] p').innerHTML = '<strong>' + data.username + ':</strong> ' + newContent;")
          .append("        document.getElementById('edit-' + commentId).style.display = 'none';")
          .append("      } else {")
          .append("        alert('Fehler beim Speichern des Kommentars.');")
          .append("      }")
          .append("    })")
          .append("    .catch(err => console.error('Fehler:', err));")
          .append("  });")
          .append("});")
          .append("</script>")





          .append("</body></html>");



        context.response().end(htmlResponse.toString());
      } else {
        context.response().setStatusCode(500).end("<div class='alert alert-danger text-center'>Fehler bei der Rezeptsuche.</div>");
      }
    });
  }


  public void deleteRecipe(RoutingContext context) {
    // Prüfen, ob der Benutzer authentifiziert ist
    if (context.user() == null) {
      context.response().setStatusCode(401).end("❌ Benutzer nicht authentifiziert!");
      return;
    }

    // Benutzer-ID aus dem Token extrahieren
    String tokenUserId = context.user().principal().getString("userId");
    if (tokenUserId == null) {
      context.response().setStatusCode(401).end("❌ Kein Benutzer im Token gefunden!");
      return;
    }

    // Rezept-ID aus der URL extrahieren
    String recipeId = context.pathParam("recipe_id");
    if (recipeId == null) {
      context.response().setStatusCode(400).end("⚠ Rezept-ID ist erforderlich!");
      return;
    }

    // Benutzer-ID aus der URL holen (optional, wenn nur der Besitzer das Rezept löschen darf)
    String pathUserId = context.pathParam("user_id");

    // Überprüfen, ob der Benutzer zu diesem Rezept gehört
    if (pathUserId != null && !pathUserId.equals(tokenUserId)) {
      context.response().setStatusCode(403).end("❌ Du darfst dieses Rezept nicht löschen!");
      return;
    }

    // SQL-Query, um das Rezept zu löschen
    String query = "DELETE FROM recipes WHERE id = ? AND user_id = ?";
    JsonArray params = new JsonArray().add(recipeId).add(tokenUserId);


    dbClient.updateWithParams(query, params, res -> {
      if (res.succeeded()) {
        UpdateResult updateResult = res.result(); // Das UpdateResult holen

        // Überprüfen, ob UpdateResult korrekt ist und ob eine Zeile betroffen wurde
        if (updateResult != null && updateResult.getUpdated() > 0) {  // getUpdated() gibt die Anzahl der betroffenen Zeilen zurück
          // Rezept erfolgreich gelöscht
          context.response()
            .setStatusCode(302)  // Redirect
            .putHeader("Location", "/meine-rezepte.html")  // Zur Rezeptliste zurückleiten
            .end();

        } else {
          context.response().setStatusCode(404).end("❌ Rezept nicht gefunden oder Du bist nicht der Ersteller!");
        }
      } else {
        context.response().setStatusCode(500).end("❌ Fehler beim Löschen des Rezepts: " + res.cause().getMessage());
      }
    });
  }

  /**
   * @api {post} /recipes Neues Rezept erstellen
   * @apiName CreateRecipe
   * @apiGroup Recipes
   * @apiDescription Erstellt ein neues Rezept für den authentifizierten Benutzer.
   *
   * @apiHeader {String} Authorization Bearer-Token für die Authentifizierung.
   *
   * @apiBody {String} title Der Titel des Rezepts (Pflichtfeld).
   * @apiBody {String} description Die Beschreibung des Rezepts (Pflichtfeld).
   * @apiBody {Number} portions Die Anzahl der Portionen (Pflichtfeld).
   *
   * @apiSuccess  {String} message ✅ Rezept erfolgreich erstellt!
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 201 Created
   *     {
   *       "message": "✅ Rezept erfolgreich erstellt!",
   *       "recipeId": 42
   *     }
   *
   * @apiError (Alternative Case) {String} message ⚠ Alle Felder sind erforderlich!
   * @apiErrorExample {json} Fehlende Eingaben:
   *     HTTP/1.1 400 Bad Request
   *     {
   *       "message": "⚠ Alle Felder sind erforderlich!"
   *     }
   *
   * @apiError  {String} message ❌ Nicht authentifiziert!
   * @apiErrorExample {json} Benutzer nicht eingeloggt:
   *     HTTP/1.1 401 Unauthorized
   *     {
   *       "message": "❌ Nicht authentifiziert!"
   *     }
   *
   * @apiError  {String} message ❌ Rezept konnte nicht gespeichert werden!
   * @apiErrorExample {json} Datenbankfehler:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "❌ Rezept konnte nicht gespeichert werden!"
   *     }
   */

  public void createRecipe(RoutingContext context) {
    // Authentifizierung prüfen
    if (context.user() == null) {
      context.response().setStatusCode(401).end("❌ Nicht authentifiziert!");
      return;
    }
    String userId = context.user().principal().getString("userId");

    // Formulardaten lesen
    String title = context.request().getFormAttribute("title");
    String description = context.request().getFormAttribute("description");
    int portions = Integer.parseInt(context.request().getFormAttribute("portions"));
    String imageUrl = "placeholder.jpg"; // Hier ggf. Bild-Upload verarbeiten

    // Rezept in Datenbank einfügen
    String insertRecipe = "INSERT INTO recipes (user_id, title, description, portions, image_url) VALUES (?, ?, ?, ?, ?)";
    JsonArray params = new JsonArray().add(userId).add(title).add(description).add(portions).add(imageUrl);

    dbClient.updateWithParams(insertRecipe, params, res -> {
      if (res.succeeded()) {
        int recipeId = res.result().getKeys().getInteger(0);
        processIngredients(context, recipeId); // Zutaten verarbeiten
      } else {
        context.response().setStatusCode(500).end("❌ Rezept konnte nicht gespeichert werden!");
      }
    });
  }

  private void processIngredients(RoutingContext context, int recipeId) {
    List<Future> futures = new ArrayList<>();

    List<String> names = context.request().params().getAll("ingredient_name[]");
    List<String> amounts = context.request().params().getAll("ingredient_amount[]");
    List<String> units = context.request().params().getAll("ingredient_unit[]");

    for (int i = 0; i < names.size(); i++) {

      String name = names.get(i);
      if (name == null || name.isEmpty()) continue;
      System.out.println("Zutat wird verarbeitet: " + name + ", " + amounts.get(i) + " " + units.get(i));


      // Zutat in 'ingredients' einfügen oder vorhandene ID holen
      String checkIngredient = "SELECT id FROM ingredients WHERE name = ?";
      int finalI = i;
      Promise<Integer> promise = Promise.promise();
      futures.add(promise.future());

      dbClient.queryWithParams(checkIngredient, new JsonArray().add(name), checkRes -> {
        if (checkRes.succeeded() && !checkRes.result().getRows().isEmpty()) {
          int ingredientId = checkRes.result().getRows().get(0).getInteger("id");
          linkIngredient(recipeId, ingredientId, amounts.get(finalI), units.get(finalI));
          promise.complete();
        } else {
          String insertIngredient = "INSERT INTO ingredients (name) VALUES (?)";

          dbClient.updateWithParams(insertIngredient, new JsonArray().add(name), insertRes -> {
            if (insertRes.succeeded() && insertRes.result().getKeys().size() > 0) {
              int ingredientId = insertRes.result().getKeys().getInteger(0);
              linkIngredient(recipeId, ingredientId, amounts.get(finalI), units.get(finalI));
              promise.complete();
            } else {
              promise.fail("Fehler beim Einfügen der Zutat: " + name);
            }
          });
        }
      });

    }
    // Warten, bis alle Zutaten gespeichert wurden
    CompositeFuture.all(futures).onComplete(result -> {
      if (result.succeeded()) {
        context.response().setStatusCode(303).putHeader("Location", "/profil.html").end();
      } else {
        context.response().setStatusCode(500).end("Fehler beim Speichern der Zutaten!");
      }
    });

  }

  private void linkIngredient(int recipeId, int ingredientId, String amount, String unit) {
    String sql = "INSERT INTO recipe_ingredients (recipe_id, ingredient_id, amount, unit) VALUES (?, ?, ?, ?)";
    dbClient.updateWithParams(sql, new JsonArray().add(recipeId).add(ingredientId).add(amount).add(unit), res -> {
      if (res.succeeded()) {
        System.out.println("✅ Zutat erfolgreich mit Rezept verknüpft: " + ingredientId);
      } else {
        System.out.println("❌ Fehler beim Verknüpfen der Zutat mit Rezept!");
      }
    });

    //dbClient.updateWithParams(sql, new JsonArray().add(recipeId).add(ingredientId).add(amount).add(unit), res -> {});
  }

  /**
   * @api {get} /users/:user_id/recipes Gibt alle Rezepte eines Benutzers zurück
   * @apiName GetAllRecipes
   * @apiGroup Recipes
   *
   * @apiDescription Diese Route gibt alle Rezepte eines bestimmten Benutzers zurück, einschließlich Titel, Beschreibung, Portionen und Bild-URL.
   *
   * @apiParam {Number} user_id Die ID des Benutzers, dessen Rezepte abgerufen werden sollen.
   *
   * @apiSuccess {JSON} response Ein JSON-Objekt mit einer Erfolgsmeldung und einer Liste der Rezepte.
   * @apiSuccessExample {json} Erfolgsantwort:
   *     {
   *       "message": "✅ Rezepte gefunden!",
   *       "recipes": [
   *         {
   *           "id": 1,
   *           "title": "Spaghetti Carbonara",
   *           "description": "Ein klassisches italienisches Gericht.",
   *           "portions": 4,
   *           "image_url": "https://example.com/spaghetti.jpg"
   *         },
   *         {
   *           "id": 2,
   *           "title": "Pizza Margherita",
   *           "description": "Eine einfache und leckere Pizza.",
   *           "portions": 2,
   *           "image_url": "https://example.com/pizza.jpg"
   *         }
   *       ]
   *     }
   *
   * @apiError (Bad Request 400) BadRequest Es wurde keine Benutzer-ID angegeben.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während die Rezepte abgerufen wurden.
   */
  public void getAllRecipes(RoutingContext context) {
    // Benutzer-ID aus der URL (Path-Param) holen
    String pathUserId = context.pathParam("user_id");

    // Überprüfen, ob die Benutzer-ID aus der URL vorhanden ist
    if (pathUserId == null) {
      context.response().setStatusCode(400).end("⚠ Benutzer-ID fehlt!");
      return;
    }

    // SQL-Query zur Abfrage der Rezepte des Benutzers
    String query = "SELECT id, title, description, portions, image_url FROM recipes WHERE user_id = ?";
    JsonArray params = new JsonArray().add(pathUserId);

    dbClient.queryWithParams(query, params, res -> {
      if (res.succeeded()) {
        // Ergebnisse aus der Datenbank holen
        JsonArray recipes = new JsonArray();
        res.result().getRows().forEach(row -> {
          JsonObject recipe = new JsonObject()
                  .put("id", row.getInteger("id"))
                  .put("title", row.getString("title"))
                  .put("description", row.getString("description"))
                  .put("portions", row.getInteger("portions"))
                  .put("image_url", row.getString("image_url"));
          recipes.add(recipe);
        });

        // Antwort zurück an den Client
        JsonObject responseJson = new JsonObject()
                .put("message", "✅ Rezepte gefunden!")
                .put("recipes", recipes);

        context.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(responseJson.encode());
      } else {
        context.response().setStatusCode(500).end("❌ Fehler beim Abrufen der Rezepte: " + res.cause().getMessage());
      }
    });
  }


  /**
   * @api {get} /users/:user_id/recipes/:recipe_id Gibt ein spezifisches Rezept eines Benutzers zurück
   * @apiName GetRecipeByUserAndById
   * @apiGroup Recipes
   *
   * @apiDescription Diese Route gibt ein spezifisches Rezept eines bestimmten Benutzers zurück, einschließlich Titel, Beschreibung, Portionen und Bild-URL.
   *
   * @apiParam {Number} user_id Die ID des Benutzers, dem das Rezept gehört.
   * @apiParam {Number} recipe_id Die ID des Rezepts, das abgerufen werden soll.
   *
   * @apiSuccess {JSON} recipe Das Rezept im JSON-Format.
   * @apiSuccessExample {json} Erfolgsantwort:
   *     {
   *       "id": 1,
   *       "title": "Spaghetti Carbonara",
   *       "description": "Ein klassisches italienisches Gericht.",
   *       "portions": 4,
   *       "image_url": "https://example.com/spaghetti.jpg"
   *     }
   *
   * @apiError (Bad Request 400) BadRequest Es wurde keine Benutzer-ID oder Rezept-ID angegeben.
   * @apiError (Not Found 404) NotFound Das Rezept wurde nicht gefunden.
   * @apiError (Server Error 500) ServerError Ein Fehler ist aufgetreten, während das Rezept abgerufen wurde.
   */
  public void getRecipeByUserAndById(RoutingContext context) {
    // Rezept-ID aus der URL extrahieren
    String recipeId = context.pathParam("recipe_id");
    if (recipeId == null) {
      context.response().setStatusCode(400).end("⚠ Rezept-ID ist erforderlich!");
      return;
    }

    // Benutzer-ID aus der URL extrahieren
    String pathUserId = context.pathParam("user_id");

    // Überprüfen, ob die Benutzer-ID aus der URL vorhanden ist
    if (pathUserId == null) {
      context.response().setStatusCode(400).end("⚠ Benutzer-ID fehlt!");
      return;
    }

    // SQL-Query zur Abrufung des Rezepts basierend auf der Rezept-ID und Benutzer-ID
    String query = "SELECT id, title, description, portions, image_url FROM recipes WHERE id = ? AND user_id = ?";
    JsonArray params = new JsonArray().add(recipeId).add(pathUserId);

    dbClient.queryWithParams(query, params, res -> {
      if (res.succeeded()) {
        if (res.result().getRows().isEmpty()) {
          context.response().setStatusCode(404).end("❌ Rezept nicht gefunden!");
          return;
        }

        // Rezept gefunden, gebe es zurück
        JsonObject recipe = res.result().getRows().get(0);

        context.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(recipe.encode());
      } else {
        context.response().setStatusCode(500).end("❌ Fehler beim Abrufen des Rezepts: " + res.cause().getMessage());
      }
    });
  }


  /**
   * @api {get} /users/:user_id/recipes Rezepte eines Nutzers abrufen
   * @apiName GetRecipesByUser
   * @apiGroup Recipes
   * @apiVersion 1.0.0
   * @apiDescription Ruft alle Rezepte eines bestimmten Nutzers ab und gibt sie als HTML-Seite zurück.
   *
   * @apiParam {Number} user_id Die ID des Nutzers, dessen Rezepte abgerufen werden sollen.
   *
   * @apiSuccess  {HTML} HTML-Seite mit einer Liste der Rezepte des Nutzers.
   * @apiSuccessExample {html} Erfolgreiche Antwort:
   *     HTTP/1.1 200 OK
   *     Content-Type: text/html
   *     <!DOCTYPE html>
   *     <html lang='de'>
   *     <head><title>Rezepte des Nutzers</title></head>
   *     <body>
   *       <h2>Rezepte von Nutzer 123</h2>
   *       <div class="card">
   *         <h5>Pizza Margherita</h5>
   *         <p>Leckere Pizza mit Tomatensauce und Mozzarella.</p>
   *         <p><strong>Portionen:</strong> 2</p>
   *         <img src="pizza.jpg" alt="Rezeptbild">
   *       </div>
   *     </body>
   *     </html>
   *
   * @apiError (Alternative Case) {HTML} HTML-Seite mit der Nachricht "⚠ Keine Rezepte gefunden!"
   * @apiErrorExample {html} Keine Rezepte vorhanden:
   *     HTTP/1.1 200 OK
   *     Content-Type: text/html
   *     <p class='text-center'>⚠ Keine Rezepte gefunden!</p>
   *
   * @apiError  {String} message ❌ Benutzer-ID fehlt!
   * @apiErrorExample {json} Fehlende Benutzer-ID:
   *     HTTP/1.1 400 Bad Request
   *     {
   *       "message": "⚠ Benutzer-ID fehlt!"
   *     }
   *
   * @apiError  {String} message ❌ Fehler beim Abrufen der Rezepte!
   * @apiErrorExample {json} Datenbankfehler:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "❌ Fehler beim Abrufen der Rezepte!"
   *     }
   */

  public void getRecipesByUser(RoutingContext context) {
    String userId = context.pathParam("user_id");

    if (userId == null) {
      context.response().setStatusCode(400).end("⚠ Benutzer-ID fehlt!");
      return;
    }

    String query = "SELECT id, title, description, portions, image_url FROM recipes WHERE user_id = ?";

    dbClient.queryWithParams(query, new JsonArray().add(userId), res -> {
      if (res.succeeded()) {
        List<JsonObject> recipes = res.result().getRows();

        // HTML-Seite für die Rezepte
        StringBuilder htmlResponse = new StringBuilder("<!DOCTYPE html>");
        htmlResponse.append("<html lang='de'><head>")
          .append("<meta charset='UTF-8'>")
          .append("<title>Rezepte des Nutzers</title>")
          .append("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>")
          .append("</head><body>")
          .append("<div class='container mt-5'>")
          .append("<h2 class='text-success text-center'>Rezepte von Nutzer ").append(userId).append("</h2>");

        // Falls der Nutzer keine Rezepte hat
        if (recipes.isEmpty()) {
          htmlResponse.append("<p class='text-center'>⚠ Keine Rezepte gefunden!</p>");
        } else {
          for (JsonObject recipe : recipes) {
            htmlResponse.append("<div class='card shadow mb-3'>")
              .append("<div class='card-body'>")
              .append("<h5 class='card-title'>").append(recipe.getString("title")).append("</h5>")
              .append("<p class='card-text'>").append(recipe.getString("description")).append("</p>")
              .append("<p class='card-text'><strong>Portionen:</strong> ").append(recipe.getInteger("portions")).append("</p>");

            // Falls das Rezept ein Bild hat
            if (recipe.getString("image_url") != null) {
              htmlResponse.append("<img src='").append(recipe.getString("image_url")).append("' class='img-fluid' alt='Rezeptbild'>");
            }

            htmlResponse.append("</div></div>");
          }
        }

        htmlResponse.append("</div></body></html>");

        context.response().putHeader("Content-Type", "text/html").end(htmlResponse.toString());

      } else {
        context.response().setStatusCode(500).end("❌ Fehler beim Abrufen der Rezepte!");
      }
    });
  }


  /**
   * @api {get} /recipes/:recipe_id Rezept abrufen
   * @apiName GetRecipeById
   * @apiGroup Recipes
   * @apiDescription Ruft ein Rezept basierend auf der Rezept-ID ab.
   *
   * @apiParam {Number} recipe_id Die ID des gewünschten Rezepts.
   *
   * @apiSuccess  {Number} id Die ID des Rezepts.
   * @apiSuccess  {String} title Der Titel des Rezepts.
   * @apiSuccess  {String} description Die Beschreibung des Rezepts.
   * @apiSuccess  {Number} portions Die Anzahl der Portionen.
   * @apiSuccess  {String} image_url Die URL des Rezeptbildes.
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 200 OK
   *     {
   *       "id": 42,
   *       "title": "Spaghetti Bolognese",
   *       "description": "Klassisches italienisches Rezept.",
   *       "portions": 4,
   *       "image_url": "https://example.com/spaghetti.jpg"
   *     }
   *
   * @apiError (Alternative Case) {String} message ⚠ Rezept-ID ist erforderlich!
   * @apiErrorExample {json} Fehlende Rezept-ID:
   *     HTTP/1.1 400 Bad Request
   *     {
   *       "message": "⚠ Rezept-ID ist erforderlich!"
   *     }
   *
   * @apiError {String} message ❌ Rezept nicht gefunden!
   * @apiErrorExample {json} Rezept nicht vorhanden:
   *     HTTP/1.1 404 Not Found
   *     {
   *       "message": "❌ Rezept nicht gefunden!"
   *     }
   *
   * @apiError {String} message ❌ Fehler beim Abrufen des Rezepts!
   * @apiErrorExample {json} Datenbankfehler:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "❌ Fehler beim Abrufen des Rezepts!"
   *     }
   */

  public void getRecipeById(RoutingContext context) {
    // Rezept-ID aus der URL extrahieren
    String recipeId = context.pathParam("recipe_id");
    if (recipeId == null) {
      context.response().setStatusCode(400).end("⚠ Rezept-ID ist erforderlich!");
      return;
    }

    // SQL-Query zur Abrufung des Rezepts basierend auf der Rezept-ID
    String query = "SELECT id, title, description, portions, image_url FROM recipes WHERE id = ?";
    JsonArray params = new JsonArray().add(recipeId);

    dbClient.queryWithParams(query, params, res -> {
      if (res.succeeded()) {
        if (res.result().getRows().isEmpty()) {
          context.response().setStatusCode(404).end("❌ Rezept nicht gefunden!");
          return;
        }

        // Rezept gefunden, gebe es zurück
        JsonObject recipe = res.result().getRows().get(0);

        context.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(recipe.encode());
      } else {
        context.response().setStatusCode(500).end("❌ Fehler beim Abrufen des Rezepts: " + res.cause().getMessage());
      }
    });
  }


  /**
   * @api {put} /users/:user_id/recipes/:recipe_id Rezept vollständig aktualisieren
   * @apiName UpdateRecipePut
   * @apiGroup Recipes
   * @apiDescription Aktualisiert ein bestehendes Rezept vollständig. Der Benutzer muss authentifiziert sein und das Rezept besitzen.
   *
   * @apiHeader {String} Authorization Bearer-Token für die Authentifizierung.
   *
   * @apiParam {Number} user_id Die ID des Benutzers, dem das Rezept gehört.
   * @apiParam {Number} recipe_id Die ID des zu aktualisierenden Rezepts.
   *
   * @apiBody {String} title Der neue Titel des Rezepts (Pflichtfeld).
   * @apiBody {String} description Die neue Beschreibung des Rezepts (Pflichtfeld).
   * @apiBody {Number} portions Die neue Anzahl der Portionen (Pflichtfeld).
   * @apiBody {String} image_url Die neue Bild-URL des Rezepts (Pflichtfeld).
   *
   * @apiSuccess  {String} message ✅ Rezept vollständig aktualisiert!
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 200 OK
   *     {
   *       "message": "✅ Rezept vollständig aktualisiert!"
   *     }
   *
   * @apiError (Alternative Case) {String} message ⚠ Alle Felder (Titel, Beschreibung, Portionen, Bild-URL) sind erforderlich!
   * @apiErrorExample {json} Fehlende Eingaben:
   *     HTTP/1.1 400 Bad Request
   *     {
   *       "message": "⚠ Alle Felder (Titel, Beschreibung, Portionen, Bild-URL) sind erforderlich!"
   *     }
   *
   * @apiError  {String} message ❌ Benutzer nicht authentifiziert!
   * @apiErrorExample {json} Nicht eingeloggt:
   *     HTTP/1.1 401 Unauthorized
   *     {
   *       "message": "❌ Benutzer nicht authentifiziert!"
   *     }
   *
   * @apiError  {String} message ❌ Du darfst dieses Rezept nicht bearbeiten!
   * @apiErrorExample {json} Zugriff verweigert:
   *     HTTP/1.1 403 Forbidden
   *     {
   *       "message": "❌ Du darfst dieses Rezept nicht bearbeiten!"
   *     }
   *
   * @apiError  {String} message ❌ Fehler beim Aktualisieren des Rezepts!
   * @apiErrorExample {json} Datenbankfehler:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "❌ Fehler beim Aktualisieren des Rezepts!"
   *     }
   */

  public void updateRecipePut(RoutingContext context) {
    // Prüfen, ob der Benutzer authentifiziert ist
    if (context.user() == null) {
      context.response().setStatusCode(401).end("❌ Benutzer nicht authentifiziert!");
      return;
    }

    // Benutzer-ID aus dem Token extrahieren
    String tokenUserId = context.user().principal().getString("userId");
    if (tokenUserId == null) {
      context.response().setStatusCode(401).end("❌ Kein Benutzer im Token gefunden!");
      return;
    }

    // Rezept-ID aus der URL extrahieren
    String recipeId = context.pathParam("recipe_id");
    if (recipeId == null) {
      context.response().setStatusCode(400).end("⚠ Rezept-ID ist erforderlich!");
      return;
    }

    // Benutzer-ID aus der URL holen (optional, wenn nur der Besitzer das Rezept bearbeiten darf)
    String pathUserId = context.pathParam("user_id");

    // Überprüfen, ob der Benutzer zu diesem Rezept gehört
    if (pathUserId != null && !pathUserId.equals(tokenUserId)) {
      context.response().setStatusCode(403).end("❌ Du darfst dieses Rezept nicht bearbeiten!");
      return;
    }

    // Rezeptdaten aus dem Request-Body extrahieren
    JsonObject body = context.getBodyAsJson();
    String title = body.getString("title");
    String description = body.getString("description");
    Integer portions = body.getInteger("portions");
    String imageUrl = body.getString("image_url");

    // Prüfen, ob der Titel vorhanden ist (Titel ist erforderlich)
    if (title == null || description == null || portions == null || imageUrl == null) {
      context.response().setStatusCode(400).end("⚠ Alle Felder (Titel, Beschreibung, Portionen, Bild-URL) sind erforderlich!");
      return;
    }

    // SQL-Query zur vollständigen Aktualisierung des Rezepts
    String query = "UPDATE recipes SET title = ?, description = ?, portions = ?, image_url = ? WHERE id = ?";
    JsonArray params = new JsonArray().add(title).add(description).add(portions).add(imageUrl).add(recipeId);

    dbClient.updateWithParams(query, params, res -> {
      if (res.succeeded()) {
        // Rezept erfolgreich aktualisiert
        context.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("message", "✅ Rezept vollständig aktualisiert!").encode());
      } else {
        context.response().setStatusCode(500).end("❌ Fehler beim Aktualisieren des Rezepts: " + res.cause().getMessage());
      }
    });
  }



  /**
   * @api {patch} /users/:user_id/recipes/:recipe_id Rezept teilweise aktualisieren
   * @apiName UpdateRecipePatch
   * @apiGroup Recipes
   * @apiDescription Aktualisiert ein Rezept teilweise. Nur die angegebenen Felder werden geändert.
   *
   * @apiHeader {String} Authorization Bearer-Token zur Authentifizierung.
   *
   * @apiParam {Number} user_id Die ID des Benutzers (muss mit dem Token übereinstimmen).
   * @apiParam {Number} recipe_id Die ID des Rezepts, das aktualisiert werden soll.
   *
   * @apiBody {String} [title] Neuer Titel des Rezepts.
   * @apiBody {String} [description] Neue Beschreibung des Rezepts.
   * @apiBody {Number} [portions] Neue Anzahl der Portionen.
   * @apiBody {String} [image_url] Neue Bild-URL für das Rezept.
   *
   * @apiSuccess {String} message ✅ Rezept teilweise aktualisiert!
   * @apiSuccessExample {json} Erfolgreiche Antwort:
   *     HTTP/1.1 200 OK
   *     {
   *       "message": "✅ Rezept teilweise aktualisiert!"
   *     }
   *
   * @apiError (Unauthorized) {String} message ❌ Benutzer nicht authentifiziert!
   * @apiErrorExample {json} Fehlende Authentifizierung:
   *     HTTP/1.1 401 Unauthorized
   *     {
   *       "message": "❌ Benutzer nicht authentifiziert!"
   *     }
   *
   * @apiError (Forbidden) {String} message ❌ Du darfst dieses Rezept nicht bearbeiten!
   * @apiErrorExample {json} Kein Zugriff:
   *     HTTP/1.1 403 Forbidden
   *     {
   *       "message": "❌ Du darfst dieses Rezept nicht bearbeiten!"
   *     }
   *
   * @apiError (Bad Request) {String} message ⚠ Rezept-ID ist erforderlich!
   * @apiErrorExample {json} Fehlende Rezept-ID:
   *     HTTP/1.1 400 Bad Request
   *     {
   *       "message": "⚠ Rezept-ID ist erforderlich!"
   *     }
   *
   * @apiError (Server Error) {String} message ❌ Fehler beim Aktualisieren des Rezepts!
   * @apiErrorExample {json} Fehlerhafte Aktualisierung:
   *     HTTP/1.1 500 Internal Server Error
   *     {
   *       "message": "❌ Fehler beim Aktualisieren des Rezepts!"
   *     }
   */

  public void updateRecipePatch(RoutingContext context) {
    // Prüfen, ob der Benutzer authentifiziert ist
    if (context.user() == null) {
      context.response().setStatusCode(401).end("❌ Benutzer nicht authentifiziert!");
      return;
    }

    // Benutzer-ID aus dem Token extrahieren
    String tokenUserId = context.user().principal().getString("userId");
    if (tokenUserId == null) {
      System.out.println("Token User ID: " + tokenUserId);

      context.response().setStatusCode(401).end("❌ Kein Benutzer im Token gefunden!");
      return;
    }

    // Rezept-ID aus der URL extrahieren
    String recipeId = context.pathParam("recipe_id");
    if (recipeId == null) {
      context.response().setStatusCode(400).end("⚠ Rezept-ID ist erforderlich!");
      return;
    }

    // Benutzer-ID aus der URL holen (optional, wenn nur der Besitzer das Rezept bearbeiten darf)
    String pathUserId = context.pathParam("user_id");

    // Überprüfen, ob der Benutzer zu diesem Rezept gehört
    if (pathUserId != null && !pathUserId.equals(tokenUserId)) {
      context.response().setStatusCode(403).end("❌ Du darfst dieses Rezept nicht bearbeiten!");
      return;
    }

    // Rezeptdaten aus dem Request-Body extrahieren
    JsonObject body = context.getBodyAsJson();
    String title = body.getString("title");
    String description = body.getString("description");
    Integer portions = body.getInteger("portions");
    String imageUrl = body.getString("image_url");

    // SQL-Query zur partiellen Aktualisierung des Rezepts
    StringBuilder query = new StringBuilder("UPDATE recipes SET ");
    JsonArray params = new JsonArray();

    // Dynamisch nur die Felder hinzufügen, die vorhanden sind
    if (title != null) {
      query.append("title = ?, ");
      params.add(title);
    }
    if (description != null) {
      query.append("description = ?, ");
      params.add(description);
    }
    if (portions != null) {
      query.append("portions = ?, ");
      params.add(portions);
    }
    if (imageUrl != null) {
      query.append("image_url = ?, ");
      params.add(imageUrl);
    }

    // Entferne das letzte Komma und füge die WHERE-Klausel hinzu
    query.setLength(query.length() - 2);
    query.append(" WHERE id = ?");
    params.add(recipeId);

    dbClient.updateWithParams(query.toString(), params, res -> {
      if (res.succeeded()) {
        // Rezept erfolgreich aktualisiert
        context.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("message", "✅ Rezept teilweise aktualisiert!").encode());
      } else {
        context.response().setStatusCode(500).end("❌ Fehler beim Aktualisieren des Rezepts: " + res.cause().getMessage());
      }
    });
  }


}
