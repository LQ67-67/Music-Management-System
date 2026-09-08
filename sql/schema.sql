-- Music Library Management System - MySQL Schema

-- CREATE DATABASE music_library CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS tracks;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS users;

-- user table with regular users and administrators
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- customer table (can be associated with users or used separately; user_id NULL/0 = unlinked)
CREATE TABLE customers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(50),
    city VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- music track master data
CREATE TABLE tracks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    artist VARCHAR(200) NOT NULL,
    album VARCHAR(200),
    genre VARCHAR(100),
    price DECIMAL(10,2) NOT NULL DEFAULT 0,
    stock_qty INT NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
);

-- order master table
CREATE TABLE orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    user_id INT NOT NULL,
    order_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status ENUM('PENDING', 'CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    shipping_city VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- When an order is deleted, the customer and user are not deleted, and the customer and user are updated in a cascade when the order is updated
    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    -- When an order is deleted, the customer and user are not deleted, and the customer and user are updated in a cascade when the order is updated
    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- change the status column to a string with a maximum length of 20
ALTER TABLE orders MODIFY status VARCHAR(20) DEFAULT 'PENDING';

-- order schedule
CREATE TABLE order_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    track_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_items_track
        FOREIGN KEY (track_id) REFERENCES tracks(id)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

SET FOREIGN_KEY_CHECKS = 1;

-- basic test data
INSERT INTO users (username, password_hash, role) VALUES
('admin', 'admin', 'ADMIN'),
('user1', 'user1', 'USER');

-- Skim is linked to the user1 login account (id 2)
INSERT INTO customers (user_id, name, email, phone, city) VALUES
(2, 'Skim', 'skim@gmail.com', '012-3456789', 'Kuala Lumpur'),
(NULL, 'Lando Norris', 'norries@soton.ac.uk', '013-9876543', 'Penang');

INSERT INTO tracks (title, artist, album, genre, price, stock_qty) VALUES
('1. Until Tomorrow''s Twilight', '北川勝利', 'ARIA The OVA ~ARIETTA~', 'Anime',  3.50, 100),
('2. Nagisa (なぎさ)', 'Key Sound Team', 'CLANNAD ORIGINAL SOUNDTRACK', 'Anime', 4.20, 80),
('3. The Day I Waited for the Wind', 'Key Sounds Label', 'Kanon', 'Anime', 5.00, 50),
('4. Bad Apple', 'Touhou Project game', 'Touhou', 'Remix', 6.00, 67),
('5. Moon Light', 'Fred Capozio', 'Moon Light', 'Piano', 7.00, 40),
('6. The Promise of the Dandelion', 'Jay Chou', 'I am busy', 'Piano', 15.00, 100),
('7. Schubert''s Serenade (Ständchen)', 'Franz Schubert', 'Schwanengesang', 'Classics', 10.00, 70),
('8. Affections Touching Across Time', 'Kaoru Wada', 'Inuyasha', 'Piano', 15.00, 100),
('9. Ievan Polkka', 'Hatsune Miku', 'Ievan Polkka', 'Electronic music', 8.00, 40),
('10. Ariga Thesis','MUYKKE','Ariga Thesis','Remix', 5.20, 100);