#!/bin/bash

# Selah Bot Installation Script for Debian 13
# This script installs all required dependencies and builds the bot

set -e  # Exit on any error

echo "================================"
echo "  Selah Bot Installation Script"
echo "  Debian 13 Setup"
echo "================================"
echo ""

# Check if running as root or with sudo
if [[ $EUID -ne 0 ]]; then
   echo "This script must be run with sudo"
   exit 1
fi

echo "[1/5] Updating package manager..."
apt-get update
apt-get upgrade -y

echo "[2/5] Installing Tesseract OCR and dependencies..."
apt-get install -y tesseract-ocr
apt-get install -y libtesseract-dev

echo "[3/5] Installing Tesseract language data (English)..."
apt-get install -y tesseract-ocr-eng

echo "[4/5] Verifying Tesseract installation..."
which tesseract
tesseract --version

echo "[5/5] Installing Java Development Kit (if not present)..."
apt-get install -y default-jdk
java -version
javac -version

echo ""
echo "================================"
echo "  Installation Complete!"
echo "================================"
echo ""
echo "Next steps:"
echo "1. Set the SELAH_DISCORD_TOKEN environment variable with your Discord bot token"
echo "2. Create/populate the server_configs/ folder with server configuration files"
echo "3. Build the bot with: mvn clean package"
echo "4. Run the bot with: java -jar target/selah-bot-1.0.jar"
echo ""
echo "For more information, check the README.md"