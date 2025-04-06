package com.example.starter;

import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.core.Vertx;

public class DatabaseQueryExecutor {


  public static void queryUsers(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM users");
  }

 public static void queryRecipes(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM recipes");
  }

 public static void querySteps(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM steps");
  }

   public static void queryIngredients(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM ingredients");
  }

   public static void queryRecipeIngredients(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM recipe_ingredients");
  }

   public static void queryShoppingLists(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM shopping_lists");
  }

   public static void queryCategories(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM categories");
  }

   public static void queryRecipeCategories(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM recipe_categories");
  }

   public static void queryFavorites(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM favorites");
  }

   public static void queryComments(Vertx vertx) {
    executeQuery(vertx, "SELECT * FROM comments");
  }

   private static void executeQuery(Vertx vertx, String query) {
    JDBCClient dbClient = DatabaseConfig.connect(vertx);

    dbClient.getConnection(res -> {
      if (res.succeeded()) {
        res.result().query(query, queryResult -> {
          if (queryResult.succeeded()) {
            System.out.println(queryResult.result().getRows());
          } else {
            System.out.println("Fehler bei der Abfrage: " + queryResult.cause());
          }
        });
      } else {
        System.out.println("Verbindungsfehler: " + res.cause());
      }
    });
  }
}
