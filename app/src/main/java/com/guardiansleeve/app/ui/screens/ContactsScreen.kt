package com.guardiansleeve.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardiansleeve.app.model.EmergencyContact
import com.guardiansleeve.app.ui.theme.*

@Composable
fun ContactsScreen(
    contacts: List<EmergencyContact>,
    onAddContact: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSoft)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Emergency Contacts (Top 5)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onAddContact) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = PurpleHero)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SOS alerts are automatically broadcasted to 3 fixed guardians + 2 dynamically selected nearest people.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }

        // Fixed Contacts Header
        item {
            Text(
                text = "Primary Fixed Contacts (3)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PurpleHero,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(contacts.filter { it.isFixed }) { contact ->
            ContactCard(contact = contact, isFixed = true)
        }

        // Dynamic Contacts Header
        item {
            Text(
                text = "Geographically Nearest Contacts (Top 2 Auto-Selected)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MintGreen,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(contacts.filter { !it.isFixed }) { contact ->
            ContactCard(contact = contact, isFixed = false)
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun ContactCard(contact: EmergencyContact, isFixed: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(if (isFixed) PurpleLight else MintLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFixed) Icons.Default.Shield else Icons.Default.NearMe,
                    contentDescription = null,
                    tint = if (isFixed) PurpleHero else MintGreen,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (isFixed) PurpleLight else MintLight,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isFixed) "Fixed" else "Dynamic",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFixed) PurpleHero else MintGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${contact.phone} • TG ID: ${contact.telegramChatId}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                if (!isFixed && contact.distanceInKm != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📍 %.1f km away from current GPS".format(contact.distanceInKm),
                        fontSize = 11.sp,
                        color = CoralRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Telegram Enabled",
                tint = SoftCyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
