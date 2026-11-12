package app.yadavar.shamsi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The first screen: two separate worlds in one app. "Personal" is the whole existing reminder app (private,
 * stored only on this phone). "Shared" is for groups; it never shows or receives personal reminders.
 */
@Composable
fun AppRoot(openRequest: MutableState<String?>) {
    var space by rememberSaveable { mutableStateOf<String?>(null) }
    // A tap on a widget row or a notification always goes straight to the personal reminders.
    LaunchedEffect(openRequest.value) {
        if (openRequest.value != null) space = "personal"
    }
    when (space) {
        "personal" -> AppScreen(openRequest, onBackToHub = { space = null })
        "shared" -> SharedHome(onBack = { space = null })
        else -> HubScreen(onPersonal = { space = "personal" }, onShared = { space = "shared" })
    }
}

@Composable
private fun HubScreen(onPersonal: () -> Unit, onShared: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .androidx_systemBars()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text("یادآور شمسی", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        SpaceCard(
            icon = Icons.Filled.Person,
            title = "یادآورهای شخصی",
            subtitle = "یادآوری‌های خصوصی من",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onPersonal,
        )
        SpaceCard(
            icon = Icons.Filled.Groups,
            title = "یادآورهای اشتراکی",
            subtitle = "گروه‌ها و کارهای مشترک",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onShared,
        )
    }
}

@Composable
private fun Modifier.androidx_systemBars(): Modifier =
    this.then(Modifier.windowInsetsPadding(WindowInsets.systemBars))

@Composable
private fun SpaceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
