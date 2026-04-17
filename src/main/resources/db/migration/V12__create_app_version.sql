CREATE TABLE app_version (
    id                    BIGSERIAL    PRIMARY KEY,
    platform              VARCHAR(10)  NOT NULL,
    latest_version        VARCHAR(20)  NOT NULL,
    min_supported_version VARCHAR(20)  NOT NULL,
    store_url             VARCHAR(500) NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uq_app_version_platform UNIQUE (platform),
    CONSTRAINT ck_app_version_platform CHECK (platform IN ('ios', 'android'))
);

INSERT INTO app_version (platform, latest_version, min_supported_version, store_url)
VALUES
  ('ios',     '1.1.0', '1.0.0', 'https://apps.apple.com/app/id<APP_ID>'),
  ('android', '1.1.0', '1.0.0', 'https://play.google.com/store/apps/details?id=<PKG>');
