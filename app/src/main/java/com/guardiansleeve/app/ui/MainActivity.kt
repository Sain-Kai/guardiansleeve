package com.guardiansleeve.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guardiansleeve.app.data.ContactsRepository
import com.guardiansleeve.app.model.AlertEscalationState
import com.guardiansleeve.app.model.TriggerSource
import com.guardiansleeve.app.model.UserRole
import com.guardiansleeve.app.service.GuardianForegroundService
import com.guardiansleeve.app.ui.components.EscalationCountdownDialog
import com.guardiansleeve.app.ui.screens.ContactsScreen
import com.guardiansleeve.app.ui.screens.OwnerDashboardScreen
import com.guardiansleeve.app.ui.screens.UserDashboardScreen
import com.guardiansleeve.app.ui.theme.GuardianSleeveTheme
import com.guardiansleeve.app.ui.theme.PurpleHero
import com.guardiansleeve.app.ui.theme.SurfaceCard
import com.guardiansleeve.app.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val contactsRepo = ContactsRepository()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val audio = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (fineLocation && audio) {
            GuardianForegroundService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()
        GuardianForegroundService.start(this)

        setContent {
            GuardianSleeveTheme {
                MainContent()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }

        // Overlay permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Ignore if not supported in test environment
            }
        }
    }

    @Composable
    private fun MainContent() {
        var currentRole by remember { mutableStateOf(UserRole.USER) }
        var currentTab by remember { mutableStateOf(0) } // 0: Home, 1: Contacts, 2: Owner

        val alertState by GuardianForegroundService.alertStateFlow.collectAsState()
        val countdownSeconds by GuardianForegroundService.countdownSecondsFlow.collectAsState()
        val vitals by GuardianForegroundService.currentVitalsFlow.collectAsState()

        val isMicActive by GuardianForegroundService.isMicActiveFlow.collectAsState()
        val contacts = remember { contactsRepo.getAllContacts() }

        Scaffold(
            bottomBar = {
                BottomNavigationBar(
                    selectedTab = currentTab,
                    onSelectTab = { index ->
                        currentTab = index
                        if (index == 2) currentRole = UserRole.OWNER else currentRole = UserRole.USER
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    0 -> UserDashboardScreen(
                        vitals = vitals,
                        alertState = alertState,
                        isMicActive = isMicActive,
                        onSimulateTrigger = { source, desc ->
                            GuardianForegroundService.instance?.handleTrigger(source, desc)
                        },
                        onTestVoiceWake = {
                            GuardianForegroundService.triggerOnDemandVoiceCheck("Dashboard Test Button")
                        },
                        onNavigateToContacts = { currentTab = 1 },
                        onNavigateToOwner = { currentTab = 2; currentRole = UserRole.OWNER }
                    )
                    1 -> ContactsScreen(
                        contacts = contacts,
                        onAddContact = { /* Contact picker / addition */ }
                    )
                    2 -> OwnerDashboardScreen(
                        vitals = vitals,
                        alertState = alertState,
                        onRemoteDisarm = {
                            GuardianForegroundService.instance?.cancelAlert("Owner Remote Disarm")
                        },
                        onForceRemoteSOS = {
                            GuardianForegroundService.instance?.handleTrigger(
                                TriggerSource.REMOTE_OWNER,
                                "Owner Forced Remote SOS"
                            )
                        },
                        onSilenceAlarm = {
                            GuardianForegroundService.instance?.silenceOwnerAlarm("Owner Manually Silenced Beep")
                        }
                    )
                }

                // Stage 1 Escalation Dialog ("Are you okay?" 5s Countdown)
                if (alertState.escalationState == AlertEscalationState.STAGE_1_COUNTDOWN) {
                    EscalationCountdownDialog(
                        countdownSeconds = countdownSeconds,
                        triggerReason = alertState.statusMessage,
                        onSafeDismiss = {
                            GuardianForegroundService.instance?.cancelAlert("User Confirmed Safe")
                        },
                        onImmediateTrigger = {
                            GuardianForegroundService.instance?.handleTrigger(
                                alertState.triggerSource,
                                "Immediate User Confirmation"
                            )
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun BottomNavigationBar(
        selectedTab: Int,
        onSelectTab: (Int) -> Unit
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceCard,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    isSelected = selectedTab == 0,
                    onClick = { onSelectTab(0) }
                )
                BottomNavItem(
                    icon = Icons.Default.People,
                    label = "Contacts",
                    isSelected = selectedTab == 1,
                    onClick = { onSelectTab(1) }
                )
                BottomNavItem(
                    icon = Icons.Default.AdminPanelSettings,
                    label = "Owner",
                    isSelected = selectedTab == 2,
                    onClick = { onSelectTab(2) }
                )
            }
        }
    }

    @Composable
    private fun BottomNavItem(
        icon: ImageVector,
        label: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .background(
                    if (isSelected) PurpleHero else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) Color.White else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
