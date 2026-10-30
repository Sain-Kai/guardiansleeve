# Telegram Bot API Setup Guide for Guardian Sleeve

Guardian Sleeve uses the official, free, high-speed **Telegram Bot API** to transmit real-time emergency SOS alerts with GPS coordinates, live heart rate, and battery levels to 5 designated contacts.

---

## Step 1: Create Your Telegram Bot (Free)

1. Open Telegram and search for **`@BotFather`**.
2. Send the command:
   ```text
   /newbot
   ```
3. Enter a friendly name for your bot (e.g., `Guardian Sleeve SOS Bot`).
4. Enter a username ending in `bot` (e.g., `MyGuardianSleeveBot`).
5. BotFather will provide your **HTTP API Token**, looking like:
   ```text
   7123456789:AAFlkB4_Xz-dF6y5Z...
   ```
6. Copy this token into your `TelegramApiService.kt` or set it in the Guardian Sleeve settings screen.

---

## Step 2: Obtain Chat IDs for Top 5 Contacts

Each contact who should receive alerts must start a chat with your bot:

1. Send your bot link (`t.me/MyGuardianSleeveBot`) to your 5 emergency contacts (or create a dedicated Family Emergency Telegram Group and add the bot to it).
2. Each contact presses **Start** or sends any message (e.g., `Hello`).
3. To retrieve their unique `chat_id`:
   Open your browser and visit:
   ```text
   https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates
   ```
4. Look for the `"chat":{"id":123456789,...}` field in the JSON response.
5. Save the 9-digit `chat_id` into the Guardian Sleeve contact list for each contact.

---

## Step 3: Telegram Alert Payload Format

When an emergency is triggered, Guardian Sleeve dispatches:
```text
🚨 GUARDIAN SLEEVE EMERGENCY SOS 🚨

⚠️ Victim: Sohan
⚠️ Trigger: Physical Panic Button
💓 Heart Rate: 138 BPM
🔋 Battery: 88%

📍 Current Location:
https://www.google.com/maps?q=28.6139,77.2090

Immediate assistance is required! If this is an emergency, contact local authorities immediately.
```
In addition, the app automatically calls the `sendLocation` endpoint to drop a native Telegram GPS map pin directly inside the chat.
