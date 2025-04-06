CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(150) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE steps (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       recipe_id INT NOT NULL,
                       step_number INT NOT NULL,
                       instruction TEXT NOT NULL,
                       FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
);
DROP TABLE users CASCADE ;

DROP TABLE steps;
DROP TABLE recipes;
DROP TABLE users;

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(150) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE steps (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       recipe_id INT NOT NULL,
                       step_number INT NOT NULL,
                       instruction TEXT NOT NULL,
                       FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
);

CREATE TABLE ingredients (
                             id INT AUTO_INCREMENT PRIMARY KEY,
                             name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE recipe_ingredients (
                                    id INT AUTO_INCREMENT PRIMARY KEY,
                                    recipe_id INT NOT NULL,
                                    ingredient_id INT NOT NULL,
                                    amount DECIMAL(5,2) NOT NULL,
                                    unit VARCHAR(50) NOT NULL, -- z.B. "g", "ml", "TL"
                                    FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                                    FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE CASCADE
);

CREATE TABLE shopping_lists (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                user_id INT NOT NULL,
                                recipe_id INT UNIQUE NOT NULL,  -- Jedes Rezept kann nur eine Einkaufsliste haben
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
);




DROP TABLE recipes;

SHOW CREATE TABLE recipes;

DROP TABLE steps;
DROP TABLE recipes;
DROP TABLE users CASCADE ;
DROP TABLE ingredients;
DROP TABLE categories;

DELETE FROM recipes WHERE user_id IS NOT NULL;


SET FOREIGN_KEY_CHECKS = 0;  -- Deaktiviert Fremdschlüsselüberprüfungen, um Fehler zu vermeiden

-- Lösche alle Tabellen in der aktuellen Datenbank
-- SET @tables = (SELECT GROUP_CONCAT(table_name) FROM information_schema.tables WHERE table_schema = DATABASE());

-- Erzeuge und führe den DROP TABLE-Befehl aus
SET @query = CONCAT('DROP TABLE IF EXISTS ', @tables);
PREPARE stmt FROM @query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;  -- Reaktiviert die Fremdschlüsselüberprüfung


CREATE TABLE comments (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        recipe_id INT NOT NULL,
                        content TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
);

CREATE TABLE recipes (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       user_id INT NOT NULL,
                       title VARCHAR(255) NOT NULL,
                       description TEXT,
                       portions INT DEFAULT 1,
                       image_url VARCHAR(255),
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE favorites (
                         user_id INT NOT NULL,
                         recipe_id INT NOT NULL,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         PRIMARY KEY (user_id, recipe_id),
                         FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                         FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
);

CREATE TABLE recipe_categories (
                                 recipe_id INT NOT NULL,
                                 category_id INT NOT NULL,
                                 PRIMARY KEY (recipe_id, category_id),
                                 FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                                 FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

CREATE TABLE categories (
                          id INT AUTO_INCREMENT PRIMARY KEY,
                          name VARCHAR(100) UNIQUE NOT NULL
);


INSERT INTO users (name, email, password_hash)
VALUES ('Testnutzer', 'test@example.com', 'dummyhash123');

CREATE INDEX idx_recipes_user_id ON recipes(user_id);


INSERT INTO users (id, name, email) VALUES (1, 'Testnutzer', 'test@example.com');


SELECT * FROM ingredients;
SELECT * FROM recipe_ingredients;

