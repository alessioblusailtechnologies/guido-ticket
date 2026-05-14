CREATE TABLE IF NOT EXISTS chat_session (
    id                          VARCHAR(36) NOT NULL PRIMARY KEY,
    title                       VARCHAR(255) NOT NULL,
    model                       VARCHAR(64),
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    total_input_tokens          BIGINT NOT NULL DEFAULT 0,
    total_output_tokens         BIGINT NOT NULL DEFAULT 0,
    total_cache_creation_tokens BIGINT NOT NULL DEFAULT 0,
    total_cache_read_tokens     BIGINT NOT NULL DEFAULT 0,
    total_cost_usd              DECIMAL(12, 6) NOT NULL DEFAULT 0,
    KEY idx_chat_session_updated_at (updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id                  VARCHAR(36) NOT NULL,
    ordinal                     INT NOT NULL,
    role                        VARCHAR(16) NOT NULL,
    content                     MEDIUMTEXT,
    attachments_json            JSON,
    query_results_json          JSON,
    steps_json                  JSON,
    input_tokens                INT,
    output_tokens               INT,
    cache_creation_tokens       INT,
    cache_read_tokens           INT,
    cost_usd                    DECIMAL(12, 6),
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session (id) ON DELETE CASCADE,
    UNIQUE KEY uniq_chat_message_session_ordinal (session_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
