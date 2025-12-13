# Node.js Dockerfile for ContentSync Server

FROM node:18-alpine

# Set working directory
WORKDIR /app

# Install production dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy application code
COPY src ./src

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD node -e "require('http').get('http://localhost:3000/health', (r) => {process.exit(r.statusCode === 200 ? 0 : 1)})"

# Set environment
ENV NODE_ENV=production

# Run as non-root user
USER node

# Start server
CMD ["node", "src/index.js"]
