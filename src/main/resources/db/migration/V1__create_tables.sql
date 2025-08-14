CREATE TABLE IF NOT EXISTS profile (
    player_uuid VARCHAR(36) PRIMARY KEY,
    rod_level INT NOT NULL,
    rod_xp BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS loot (
    key VARCHAR(64) PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    base_weight DOUBLE NOT NULL,
    min_rod_level INT NOT NULL,
    broadcast BOOLEAN NOT NULL,
    price_base DOUBLE NOT NULL,
    price_per_kg DOUBLE NOT NULL,
    payout_multiplier DOUBLE NOT NULL,
    min_weight_g DOUBLE NOT NULL,
    max_weight_g DOUBLE NOT NULL,
    item_base64 TEXT
);

CREATE TABLE IF NOT EXISTS quest (
    stage INT PRIMARY KEY,
    goal INT NOT NULL,
    reward DOUBLE NOT NULL
);

CREATE TABLE IF NOT EXISTS param (
    key VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);
