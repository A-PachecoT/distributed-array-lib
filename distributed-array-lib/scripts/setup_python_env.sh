#!/bin/bash

# Script to set up the Python virtual environment and install dependencies.

echo "=== Setting up Python Environment ==="

# Check if Python 3 is installed
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 is not installed. Please install Python 3.6 or higher."
    exit 1
fi

# Navigate to the python directory
cd ../python || exit

# Create a virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo "Creating virtual environment in 'python/venv'..."
    python3 -m venv venv
else
    echo "Virtual environment 'venv' already exists."
fi

# Activate the virtual environment and install dependencies
echo "Installing dependencies from requirements.txt..."
source venv/bin/activate
pip install -r requirements.txt
deactivate

echo -e "\n✅ Python environment setup is complete."
echo "The scripts will now use this environment automatically."
echo "No need to activate it manually." 