package com.guardiansleeve.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import com.guardiansleeve.app.data.AppSettings
import com.guardiansleeve.app.model.AlertEscalationState
import com.guardiansleeve.app.model.EmergencyAlert
import com.guardiansleeve.app.model.TriggerSource
import com.guardiansleeve.app.model.VitalsData
import com.guardiansleeve.app.service.GuardianForegroundService
import com.guardiansleeve.app.ui.theme.*

@Composable
fun UserDashboardScreen(
    vitals: VitalsData,
    alertState: EmergencyAlert,
    isMicActive: Boolean,
    onSimulateTrigger: (TriggerSource, String) -> Unit,
    onTestVoiceWake: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToOwner: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        var serverUrlText by remember { mutableStateOf(appSettings.serverUrl) }
        var apiKeyText by remember { mutableStateOf(appSettings.nemotronApiKey) }
        var victimNameText by remember { mutableStateOf(appSettings.victimName) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "⚙️ Guardian Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Mobile Hotspot & Server IP",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PurpleHero
                    )
                    OutlinedTextField(
                        value = serverUrlText,
                        onValueChange = { serverUrlText = it },
                        label = { Text("Server URL", fontSize = 12.sp) },
                        placeholder = { Text("http://192.168.43.1:5000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Quick Presets:",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = { serverUrlText = "http://10.0.2.2:5000" },
                            label = { Text("Emulator (10.0.2.2)", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { serverUrlText = "http://192.168.43.1:5000" },
                            label = { Text("Hotspot (43.1)", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { serverUrlText = "http://localhost:5000" },
                            label = { Text("Localhost", fontSize = 10.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "NVIDIA Nemotron ASR (80ms Latency)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PurpleHero
                    )
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        label = { Text("API Key (nvapi-...) - Optional", fontSize = 12.sp) },
                        placeholder = { Text("nvapi-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "If key is not provided, local on-device SpeechRecognizer is automatically used.",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    OutlinedTextField(
                        value = victimNameText,
                        onValueChange = { victimNameText = it },
                        label = { Text("User Name", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        appSettings.serverUrl = serverUrlText
                        appSettings.nemotronApiKey = apiKeyText
                        appSettings.victimName = victimNameText
                        GuardianForegroundService.updateNetworkAndAsrSettings(
                            serverUrl = appSettings.serverUrl,
                            apiKey = appSettings.nemotronApiKey
                        )
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleHero)
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSoft)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            // Top App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                }
                Text(
                    text = "Guardian Activity",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                IconButton(onClick = onNavigateToContacts) {
                    Icon(Icons.Default.People, contentDescription = "Contacts", tint = TextPrimary)
                }
            }
        }

        // Greeting Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(PurpleHero.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🛡️", fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Good day, ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextPrimary
                        )
                        Text(
                            text = "Sohan",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Guardian Sleeve is monitoring & protecting you",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Top Grid Section: Big Walk Card (Purple) + Sleep/Battery Card & Heart Card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Purple Walk Hero Card (Matches Mockup Left Card)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(200.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PurpleHero),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Walk",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.DirectionsWalk,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Circular Progress Arc for Steps
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(90.dp)) {
                                drawArc(
                                    color = Color.White.copy(alpha = 0.25f),
                                    startAngle = 140f,
                                    sweepAngle = 260f,
                                    useCenter = false,
                                    style = Stroke(width = 16f, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = Color.White,
                                    startAngle = 140f,
                                    sweepAngle = 190f,
                                    useCenter = false,
                                    style = Stroke(width = 16f, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "7,235",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Steps",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Right Column: Battery / Sleep Card + Heart Rate Card
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Battery / Status Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(93.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Sleeve Batt",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = WarmOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${vitals.batteryPercent}%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LiPo",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }

                    // Heart Rate Card (Pulse & Palpitation indicator)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(93.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Heart",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = CoralRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Live ECG Line Canvas
                            Canvas(modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)) {
                                val p = Path().apply {
                                    moveTo(0f, size.height * 0.5f)
                                    lineTo(size.width * 0.2f, size.height * 0.5f)
                                    lineTo(size.width * 0.35f, 0f)
                                    lineTo(size.width * 0.5f, size.height)
                                    lineTo(size.width * 0.65f, size.height * 0.2f)
                                    lineTo(size.width * 0.8f, size.height * 0.5f)
                                    lineTo(size.width, size.height * 0.5f)
                                }
                                drawPath(
                                    path = p,
                                    color = CoralRed,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${vitals.heartRate}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (vitals.heartRate >= 130) AlertRed else CoralRed
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "bpm",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Second Row: Exercise & Audio NLP Shield Card + Calories / Safety Meter
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Audio Keyword Spotter ("help") On-Demand Status Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMicActive) AlertRed.copy(alpha = 0.12f) else SurfaceCard
                    ),
                    border = if (isMicActive) BorderStroke(2.dp, AlertRed) else null,
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isMicActive) "Mic Active" else "Acoustic ASR",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMicActive) AlertRed else TextPrimary
                            )
                            Icon(
                                imageVector = if (isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (isMicActive) AlertRed else SoftCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (isMicActive) "Verifying Voice...\nSay 'HELP' now!" else "Mic OFF (0% Drain)\nWaiting for Shout",
                            fontSize = 12.sp,
                            color = if (isMicActive) AlertRed else TextSecondary,
                            fontWeight = if (isMicActive) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = 16.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isMicActive) AlertRed else MintGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMicActive) "LISTENING (6s)" else "STANDBY (SLEEP)",
                                fontSize = 11.sp,
                                color = if (isMicActive) AlertRed else MintGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Accelerometer & Impact Status Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ADXL345",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = MintGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "G-Force Vector:\n${vitals.accelerationG} G",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MintGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Normal Movement",
                                fontSize = 11.sp,
                                color = MintGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Simulation / Manual Testing Trigger Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Trigger Test & Simulation Center",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onTestVoiceWake,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleHero)
                        ) {
                            Text(text = "Test Wake Mic", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSimulateTrigger(TriggerSource.SLEEVE_BUTTON, "Hardware Sleeve Button") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                        ) {
                            Text(text = "Panic Button", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSimulateTrigger(TriggerSource.PALPITATION_SPIKE, "Palpitation (145 BPM)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarmOrange)
                        ) {
                            Text(text = "Pulse Spike", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onSimulateTrigger(TriggerSource.ACCIDENT_FALL_BANG, "Hard Bang / Accident / Fall from Height") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "SIMULATE ACCIDENT / HARD BANG / FALL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp)) // padding for bottom nav
        }
    }
}
