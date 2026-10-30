#!/usr/bin/env python3
"""
Guardian Sleeve - Telegram API Dispatcher & Tester
Sends real-time emergency geo-coordinates and Google Maps links to a user's Telegram.
"""

import sys
import json
import urllib.request

def send_telegram_alert(bot_token, chat_id, latitude=28.6139, longitude=77.2090, victim_name="Sohan"):
    maps_link = f"https://www.google.com/maps?q={latitude},{longitude}"
    
    # 1. Send HTML formatted distress message
    text = f"""🚨 <b>GUARDIAN SLEEVE EMERGENCY SOS</b> 🚨

⚠️ <b>Victim:</b> {victim_name}
⚠️ <b>Trigger:</b> Physical Panic Button / Acoustic Vocal Distress
💓 <b>Heart Rate:</b> 84 BPM
🔋 <b>Battery:</b> 92%

📍 <b>Live Geo-Location:</b>
{maps_link}

<i>Coordinates: {latitude}° N, {longitude}° E</i>
<i>Immediate emergency medical/police response required!</i>
"""
    
    url_msg = f"https://api.telegram.org/bot{bot_token}/sendMessage"
    payload_msg = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": False
    }
    
    try:
        req = urllib.request.Request(
            url_msg,
            data=json.dumps(payload_msg).encode('utf-8'),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            res_data = json.loads(resp.read().decode())
            print(f" Message successfully sent to Telegram (Message ID: {res_data['result']['message_id']})")
    except Exception as e:
        print(f"❌ Error sending Telegram message: {e}")
        return False

    # 2. Send precise interactive map pin (sendLocation)
    url_loc = f"https://api.telegram.org/bot{bot_token}/sendLocation"
    payload_loc = {
        "chat_id": chat_id,
        "latitude": latitude,
        "longitude": longitude
    }
    try:
        req = urllib.request.Request(
            url_loc,
            data=json.dumps(payload_loc).encode('utf-8'),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f" Interactive Map Pin successfully dispatched to Telegram!")
        return True
    except Exception as e:
        print(f"⚠️ Location pin failed: {e}")
        return True

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python3 scripts/test_telegram.py <BOT_TOKEN> <CHAT_ID> [LATITUDE] [LONGITUDE]")
        sys.exit(1)
        
    token = sys.argv[1]
    cid = sys.argv[2]
    lat = float(sys.argv[3]) if len(sys.argv) > 3 else 28.6139
    lng = float(sys.argv[4]) if len(sys.argv) > 4 else 77.2090
    
    print(f"📡 Dispatching Emergency Geo-Location to Telegram Chat ID: {cid}...")
    send_telegram_alert(token, cid, lat, lng)
