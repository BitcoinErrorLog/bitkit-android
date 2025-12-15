package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import to.bitkit.paykit.storage.RotationSettings
import to.bitkit.paykit.storage.RotationSettingsStorage
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn

@Composable
fun RotationSettingsScreen(
    onNavigateBack: () -> Unit,
    rotationSettingsStorage: RotationSettingsStorage? = null
) {
    // TODO: Create ViewModel for RotationSettings
    val settings = remember { mutableStateOf<RotationSettings?>(null) }
    
    ScreenColumn {
        AppTopBar(
            titleText = "Rotation Settings",
            onBackClick = onNavigateBack
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Global Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Title(text = "Global Settings")
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Rotate Enabled",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Automatically rotate endpoints after use",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.value?.autoRotateEnabled ?: false,
                            onCheckedChange = {
                                // TODO: Update settings
                            }
                        )
                    }
                    
                    OutlinedTextField(
                        value = settings.value?.defaultPolicy ?: "on-use",
                        onValueChange = { /* TODO: Update policy */ },
                        label = { Text("Default Policy") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true
                    )
                }
            }
            
            // Method Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Title(text = "Method Settings")
                    
                    settings.value?.methodSettings?.entries?.forEach { (methodId, methodSettings) ->
                        MethodRotationCard(
                            methodId = methodId,
                            methodSettings = methodSettings
                        )
                    } ?: run {
                        Text(
                            text = "No method-specific settings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MethodRotationCard(
    methodId: String,
    methodSettings: to.bitkit.paykit.storage.MethodRotationSettings
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = methodId,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Policy: ${methodSettings.policy}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Use count: ${methodSettings.useCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Rotations: ${methodSettings.rotationCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

