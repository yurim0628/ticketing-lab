-- Initial schema for time-slot capacity experiments.
-- Add subsequent schema changes as new migrations; do not rewrite an applied migration.
CREATE TABLE bk_ticket_session (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    starts_at   DATETIME(3)  NOT NULL,
    capacity    INT          NOT NULL,
    remaining   INT          NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT ck_bk_ticket_session_capacity CHECK (capacity >= 0),
    -- Persisted inventory stays within capacity, independently of application-side validation.
    CONSTRAINT ck_bk_ticket_session_remaining CHECK (remaining >= 0),
    CONSTRAINT ck_bk_ticket_session_remaining_le_capacity CHECK (remaining <= capacity)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
