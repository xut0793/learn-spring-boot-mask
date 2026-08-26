DROP TABLE IF EXISTS contacts;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    id_card VARCHAR(32) NOT NULL,
    email VARCHAR(128) NOT NULL,
    bank_card VARCHAR(32) NOT NULL,
    city VARCHAR(64),
    address_detail VARCHAR(128),
    express_no VARCHAR(32)
);

CREATE TABLE contacts (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    contact_value VARCHAR(64) NOT NULL
);
