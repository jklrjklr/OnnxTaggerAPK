package com.example.onnxtagger.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.onnxtagger.OnnxTaggerApp
import com.example.onnxtagger.data.model.ModelProfile

@Composable
fun ProfileManagerScreen(
    activeProfileId: String,
    onLoadProfile: (ModelProfile) -> Unit,
    onSaveProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as OnnxTaggerApp
    val profiles by app.profileRepository.profilesFlow.collectAsStateWithLifecycle(emptyList())
    var newProfileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model Profiles") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Save-as-new-profile row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            if (newProfileName.isNotBlank()) {
                                onSaveProfile(newProfileName.trim())
                                newProfileName = ""
                            }
                        },
                        enabled = newProfileName.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save current settings as profile")
                    }
                }

                if (profiles.isEmpty()) {
                    Text(
                        "No saved profiles. Configure your model settings, enter a name above, and tap save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            ProfileRow(
                                profile = profile,
                                isActive = profile.id == activeProfileId,
                                onLoad = { onLoadProfile(profile) },
                                onDelete = { onDeleteProfile(profile.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ProfileRow(
    profile: ModelProfile,
    isActive: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isActive) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Active profile",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Spacer(Modifier.size(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "T: ${"%.2f".format(profile.temperature)}  Tags: ${profile.maxTags}  Tokens: ${profile.maxTokens}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = onLoad) { Text("Load") }

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete profile ${profile.name}",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
