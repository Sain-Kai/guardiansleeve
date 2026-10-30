const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const path = require('path');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

const PORT = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// In-Memory Device State Store
// Key: userId (e.g., 'sohan')
const devices = {
  sohan: {
    userId: 'sohan',
    name: 'Sohan',
    latitude: 28.6139,
    longitude: 77.2090,
    heartRate: 72,
    spO2: 98,
    battery: 92,
    alertState: 'IDLE', // 'IDLE', 'STAGE_1_COUNTDOWN', 'STAGE_2_ESCALATED'
    triggerSource: 'NONE',
    statusMessage: 'Shield Active & Monitoring',
    isAccident: false,
    isOwnerAlarmBeeping: false,
    lastSeen: Date.now(),
    history: []
  }
};

// Queue of pending commands for devices
// Key: userId -> Array of commands [{ id, command, timestamp }]
const pendingCommands = {
  sohan: []
};

// --- WEBSOCKET BROADCAST TO CONNECTED OWNER DASHBOARDS ---
function broadcastToOwners(data) {
  const payload = JSON.stringify(data);
  wss.clients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(payload);
    }
  });
}

wss.on('connection', (ws) => {
  console.log('[WebSocket] Owner dashboard connected.');
  // Send current state of all devices immediately
  ws.send(JSON.stringify({ type: 'INIT_STATE', devices }));

  ws.on('close', () => {
    console.log('[WebSocket] Owner dashboard disconnected.');
  });
});

// --- REST API ENDPOINTS ---

// 1. Mobile Phone Telemetry Ingestion (User app posts every few seconds or on alert)
app.post('/api/telemetry', (req, res) => {
  const {
    userId = 'sohan',
    latitude,
    longitude,
    heartRate = 72,
    spO2 = 98,
    battery = 90,
    alertState = 'IDLE',
    triggerSource = 'NONE',
    statusMessage = '',
    isAccident = false,
    isOwnerAlarmBeeping = false
  } = req.body;

  if (!devices[userId]) {
    devices[userId] = {
      userId,
      name: userId,
      history: []
    };
  }

  const device = devices[userId];
  device.latitude = latitude !== undefined ? latitude : device.latitude;
  device.longitude = longitude !== undefined ? longitude : device.longitude;
  device.heartRate = heartRate;
  device.spO2 = spO2;
  device.battery = battery;
  device.alertState = alertState;
  device.triggerSource = triggerSource;
  device.isAccident = isAccident;
  device.isOwnerAlarmBeeping = isOwnerAlarmBeeping;
  device.statusMessage = statusMessage || (alertState === 'STAGE_2_ESCALATED' ? (isAccident ? '🚨 ACCIDENT / HARD BANG DETECTED!' : 'EMERGENCY ACTIVE') : 'Monitoring Normal');
  device.lastSeen = Date.now();

  // Save to location history breadcrumbs (limit last 100)
  if (latitude && longitude) {
    device.history.push({
      latitude,
      longitude,
      timestamp: Date.now(),
      heartRate
    });
    if (device.history.length > 100) device.history.shift();
  }

  console.log(`[Telemetry] ${userId} | HR: ${heartRate} | Lat: ${latitude}, Lng: ${longitude} | State: ${alertState} | Beep: ${isOwnerAlarmBeeping}`);

  // Broadcast live update to all active Owner viewers
  broadcastToOwners({
    type: 'DEVICE_UPDATE',
    device
  });

  // Check if there are pending commands to return to the mobile app
  const commandsForDevice = pendingCommands[userId] || [];
  const nextCommand = commandsForDevice.shift() || null;

  res.json({
    success: true,
    serverTime: Date.now(),
    pendingCommand: nextCommand
  });
});

// Endpoint for Owner to Manually Silence the Continuous Beep
app.post('/api/device/:userId/silence-beep', (req, res) => {
  const userId = req.params.userId;
  const device = devices[userId];
  if (!device) {
    return res.status(404).json({ error: 'Device not found' });
  }

  const wasBeeping = device.isOwnerAlarmBeeping;
  device.isOwnerAlarmBeeping = false;
  console.log(`[Alarm Silenced] Silence request for ${userId} (wasBeeping: ${wasBeeping})`);

  if (wasBeeping) {
    // Only queue command to phone if it was actually beeping
    pendingCommands[userId] = pendingCommands[userId] || [];
    pendingCommands[userId].push({
      id: `cmd_silence_${Date.now()}`,
      command: 'SILENCE_BEEP',
      timestamp: Date.now()
    });
  }

  broadcastToOwners({
    type: 'DEVICE_UPDATE',
    device
  });

  res.json({ success: true, message: 'Continuous emergency alarm silenced.' });
});

// 2. Fetch Device Status (Owner App / Web Polling)
app.get('/api/device/:userId', (req, res) => {
  const userId = req.params.userId;
  const device = devices[userId];
  if (!device) {
    return res.status(404).json({ error: 'Device not found' });
  }
  res.json(device);
});

// 3. Owner Issues Remote Command (Disarm or Force SOS)
app.post('/api/device/:userId/command', (req, res) => {
  const userId = req.params.userId;
  const { command } = req.body; // 'DISARM' or 'FORCE_SOS'

  if (!['DISARM', 'FORCE_SOS'].includes(command)) {
    return res.status(400).json({ error: 'Invalid command. Must be DISARM or FORCE_SOS' });
  }

  if (!pendingCommands[userId]) {
    pendingCommands[userId] = [];
  }

  const cmdObj = {
    id: `cmd_${Date.now()}`,
    command,
    timestamp: Date.now()
  };

  pendingCommands[userId].push(cmdObj);
  console.log(`[Command Queued] Owner issued ${command} for ${userId}`);

  // If DISARM, update device state locally immediately
  if (command === 'DISARM' && devices[userId]) {
    devices[userId].alertState = 'IDLE';
    devices[userId].statusMessage = 'Disarmed by Owner';
    broadcastToOwners({
      type: 'DEVICE_UPDATE',
      device: devices[userId]
    });
  }

  // Broadcast command to any WebSocket-connected clients (mobile or owner)
  broadcastToOwners({
    type: 'REMOTE_COMMAND',
    userId,
    command: cmdObj
  });

  res.json({
    success: true,
    message: `Command ${command} sent to device ${userId}`,
    command: cmdObj
  });
});

// 4. Mobile Phone Polls for Pending Commands
app.get('/api/device/:userId/pending-command', (req, res) => {
  const userId = req.params.userId;
  const commands = pendingCommands[userId] || [];
  const nextCommand = commands.shift() || null;
  res.json({ command: nextCommand });
});

// 5. Telegram Webhook (Two-Way Remote Control for Owner)
// Allows Owner to reply /disarm directly inside Telegram!
app.post('/api/telegram-webhook', (req, res) => {
  try {
    const message = req.body.message;
    if (message && message.text) {
      const text = message.text.trim().toLowerCase();
      const chatId = message.chat.id;

      console.log(`[Telegram Webhook] Received from ${chatId}: ${text}`);

      if (text === '/disarm') {
        pendingCommands['sohan'] = pendingCommands['sohan'] || [];
        pendingCommands['sohan'].push({
          id: `cmd_tg_${Date.now()}`,
          command: 'DISARM',
          timestamp: Date.now()
        });
        if (devices['sohan']) {
          devices['sohan'].alertState = 'IDLE';
          devices['sohan'].statusMessage = 'Disarmed via Telegram';
          broadcastToOwners({ type: 'DEVICE_UPDATE', device: devices['sohan'] });
        }
      } else if (text === '/sos') {
        pendingCommands['sohan'] = pendingCommands['sohan'] || [];
        pendingCommands['sohan'].push({
          id: `cmd_tg_${Date.now()}`,
          command: 'FORCE_SOS',
          timestamp: Date.now()
        });
      }
    }
  } catch (e) {
    console.error('Webhook error:', e);
  }
  res.sendStatus(200);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`=================================================`);
  console.log(` Guardian Sleeve Cloud Relay Backend Running!`);
  console.log(` URL: http://localhost:${PORT}`);
  console.log(` Owner Web Portal: http://localhost:${PORT}`);
  console.log(` WebSocket Server: ws://localhost:${PORT}`);
  console.log(`=================================================`);
});
