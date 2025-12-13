-- ContentSync Database Schema
-- PostgreSQL / Supabase Compatible

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email TEXT UNIQUE,
    phone TEXT UNIQUE,
    display_name TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login TIMESTAMP WITH TIME ZONE,
    CONSTRAINT email_or_phone_required CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);

-- Rooms table
CREATE TABLE rooms (
    id TEXT PRIMARY KEY,  -- short random id (6-8 chars, e.g., AB12CD)
    host_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    host_file_hash TEXT NOT NULL,  -- SHA-256 hex string
    host_file_duration_ms BIGINT NOT NULL,
    host_file_size BIGINT NOT NULL,
    host_file_codec JSONB,  -- {video: "h264", audio: "aac", resolution: "1920x1080"}
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    passcode TEXT,  -- optional room password
    is_active BOOLEAN DEFAULT TRUE,
    max_participants INTEGER DEFAULT 2,
    CONSTRAINT valid_duration CHECK (host_file_duration_ms > 0),
    CONSTRAINT valid_file_size CHECK (host_file_size > 0)
);

CREATE INDEX idx_rooms_host ON rooms(host_user_id);
CREATE INDEX idx_rooms_active ON rooms(is_active, expires_at);
CREATE INDEX idx_rooms_created_at ON rooms(created_at DESC);

-- Participants table
CREATE TABLE participants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    room_id TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    role TEXT NOT NULL CHECK (role IN ('host', 'follower')),
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    left_at TIMESTAMP WITH TIME ZONE,
    socket_id TEXT,  -- current active socket ID
    is_connected BOOLEAN DEFAULT TRUE,
    CONSTRAINT unique_user_room UNIQUE (room_id, user_id)
);

CREATE INDEX idx_participants_room ON participants(room_id);
CREATE INDEX idx_participants_user ON participants(user_id);
CREATE INDEX idx_participants_active ON participants(room_id, is_connected);

-- Room events log (optional, for debugging and analytics)
CREATE TABLE room_events (
    id BIGSERIAL PRIMARY KEY,
    room_id TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL,  -- 'play', 'pause', 'seek', 'join', 'leave', 'reaction', 'chat'
    payload JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_room_events_room ON room_events(room_id, created_at DESC);
CREATE INDEX idx_room_events_type ON room_events(event_type);
CREATE INDEX idx_room_events_created_at ON room_events(created_at DESC);

-- Function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for users table
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function to clean up expired rooms (run periodically via cron or pg_cron)
CREATE OR REPLACE FUNCTION cleanup_expired_rooms()
RETURNS void AS $$
BEGIN
    UPDATE rooms
    SET is_active = FALSE, closed_at = NOW()
    WHERE is_active = TRUE
      AND expires_at < NOW();
END;
$$ LANGUAGE plpgsql;

-- View for active rooms with participant counts
CREATE VIEW active_rooms_view AS
SELECT 
    r.id,
    r.host_user_id,
    r.created_at,
    r.expires_at,
    r.host_file_duration_ms,
    COUNT(p.id) FILTER (WHERE p.is_connected = TRUE) as active_participants,
    MAX(p.joined_at) as last_activity
FROM rooms r
LEFT JOIN participants p ON r.id = p.room_id
WHERE r.is_active = TRUE
  AND r.expires_at > NOW()
GROUP BY r.id;

-- Grant permissions (adjust based on your Supabase setup)
-- For authenticated users
GRANT SELECT, INSERT ON users TO authenticated;
GRANT SELECT, INSERT ON rooms TO authenticated;
GRANT SELECT, INSERT ON participants TO authenticated;
GRANT SELECT, INSERT ON room_events TO authenticated;

-- Comments for documentation
COMMENT ON TABLE users IS 'User accounts with Firebase Auth integration';
COMMENT ON TABLE rooms IS 'Sync rooms containing file metadata (no actual video files)';
COMMENT ON TABLE participants IS 'Room membership tracking';
COMMENT ON TABLE room_events IS 'Event log for debugging and analytics';
COMMENT ON COLUMN rooms.host_file_hash IS 'SHA-256 hash of the video file for verification';
COMMENT ON COLUMN rooms.host_file_codec IS 'JSON object with codec info: {video, audio, resolution}';
COMMENT ON COLUMN rooms.passcode IS 'Optional bcrypt-hashed passcode for room access';
