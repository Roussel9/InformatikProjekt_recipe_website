package com.example.starter;

import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.core.json.JsonObject;

public class DatabaseConfig {
  public static JDBCClient connect(Vertx vertx) {
    JsonObject config = new JsonObject()
      .put("url", "jdbc:mariadb://ip1-dbs.mni.thm.de:3306/InfP-WS2425-37")
      .put("driver_class", "org.mariadb.jdbc.Driver")
      .put("user", "InfP-WS2425-37")
      .put("password", "zY7EcCPw*wWvUSLo");

    System.out.println("🔗 Versuche, eine Verbindung zur Datenbank herzustellen...");
    JDBCClient client = JDBCClient.createShared(vertx, config);

    client.getConnection(res -> {
      if (res.succeeded()) {
        System.out.println("✅ Erfolgreich mit der Datenbank verbunden!");
      } else {
        System.out.println("❌ Fehler bei der Verbindung: " + res.cause().getMessage());
      }
    });

    return client;
  }
}
