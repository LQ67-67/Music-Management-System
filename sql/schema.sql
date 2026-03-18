-- Music Library Management System - MySQL Schema
-- Coursework: COMP1322 Sem2 2025/2026

-- 注意：请根据你本地 MySQL 设置，先创建数据库：
-- CREATE DATABASE music_library CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 然后在该数据库中执行本脚本。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS tracks;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS users;

-- 用户表：包含普通用户和管理员
CREATE TABLE users (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role         ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 客户表（可以与 users 关联，也可以单独使用）
CREATE TABLE customers (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(100),
    phone      VARCHAR(50),
    city       VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 音乐曲目主数据
CREATE TABLE tracks (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    artist      VARCHAR(200) NOT NULL,
    album       VARCHAR(200),
    genre       VARCHAR(100),
    price       DECIMAL(10,2) NOT NULL DEFAULT 0,
    stock_qty   INT NOT NULL DEFAULT 0,
    is_active   TINYINT(1) NOT NULL DEFAULT 1,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
);

-- 订单主表
CREATE TABLE orders (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    customer_id    INT NOT NULL,
    user_id        INT NOT NULL,
    order_date     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status         ENUM('PENDING', 'CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    total_amount   DECIMAL(10,2) NOT NULL DEFAULT 0,
    shipping_city  VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- 订单明细表
CREATE TABLE order_items (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    order_id    INT NOT NULL,
    track_id    INT NOT NULL,
    quantity    INT NOT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,
    line_total  DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_items_track
        FOREIGN KEY (track_id) REFERENCES tracks(id)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

SET FOREIGN_KEY_CHECKS = 1;

-- 基础测试数据
INSERT INTO users (username, password_hash, role) VALUES
('admin', 'admin', 'ADMIN'),
('user1', 'user1', 'USER');

INSERT INTO customers (name, email, phone, city) VALUES
('Alice', 'alice@example.com', '012-3456789', 'Kuala Lumpur'),
('Bob',   'bob@example.com',   '013-9876543', 'Penang');

INSERT INTO tracks (title, artist, album, genre, price, stock_qty) VALUES
('Song A', 'Artist A', 'Album A', 'Pop',  3.50, 100),
('Song B', 'Artist B', 'Album B', 'Rock', 4.20, 80),
('Song C', 'Artist C', 'Album C', 'Jazz', 5.00, 50);

