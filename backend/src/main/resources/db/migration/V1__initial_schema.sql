CREATE TABLE upscale_batch (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    preset VARCHAR(32) NOT NULL DEFAULT 'ADOBE_STOCK',
    scale INT NOT NULL DEFAULT 4,
    model VARCHAR(64) NOT NULL DEFAULT 'general',
    output_format VARCHAR(16) NOT NULL DEFAULT 'JPEG',
    quality INT NOT NULL DEFAULT 95,
    total_images INT NOT NULL DEFAULT 0,
    completed_images INT NOT NULL DEFAULT 0,
    failed_images INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error TEXT
);

CREATE INDEX idx_upscale_batch_status ON upscale_batch(status);
CREATE INDEX idx_upscale_batch_created_at ON upscale_batch(created_at DESC);

CREATE TABLE upscale_image (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES upscale_batch(id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    input_path VARCHAR(512) NOT NULL,
    output_path VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    input_width INT,
    input_height INT,
    input_megapixels NUMERIC(6, 2),
    input_size BIGINT,
    output_width INT,
    output_height INT,
    output_megapixels NUMERIC(6, 2),
    output_size BIGINT,
    output_format VARCHAR(16),
    color_profile VARCHAR(32),
    processing_time_ms BIGINT,
    stock_ready BOOLEAN NOT NULL DEFAULT FALSE,
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_upscale_image_batch_id ON upscale_image(batch_id);
CREATE INDEX idx_upscale_image_status ON upscale_image(status);
CREATE INDEX idx_upscale_image_created_at ON upscale_image(created_at ASC);
