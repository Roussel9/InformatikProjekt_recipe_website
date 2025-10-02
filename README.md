# Recipe Website

## Projektbeschreibung
Die **Recipe Website** ist eine Webanwendung, die es Nutzern ermöglicht, Rezepte zu durchsuchen, eigene Rezepte zu erstellen und Kommentare zu hinterlassen.  
Das Projekt kombiniert **Backend-Logik**, **Datenbankverwaltung** und **Frontend-Design**, um eine vollständige, interaktive Plattform für Koch- und Rezeptideen bereitzustellen.

**Use Case:**  
- Nutzer möchte hinzufugen , verwalten oder löschen
- Entwickler möchte die Interaktion zwischen Frontend, Backend und Datenbank demonstrieren  
- Lern- und Praxisprojekt für moderne Webentwicklung mit Java

---

## Technologien & Tools

**Backend:**  
- **Vert.x** – High-Performance Web-Framework für Java  
- **MariaDB** – SQL-Datenbank für Rezepte, Nutzer und Kommentare  
- **apiDoc** – Dokumentation des Backends  

**Frontend:**  
- **HTML5 & CSS3** – Struktur und Design der Webanwendung  
- **Bootstrap 5** – Schnelle, responsive Styles  

**DevOps & Versionierung:**  
- **GitLab** – Repository für Zusammenarbeit und CI/CD  

---

## Architektur & Struktur
Die Anwendung ist modular aufgebaut, um Frontend, Backend und Datenbank sauber zu trennen:


**Datenfluss & Komponenten:**  
- **Frontend:** Darstellung der Rezepte, Formulareingaben, Benutzerinteraktion  
- **Backend (Vert.x):** Routing, API-Endpunkte, Authentifizierung  
- **MariaDB:** Speicherung von Rezepten, Nutzerdaten,favorite und Kommentaren  
- **apiDoc:** Generierung von API-Dokumentation aus dem Code

---

## Funktionen & Features
- Rezeptanzeige nach Kategorien  
- Rezepte erstellen, bearbeiten und löschen  
- Nutzerregistrierung und Login  
- Kommentare zu Rezepten hinterlassen  
- Responsive Design für Desktop und Mobile  
- API-Dokumentation für Backend-Endpunkte

---
Installation & Nutzung

Repository klonen:

git clone


## Backend starten:

Stelle sicher, dass Java und Maven installiert sind

Navigiere in den Backend-Ordner:

cd backend
mvn clean install
mvn exec:java


**Datenbank einrichten**:

MariaDB starten

database/schema.sql ausführen, um Tabellen und Testdaten anzulegen

Frontend öffnen:

Öffne frontend/index.html in einem Browser oder über einen lokalen Webserver . Unter sind zum Beispiel Starseite und Profilseite


### Startseite 

![Homepage Screenshot](frontend/assets/homepage.png)

### Profilseite 

![profilpage Screenshot](frontend/assets/profilpage.png)

## Zukunft & Ideen

Erweiterung der Rezeptfunktionen (Favoriten, Bewertungssystem)

Such- und Filteroptionen für Rezepte

Nutzerprofile mit eigenen Rezeptlisten

Mobile App-Anbindung über REST-API

CI/CD-Pipeline automatisieren für Deployment

## Kontakt & Lizenz

**Autor: Roussel9**

**Lizenz: MIT License (frei zur Nutzung & Modifikation)**

