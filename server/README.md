# ContentSync Signaling Server

WebSocket-based signaling server for ContentSync video synchronization.

## Features

- WebSocket communication via Socket.IO
- Firebase authentication
- PostgreSQL database for room metadata
- Rate limiting and security
- Event logging and monitoring

## Setup

### Prerequisites

- Node.js 18+
- PostgreSQL (or Supabase account)
- Firebase project with Admin SDK

### Installation

```bash
npm install
```

### Configuration

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Required environment variables:
- `DATABASE_URL` - PostgreSQL connection string
- `FIREBASE_PROJECT_ID` - Firebase project ID
- `FIREBASE_CLIENT_EMAIL` - Firebase service account email
- `FIREBASE_PRIVATE_KEY` - Firebase private key

### Database Setup

Run migrations:

```bash
npm run migrate
```

Or manually run `migrations/001_initial_schema.sql` in your PostgreSQL database.

### Development

```bash
npm run dev
```

### Production

```bash
npm start
```

## API Endpoints

### REST API

#### POST /api/rooms/create
Create a new room.

**Headers:**
```
Authorization: Bearer <firebase-id-token>
```

**Body:**
```json
{
  "file_hash": "sha256-hex-string",
  "duration_ms": 3600000,
  "file_size": 1234567890,
  "codec": {
    "video": "h264",
    "audio": "aac",
    "resolution": "1920x1080"
  },
  "expires_in_days": 7,
  "passcode": "optional"
}
```

**Response:**
```json
{
  "roomId": "AB12CD",
  "shareUrl": "https://app.contentsync.com/room/AB12CD",
  "expiresAt": "2025-01-01T00:00:00.000Z"
}
```

#### POST /api/rooms/:id/validate
Validate file before joining room.

#### POST /api/rooms/:id/close
Close a room (host only).

#### GET /api/rooms/:id
Get room details and participants.

### Socket.IO Events

#### Client → Server

- `authenticate` - Initial authentication with Firebase token
- `joinRoom` - Join a room as host or follower
- `hostPlay` - Host sends play command
- `hostPause` - Host sends pause command
- `hostSeek` - Host sends seek command
- `ping` - RTT measurement
- `reaction` - Send reaction
- `chatMessage` - Send chat message
- `leaveRoom` - Leave room

#### Server → Client

- `joined` - Room join confirmation
- `hostPlay` - Relayed play command
- `hostPause` - Relayed pause command
- `hostSeek` - Relayed seek command
- `pong` - RTT response
- `reaction` - Relayed reaction
- `chatMessage` - Relayed chat message
- `participantLeft` - User left room
- `hostTransferred` - Host transferred to another user
- `error` - Error notification

## Deployment

### Docker

```bash
docker build -t contentsync-server .
docker run -p 3000:3000 --env-file .env contentsync-server
```

### Railway

1. Connect GitHub repository
2. Add environment variables in Railway dashboard
3. Deploy automatically on push

### Render

1. Create new Web Service
2. Connect GitHub repository
3. Set build command: `npm install`
4. Set start command: `npm start`
5. Add environment variables

## Security

- All endpoints require Firebase authentication
- Rate limiting on API endpoints
- Input validation with Joi
- SQL injection prevention with parameterized queries
- WSS/HTTPS in production

## Monitoring

Logs are output to console and optionally to file (set `LOG_FILE` in .env).

Log levels: error, warn, info, debug

## Testing

```bash
npm test
```

## License

MIT

