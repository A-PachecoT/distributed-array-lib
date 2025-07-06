#!/bin/bash

echo "Starting TypeScript distributed array client..."

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "Node.js is not installed. Please install Node.js 16 or higher."
    exit 1
fi

cd ../typescript

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi

# Check if TypeScript is built
if [ ! -d "dist" ]; then
    echo "Building TypeScript code..."
    npm run build
fi

# Default connection parameters
HOST=${1:-localhost}
PORT=${2:-5000}

echo "Connecting to master at $HOST:$PORT"
echo ""

# Run the client
npm start -- $HOST $PORT