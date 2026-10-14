ALTER TABLE posts ADD COLUMN item_type VARCHAR(10) NULL;
ALTER TABLE posts ADD COLUMN location VARCHAR(255) NULL;
ALTER TABLE posts ADD COLUMN contact_info VARCHAR(255) NULL;
ALTER TABLE posts ADD COLUMN occurred_at DATETIME(6) NULL;

ALTER TABLE posts ADD CONSTRAINT chk_posts_item_type CHECK (item_type IS NULL OR item_type IN ('LOST', 'FOUND'));

CREATE INDEX idx_posts_item_type ON posts(item_type);
