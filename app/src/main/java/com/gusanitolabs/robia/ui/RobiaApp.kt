package com.gusanitolabs.robia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.designsystem.RobiaTheme

private sealed interface RobiaRoute {
    @get:StringRes
    val titleRes: Int

    data object Browse : RobiaRoute {
        override val titleRes = R.string.browse
    }

    data object ManageTags : RobiaRoute {
        override val titleRes = R.string.manage
    }

    data object AddEditClothing : RobiaRoute {
        override val titleRes = R.string.add_clothing
    }

    data object ItemDetail : RobiaRoute {
        override val titleRes = R.string.item_detail
    }

    data object LanguageSettings : RobiaRoute {
        override val titleRes = R.string.language
    }
}

private data class BottomNavDestination(
    val route: RobiaRoute,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomNavDestination(RobiaRoute.Browse, R.string.browse, Icons.Rounded.GridView),
    BottomNavDestination(RobiaRoute.AddEditClothing, R.string.add_clothing, Icons.Rounded.Add),
    BottomNavDestination(RobiaRoute.ManageTags, R.string.manage, Icons.Rounded.Style),
)

@Composable
fun RobiaApp() {
    RobiaTheme {
        RobiaShell()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RobiaShell() {
    var currentRoute: RobiaRoute by remember { mutableStateOf(RobiaRoute.Browse) }
    var settingsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    Box {
                        val settingsDescription = stringResource(R.string.content_settings_menu)
                        IconButton(
                            modifier = Modifier.semantics {
                                contentDescription = settingsDescription
                            },
                            onClick = { settingsExpanded = true },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                            )
                        }
                        SettingsMenu(
                            expanded = settingsExpanded,
                            onDismiss = { settingsExpanded = false },
                            onLanguageClick = {
                                currentRoute = RobiaRoute.LanguageSettings
                                settingsExpanded = false
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = {
            RobiaBottomBar(
                currentRoute = currentRoute,
                onRouteSelected = { currentRoute = it },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        RobiaNavHost(
            currentRoute = currentRoute,
            innerPadding = innerPadding,
            onRouteSelected = { currentRoute = it },
        )
    }
}

@Composable
private fun SettingsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onLanguageClick: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.language)) },
            leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
            onClick = onLanguageClick,
        )
        DropdownMenuItem(
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.data_sync))
                    Text(
                        text = stringResource(R.string.data_sync_coming_soon),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
            leadingIcon = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
            onClick = { },
            enabled = false,
        )
    }
}

@Composable
private fun RobiaBottomBar(
    currentRoute: RobiaRoute,
    onRouteSelected: (RobiaRoute) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        bottomDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onRouteSelected(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun RobiaNavHost(
    currentRoute: RobiaRoute,
    innerPadding: PaddingValues,
    onRouteSelected: (RobiaRoute) -> Unit,
) {
    when (currentRoute) {
        RobiaRoute.Browse -> WardrobeHome(innerPadding, onRouteSelected)
        RobiaRoute.ManageTags -> PlaceholderScreen(
            innerPadding = innerPadding,
            titleRes = R.string.manage_tags_title,
            bodyRes = R.string.manage_tags_body,
            icon = Icons.Rounded.Style,
        )
        RobiaRoute.AddEditClothing -> PlaceholderScreen(
            innerPadding = innerPadding,
            titleRes = R.string.add_edit_title,
            bodyRes = R.string.add_edit_body,
            icon = Icons.Rounded.Add,
        )
        RobiaRoute.ItemDetail -> PlaceholderScreen(
            innerPadding = innerPadding,
            titleRes = R.string.item_detail_title,
            bodyRes = R.string.item_detail_body,
            icon = Icons.Rounded.Info,
        )
        RobiaRoute.LanguageSettings -> LanguageSettingsScreen(innerPadding)
    }
}

@Composable
private fun WardrobeHome(
    innerPadding: PaddingValues,
    onRouteSelected: (RobiaRoute) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.wardrobe_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.wardrobe_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { FilterBar() }
        item { GarmentPreviewCard(onClick = { onRouteSelected(RobiaRoute.ItemDetail) }) }
        item { EmptyStateCard(onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) }) }
    }
}

@Composable
private fun FilterBar() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = false,
            onClick = { },
            label = { Text(stringResource(R.string.filters)) },
            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
        )
        AssistChip(
            onClick = { },
            label = { Text(stringResource(R.string.all_filters)) },
        )
    }
}

@Composable
private fun GarmentPreviewCard(onClick: () -> Unit) {
    val favoriteDescription = stringResource(R.string.content_favorite)
    val itemDescription = stringResource(R.string.content_open_item_detail)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = itemDescription }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GarmentImagePlaceholder()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sample_item_name),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.sample_item_brand),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        modifier = Modifier.semantics {
                            contentDescription = favoriteDescription
                        },
                        onClick = { },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalTag(text = stringResource(R.string.tag_summer))
                    TonalTag(text = stringResource(R.string.tag_casual))
                }
            }
        }
    }
}

@Composable
private fun GarmentImagePlaceholder() {
    Box(
        modifier = Modifier
            .width(96.dp)
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Style,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun TonalTag(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EmptyStateCard(onAddClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.empty_wardrobe_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.empty_wardrobe_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            AssistChip(
                onClick = onAddClick,
                label = { Text(stringResource(R.string.add_clothing)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(
    innerPadding: PaddingValues,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    icon: ImageVector,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(bodyRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSettingsScreen(innerPadding: PaddingValues) {
    val languages = stringArrayResource(R.array.language_choices)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.language_settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.language_settings_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                languages.forEachIndexed { index, language ->
                    ListItem(
                        headlineContent = { Text(language) },
                        leadingContent = { Icon(Icons.Rounded.Language, contentDescription = null) },
                    )
                    if (index != languages.lastIndex) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RobiaAppPreview() {
    RobiaApp()
}
