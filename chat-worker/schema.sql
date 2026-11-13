CREATE TABLE IF NOT EXISTS comments (
  id         serial PRIMARY KEY,
  nick       text NOT NULL,
  tag        text NOT NULL,
  body       text NOT NULL,
  is_dev     boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);
