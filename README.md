# Music Library Management System

A desktop music library management system course project based on JavaFX + Maven + MySQL, which supports role login ('USER' / 'ADMIN'), music product management, shopping cart and order process, payment simulation, report statistics, music player, and text export. 

## Project Highlights

- **Dual Role Permissions**: After logging in, enter different main interfaces according to your role. 
- **Complete process on the user side**: Search tracks, add to cart, place orders, manage orders, pay and view invoices. 
- **Admin Management**: Track/Customer/Order CRUD, Inventory Maintenance, Sales Report & Export. 
- **Multi-dimensional sales report**: Statistics by type (Genre), city (City), and date (Date), including average, sum, maximum value, and minimum value. 
- **Built-in Music Player**: Supports playlist, progress, volume, up and down song switching, and cover display. 
- **One-click export TXT**: Supports exporting Tracks / Customers / Orders / Users / Chart Data. 

## Technology Stack

- **Language**: Java 25
- **UI**：JavaFX (`controls`, `fxml`, `media`)
- **Build Tools**: Maven
- **Database**: MySQL 8.x (JDBC: 'mysql-connector-j')
- **Test**: JUnit 5
- **Modular**: JPMS ('module-info.java')

## System Architecture

The project adopts a hierarchical structure: 

- **UI / Controller layer**: Handles JavaFX page events and interaction logic
  - `LoginController`
  - `UserMainController`
  - `OrderManagementController`
  - `AdminMainController`
  - `MusicPlayerController`
- **DAO Layer**: Encapsulates database CRUD
  - `UserDao`, `TrackDao`, `CustomerDao`, `OrderDao`, `OrderItemDao`
- Service tier: Aggregate business transactions
  - 'OrderService' (order placement, inventory deduction, line item write)
- Model Layer: Entity object
  - `User`, `Track`, `Customer`, `Order`, `OrderItem`
- **Session and Tool Layer**: 
  - 'SessionManager' (login state)
  - 'TrackMediaResolver' (Audio/Cover Asset Analysis)
  - 'DBConnectionManager' (database connection)

## Table of Contents Description

```text
test1/ 
├── sql/schema.sql # Database table creation and initial data
├── src/main/java/com/example/musiclibrary
│ ├── controller/ # controller
│ ├── dao/ # Data access
│ ├── service/ # Business service
│ ├── model/ # Entity model
│ ├── db/DBConnectionManager.java # DB connection configuration
│ └── MusicLibraryApp.java # JavaFX Application Portal
├── src/main/resources
│ ├── fxml/ # page layout
│ ├── css/app.css # Global style
│ ├── images/ # icon and track cover
│ └── musics/ # Music files
├── src/test/java/com/example/musiclibrary
│   ├── UserDaoTest.java
│   ├── TrackDaoTest.java
│   ├── CustomerDaoTest.java
│   └── OrderServiceTest.java
└── pom.xml
```

## Feature List

### 1) Login and Registration

- Login verifies username and password, and error message is prompted when failed. 
- New users can be registered (default role 'USER'). 
- After successful login, enter different homepages according to your role. 
- Current account information is stored in the session and can be accessed globally.
---
#### Admin:
- account: 'admin'
- password: 'admin'
#### User:
- account: 'user1'
- password: 'user1'
---
### 2) User

- Browse and search for tracks (titles/artists) on sale. 
- Select the quantity to add to the cart (including inventory check). 
- Support deleting, emptying, and settling in the shopping cart. 
- View My Orders and Order Details. 
- Order payment (card number, cardholder, expiration date, CVV input check, including simulated payment result). 
- View the order invoice. 
- Edit your profile (email/phone format verification). 
- Open the built-in music player to listen to it. 

### 3) Admin

- **Tracks**: Add/edit/soft-delete tracks, support uploading cover and audio assets. 
- **Customers**: Add, delete, modify, and check customer information. 
- Orders: View the full order and edit the status and modify the delivery city. 
- **Reports**： 
  - Summary overview (number of active tracks, number of customers, number of orders, total sales)
  - Sales by Genre / City / Date Statistics
  - Pie chart visualization + metric statistics (avg/sum/max/min)
- Data Export: Supports exporting multiple modules as '.txt'. 

## Get started quickly

### 1. Environmental requirements

- JDK **25** (consistent with 'maven.compiler.release=25' in 'pom.xml')
- Maven 3.9+
- MySQL 8.x

### 2. Initialize the database

Create a database and then import the script: 

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS music_library CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 
mysql -u root -p music_library < sql/schema.sql
```

> Contains the underlying test data (user, customer, track) within the script. 

### 3. Configure the database connection

Edit 'src/main/java/com/example/musiclibrary/db/DBConnectionManager.java': 

- 'URL' default: 'jdbc:mysql://localhost:3306/music_library?serverTimezone=UTC'
- 'USER' default: 'root'
- 'PASSWORD' default: empty string

Please change the account password according to the native MySQL configuration. 

### 4. Start the project

```bash
mvn clean javafx:run
```

It is also possible to run the configuration using the in-repository JetBrains: '.run/Music Library (Maven).run.xml'. 
Or run the main class 'MusicLibraryApp.java' directly.

## Default test account

Initialization data from 'sql/schema.sql': 

- Admin: 'admin' / 'admin'
- Normal user: 'user1' / 'user1'

> The current project is in the form of coursework, and the password is saved in plaintext, which is only used for learning and demonstration scenarios. 

## Testing

The project contains the following JUnit 5 tests: 

- `UserDaoTest`
- `TrackDaoTest`
- `CustomerDaoTest`
- `OrderServiceTest`

Run the command: 

```bash
mvn test
```

> These tests rely on a local MySQL database that is available and initialized with a 'music_library' database. 

## Export and resource file description

- The management side and report pages can export text files (including headers, timestamps, and alignment formats). 
- The track cover and audio upload logic write: 
  - `src/main/resources/images/tracks/`
  - `src/main/resources/musics/`

## Known Limitations (Recommended)

- Plaintext passwords are not recommended in production environments and should be hashed and salted (such as BCrypt). 
- The current DB configuration is written dead in code, so it is recommended to change it to an environment variable or configuration file. 
- Upload resources directly to 'src/main/resources', which is more suitable for development environments. If you need to publish, it is recommended to have a separate external storage directory. 

## License

This project uses the MIT License, see 'LICENSE' for details.