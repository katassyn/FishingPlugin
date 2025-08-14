CREATE TABLE IF NOT EXISTS quests_chain_progress (
    player_uuid VARCHAR(36) PRIMARY KEY,
    stage INT NOT NULL,
    count INT NOT NULL
);
