#!/bin/bash

echo "Compiling Java distributed array library..."

# Create output directory
mkdir -p out

# Compile all Java files
javac -cp "lib/gson-2.10.1.jar" -d out src/common/*.java src/master/*.java src/worker/*.java src/client/*.java

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
else
    echo "Compilation failed!"
    exit 1
fi