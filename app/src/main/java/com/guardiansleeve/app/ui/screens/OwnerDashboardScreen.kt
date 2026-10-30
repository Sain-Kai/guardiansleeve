package com.guardiansleeve.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardiansleeve.app.model.AlertEscalationState
import com.guardiansleeve.app.model.EmergencyAlert
import com.guardiansleeve.app.model.VitalsData
import com.guardiansleeve.app.ui.theme.*

@Composable
fun OwnerDashboardScreen(
    vitals: VitalsData,
    alertState: EmergencyAlert,
    onRemoteDisarm: () -> Unit,
    onForceRemoteSOS: () -> Unit,
    onSilenceAlarm: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentLat = alertState.latitude ?: 28.6139
    val currentLng = alertState.longitude ?: 77.2090

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSoft)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            // Owner Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Owner Remote Portal",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Box(
                    modifier = Modifier
                        .background(PurpleLight, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Remote Guardian Mode",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PurpleHero
                    )
                }
            }
        }

        // ACCIDENT / HARD BANG CONTINUOUS BEEP ALARM BANNER
        if (alertState.isOwnerAlarmBeeping) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertRed),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CRITICAL ACCIDENT ALARM",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Hard Bang / Severe Fall Detected for Sohan!\nSafety check was UNANSWERED. Alarm will beep continuously until manually stopped.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.95f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onSilenceAlarm,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Icon(Icons.Default.VolumeOff, contentDescription = null, tint = AlertRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MANUALLY SILENCE BEEP",
                                color = AlertRed,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Target Carrier Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (alertState.escalationState == AlertEscalationState.STAGE_2_ESCALATED) AlertRed else SurfaceCard
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Carrier: Sohan",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alertState.escalationState == AlertEscalationState.STAGE_2_ESCALATED) Color.White else TextPrimary
                            )
                            Text(
                                text = if (alertState.escalationState == AlertEscalationState.STAGE_2_ESCALATED)
                                    "🚨 EMERGENCY TRIGGER ACTIVE"
                                else "All Systems Normal & Safe",
                                fontSize = 13.sp,
                                color = if (alertState.escalationState == AlertEscalationState.STAGE_2_ESCALATED)
                                    Color.White.copy(alpha = 0.9f)
                                else MintGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (alertState.escalationState == AlertEscalationState.STAGE_2_ESCALATED) Color.White else PurpleLight,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (alertState.escalationState == AlertEscalationState.STAGE_2_ESCALATED)
                                    Icons.Default.Warning
                                else Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (alertState.escalationState == AlertEscalationState.STAGE_2_ESCALATED) AlertRed else PurpleHero
                            )
                        }
                    }
                }
            }
        }

        // Live Geolocation Card
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
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live GPS Coordinates",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = AlertRed)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Latitude: $currentLat\nLongitude: $currentLng",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val mapUri = Uri.parse("https://www.google.com/maps?q=$currentLat,$currentLng")
                            val intent = Intent(Intent.ACTION_VIEW, mapUri)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleHero)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Open in Google Maps", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Real-Time Telemetry
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Heart Rate
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Remote Pulse", fontSize = 13.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${vitals.heartRate} BPM",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (vitals.heartRate >= 130) AlertRed else CoralRed
                        )
                    }
                }

                // Battery
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Carrier Batt", fontSize = 13.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${vitals.batteryPercent}%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmOrange
                        )
                    }
                }
            }
        }

        // Owner Remote Controls
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
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Owner Remote Override Controls",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Remote Disarm Button
                    Button(
                        onClick = onRemoteDisarm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "REMOTE DISARM & RESTORE SCREEN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Remote SOS Trigger Button
                    OutlinedButton(
                        onClick = onForceRemoteSOS,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AlertRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FORCE REMOTE EMERGENCY SOS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AlertRed
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
