-- Row Level Security Policies for Supabase
-- These policies ensure users can only access their own data

-- Enable RLS on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE room_events ENABLE ROW LEVEL SECURITY;

-- Users policies
-- Users can read their own profile
CREATE POLICY "Users can view own profile"
  ON users FOR SELECT
  USING (auth.uid()::uuid = id);

-- Users can update their own profile
CREATE POLICY "Users can update own profile"
  ON users FOR UPDATE
  USING (auth.uid()::uuid = id);

-- Rooms policies
-- Anyone authenticated can create a room
CREATE POLICY "Authenticated users can create rooms"
  ON rooms FOR INSERT
  WITH CHECK (auth.uid()::uuid = host_user_id);

-- Users can view rooms they created
CREATE POLICY "Users can view own rooms"
  ON rooms FOR SELECT
  USING (auth.uid()::uuid = host_user_id);

-- Users can view rooms they are participants in
CREATE POLICY "Users can view rooms they joined"
  ON rooms FOR SELECT
  USING (
    id IN (
      SELECT room_id FROM participants WHERE user_id = auth.uid()::uuid
    )
  );

-- Room hosts can update/delete their rooms
CREATE POLICY "Hosts can update own rooms"
  ON rooms FOR UPDATE
  USING (auth.uid()::uuid = host_user_id);

CREATE POLICY "Hosts can delete own rooms"
  ON rooms FOR DELETE
  USING (auth.uid()::uuid = host_user_id);

-- Participants policies
-- Users can join rooms (insert participant record)
CREATE POLICY "Users can join rooms"
  ON participants FOR INSERT
  WITH CHECK (auth.uid()::uuid = user_id);

-- Users can view participants in rooms they're in
CREATE POLICY "Users can view participants in their rooms"
  ON participants FOR SELECT
  USING (
    room_id IN (
      SELECT room_id FROM participants WHERE user_id = auth.uid()::uuid
    )
  );

-- Users can update their own participant record
CREATE POLICY "Users can update own participation"
  ON participants FOR UPDATE
  USING (auth.uid()::uuid = user_id);

-- Room events policies
-- Users can insert events for rooms they're in
CREATE POLICY "Users can log events in their rooms"
  ON room_events FOR INSERT
  WITH CHECK (
    room_id IN (
      SELECT room_id FROM participants WHERE user_id = auth.uid()::uuid
    )
  );

-- Users can view events from rooms they're in
CREATE POLICY "Users can view events from their rooms"
  ON room_events FOR SELECT
  USING (
    room_id IN (
      SELECT room_id FROM participants WHERE user_id = auth.uid()::uuid
    )
  );

-- Service role bypass (for server-side operations)
-- The server using service role key can bypass all RLS policies

