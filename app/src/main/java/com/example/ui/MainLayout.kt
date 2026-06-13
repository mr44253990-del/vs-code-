package com.example.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.MainViewModel
import com.example.data.ExtensionItem
import com.example.data.ProjectFile
import com.example.ui.theme.CodeSyntaxVisualTransformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Theme mapping values
    val colors = when (viewModel.selectedTheme) {
        "Light" -> AppColors.LightTheme
        "AMOLED" -> AppColors.AmoledTheme
        else -> AppColors.DarkTheme
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        bottomBar = {
            // Status bar at the very bottom
            BottomStatusBar(viewModel, colors)
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            // 1. VS Code LHS Sidebar Rail Navigation
            VSSidebarRail(viewModel, colors)

            // 2. Collapsible sidebar panel drawer (Explorer, Search, Git, Extensions, AI, Settings)
            AnimatedVisibility(
                visible = viewModel.isSidebarExpanded && viewModel.activeSidebarTab.isNotEmpty(),
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                SidebarPanePanel(viewModel, colors)
            }

            // 3. Central Working Area: Tabs Bar, Editor Area, Bottom split terminal, Live preview
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(colors.editorBg)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Editor & optional Tab side, or WebView live split-screen preview
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Opened Tabs Bar Row
                        TabsHeaderRow(viewModel, colors)

                        // Main Editor Area with Left line numbering gutter
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(colors.editorBg)
                        ) {
                            if (viewModel.activeFile != null) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    // Gutter line numbers column (scroll matches editor height)
                                    LineNumbersGutter(viewModel, colors)

                                    // Interactive typing view with custom Syntax Highlight
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                            .horizontalScroll(rememberScrollState())
                                            .padding(8.dp)
                                    ) {
                                        BasicTextField(
                                            value = viewModel.editorText,
                                            onValueChange = { viewModel.onEditorTextChange(it) },
                                            textStyle = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 14.sp,
                                                color = colors.editorText,
                                                lineHeight = 20.sp
                                            ),
                                            cursorBrush = SolidColor(colors.accentNeon),
                                            visualTransformation = CodeSyntaxVisualTransformation(
                                                language = viewModel.activeFile?.language ?: "text",
                                                isDarkTheme = viewModel.selectedTheme != "Light",
                                                isAmoled = viewModel.selectedTheme == "AMOLED"
                                            ),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("code_editor_field")
                                        )
                                    }
                                }
                            } else {
                                // Default landing view when no tags are open
                                WorkspaceEmptyState(viewModel, colors)
                            }

                            // Glowing Floating Play/Run action button at the bottom end
                            FloatingActionButton(
                                onClick = {
                                    viewModel.executeTerminalCommand("npm run dev")
                                    viewModel.isSplitScreen = true
                                    Toast.makeText(context, "Spun local Live Server at port 3000!", Toast.LENGTH_SHORT).show()
                                },
                                containerColor = colors.accentNeon,
                                contentColor = colors.accentOn,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .testTag("floating_run_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Run code live"
                                )
                            }
                        }
                    }

                    // Split-Screen side panel (renders Live WebView simulator!)
                    if (viewModel.isSplitScreen) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(1.dp, colors.borderBorder, RoundedCornerShape(0.dp))
                        ) {
                            LiveBrowserPreviewPane(viewModel, colors)
                        }
                    }
                }

                // Collapsible Bottom virtual Output/Terminal zone section
                if (viewModel.isTerminalVisible) {
                    VConsoleArea(viewModel, colors)
                }
            }
        }
    }

    // Voice coding trigger overlay dialog helper
    if (viewModel.isVoiceActive) {
        VoiceCodingSimulationOverlay(viewModel, colors)
    }
}

// 1. VS Code LHS Sidebar Rail Navigation
@Composable
fun VSSidebarRail(
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    val items = listOf(
        SidebarIconItem("Explorer", Icons.Default.Folder, "Workspace file explorer"),
        SidebarIconItem("Search", Icons.Default.Search, "Replace tool"),
        SidebarIconItem("Git", Icons.Default.Share, "Changes and staging"), // resembling git branch
        SidebarIconItem("Extensions", Icons.Default.GridView, "Plugin extensions"),
        SidebarIconItem("AI Assistant", Icons.Default.Face, "AI Coding assistant"),
        SidebarIconItem("Settings", Icons.Default.Settings, "Workspace preferences")
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(56.dp)
            .background(colors.sidebarBg)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Visual Logo at top
            IconButton(
                onClick = { viewModel.isSidebarExpanded = !viewModel.isSidebarExpanded },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Toggle Sidebar Panel spacing",
                    tint = colors.accentNeon,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Top selection elements
            items.forEach { item ->
                val isActive = viewModel.activeSidebarTab == item.name && viewModel.isSidebarExpanded
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) colors.sidebarActiveBg else Color.Transparent)
                        .clickable {
                            if (viewModel.activeSidebarTab == item.name && viewModel.isSidebarExpanded) {
                                viewModel.isSidebarExpanded = false
                            } else {
                                viewModel.activeSidebarTab = item.name
                                viewModel.isSidebarExpanded = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.desc,
                            tint = if (isActive) colors.accentNeon else colors.textMuted,
                            modifier = Modifier
                                .run { if (isActive) size(24.dp) else size(22.dp) }
                                .align(Alignment.Center)
                        )

                        // Special Badge for Git alterations
                        if (item.name == "Git" && (viewModel.gitChanges.value.isNotEmpty() || viewModel.stagedChanges.value.isNotEmpty())) {
                            val count = viewModel.gitChanges.value.size
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(16.dp)
                                        .background(colors.accentBadge, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = count.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Lower profile badge matching visual mockups
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { viewModel.isVoiceActive = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.accentBadge.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Coding commands trigger",
                    tint = colors.accentBadge,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(colors.accentNeon.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, colors.accentNeon, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "R",
                    color = colors.accentNeon,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class SidebarIconItem(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val desc: String)

// 2. Collapsible sidebar expanded panel drawer
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarPanePanel(
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    val context = LocalContext.current
    val projectFiles by viewModel.projectFiles.collectAsState()
    val allProjects by viewModel.allProjects.collectAsState()

    var showNewFilePrompt by remember { mutableStateOf(false) }
    var folderTargetForNewFile by remember { mutableStateOf("") }
    var newFileNameInput by remember { mutableStateOf("") }
    var isFolderInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(colors.sidebarPaneBg)
            .border(
                width = 1.dp,
                brush = SolidColor(colors.borderBorder),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(vertical = 12.dp)
    ) {
        // Header Name tag of Pane
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = viewModel.activeSidebarTab.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary,
                letterSpacing = 1.sp
            )
            
            IconButton(
                onClick = { viewModel.isSidebarExpanded = false },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close pane",
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.borderBorder)

        // Renders content based on selected LHS Rail Tab
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (viewModel.activeSidebarTab) {
                "Explorer" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Project header picker
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.sidebarBg.copy(alpha = 0.4f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "WORKSPACE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textMuted
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        folderTargetForNewFile = ""
                                        isFolderInput = false
                                        showNewFilePrompt = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add new code file",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        folderTargetForNewFile = ""
                                        isFolderInput = true
                                        showNewFilePrompt = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreateNewFolder,
                                        contentDescription = "Add new directory",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Project selector dropdown list
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            var expandedProjMenu by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.sidebarActiveBg)
                                    .clickable { expandedProjMenu = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Workspaces,
                                        contentDescription = "Project",
                                        tint = colors.accentNeon,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = viewModel.activeProject?.name ?: "No project selected",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown list indicator",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Dynamic Dropdown picker lists
                            DropdownMenu(
                                expanded = expandedProjMenu,
                                onDismissRequest = { expandedProjMenu = false },
                                modifier = Modifier.background(colors.sidebarPaneBg)
                            ) {
                                allProjects.forEach { proj ->
                                    DropdownMenuItem(
                                        text = { Text(proj.name, color = colors.textPrimary) },
                                        onClick = {
                                            viewModel.loadProject(proj)
                                            expandedProjMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.FolderOpen, null, tint = colors.accentNeon) }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("New Project", color = colors.accentNeon, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.createProject("Workspace Project ${allProjects.size + 1}")
                                        expandedProjMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.AddCircle, null, tint = colors.accentNeon) }
                                )
                            }
                        }

                        // File managers tree listing
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            // Folders first
                            val folders = projectFiles.filter { it.isFolder }
                            val rootFiles = projectFiles.filter { !it.isFolder && it.parentPath.isEmpty() }

                            items(folders) { folder ->
                                val folderFiles = projectFiles.filter { !it.isFolder && it.parentPath == folder.path }
                                var isExpanded by remember { mutableStateOf(true) }

                                Column {
                                    // Render Folder Item
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .combinedClickable(
                                                onClick = { isExpanded = !isExpanded },
                                                onLongClick = { viewModel.deleteFileFromWorkspace(folder) }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                                contentDescription = "Folder ${folder.name}",
                                                tint = colors.accentNeon,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = folder.name,
                                                fontSize = 13.sp,
                                                color = colors.textPrimary
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                folderTargetForNewFile = folder.path
                                                isFolderInput = false
                                                showNewFilePrompt = true
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Create within folder",
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    // Render Child files if expanded
                                    if (isExpanded) {
                                        folderFiles.forEach { child ->
                                            val isSelectedTab = viewModel.activeFile?.id == child.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 20.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelectedTab) colors.sidebarActiveBg else Color.Transparent)
                                                    .combinedClickable(
                                                        onClick = { viewModel.selectTab(child) },
                                                        onLongClick = { viewModel.deleteFileFromWorkspace(child) }
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = getIconForFilename(child.name),
                                                        contentDescription = child.name,
                                                        tint = getIconColorForFilename(child.name, colors),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = child.name,
                                                        fontSize = 12.sp,
                                                        color = if (isSelectedTab) colors.accentNeon else colors.textSecondary
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.deleteFileFromWorkspace(child) },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete file",
                                                        tint = colors.textMuted.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Files at root folder
                            items(rootFiles) { file ->
                                val isSelected = viewModel.activeFile?.id == file.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) colors.sidebarActiveBg else Color.Transparent)
                                        .combinedClickable(
                                            onClick = { viewModel.selectTab(file) },
                                            onLongClick = { viewModel.deleteFileFromWorkspace(file) }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = getIconForFilename(file.name),
                                            contentDescription = file.name,
                                            tint = getIconColorForFilename(file.name, colors),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = file.name,
                                            fontSize = 12.sp,
                                            color = if (isSelected) colors.accentNeon else colors.textPrimary
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteFileFromWorkspace(file) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete file",
                                            tint = colors.textMuted.copy(alpha = 0.5f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                "Search" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Search & Replace", fontSize = 11.sp, color = colors.textMuted)

                        OutlinedTextField(
                            value = viewModel.searchReplaceQuery,
                            onValueChange = { viewModel.searchReplaceQuery = it },
                            placeholder = { Text("Search text...", fontSize = 12.sp, color = colors.textMuted) },
                            textStyle = TextStyle(fontSize = 12.sp),
                            colors = getVSOtdColors(colors),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = viewModel.replaceWithQuery,
                            onValueChange = { viewModel.replaceWithQuery = it },
                            placeholder = { Text("Replace with...", fontSize = 12.sp, color = colors.textMuted) },
                            textStyle = TextStyle(fontSize = 12.sp),
                            colors = getVSOtdColors(colors),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val text = viewModel.editorText
                                if (viewModel.searchReplaceQuery.isNotEmpty()) {
                                    val newText = text.replace(viewModel.searchReplaceQuery, viewModel.replaceWithQuery)
                                    viewModel.onEditorTextChange(newText)
                                    Toast.makeText(context, "Matched patterns replaced!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeon, contentColor = colors.accentOn),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Replace All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "Git" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Share, null, tint = colors.accentNeon, modifier = Modifier.size(14.dp))
                                Text(viewModel.activeBranch, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            }
                            IconButton(onClick = { /* Refresh */ }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Refresh, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Changes lists
                        Text("Changes (${viewModel.gitChanges.value.size})", fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.Bold)
                        LazyColumn(modifier = Modifier.weight(0.4f).fillMaxWidth()) {
                            items(viewModel.gitChanges.value) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(file.name, fontSize = 12.sp, color = colors.textPrimary)
                                        Text("M", fontSize = 10.sp, color = Color(0xFFFACC15), fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { viewModel.stageFile(file) }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Add, null, tint = colors.accentNeon, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Staged zone
                        Text("Staged Changes (${viewModel.stagedChanges.value.size})", fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.Bold)
                        LazyColumn(modifier = Modifier.weight(0.4f).fillMaxWidth()) {
                            items(viewModel.stagedChanges.value) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(file.name, fontSize = 12.sp, color = colors.textPrimary)
                                        Text("A", fontSize = 10.sp, color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { viewModel.unstageFile(file) }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Remove, null, tint = colors.accentBadge, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Commit input
                        OutlinedTextField(
                            value = viewModel.gitCommitMessage,
                            onValueChange = { viewModel.gitCommitMessage = it },
                            placeholder = { Text("Update UI and layouts", fontSize = 12.sp, color = colors.textMuted) },
                            textStyle = TextStyle(fontSize = 12.sp),
                            colors = getVSOtdColors(colors),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )

                        Button(
                            onClick = { viewModel.commitGitChanges() },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeon, contentColor = colors.accentOn),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().testTag("git_commit_button")
                        ) {
                            Text("Commit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "Extensions" -> {
                    val extensionsList by viewModel.extensions.collectAsState()
                    var isMarketplaceTab by remember { mutableStateOf(true) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.sidebarActiveBg)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isMarketplaceTab = true }
                                    .background(if (isMarketplaceTab) colors.accentNeon.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Marketplace", fontSize = 11.sp, color = if (isMarketplaceTab) colors.accentNeon else colors.textMuted, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isMarketplaceTab = false }
                                    .background(if (!isMarketplaceTab) colors.accentNeon.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Installed", fontSize = 11.sp, color = if (!isMarketplaceTab) colors.accentNeon else colors.textMuted, fontWeight = FontWeight.Bold)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val itemsToShow = if (isMarketplaceTab) {
                                extensionsList
                            } else {
                                extensionsList.filter { it.isInstalled }
                            }

                            items(itemsToShow) { ext ->
                                ExtensionCardRow(ext, viewModel, colors)
                            }
                        }
                    }
                }

                "AI Assistant" -> {
                    val messages by viewModel.chatMessages.collectAsState()
                    val chatListState = rememberLazyListState()

                    // Auto scroll chat to newest messages
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) {
                            chatListState.animateScrollToItem(messages.size - 1)
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        Text(
                            text = "Smart Assistant Chat",
                            fontSize = 11.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages) { msg ->
                                val isUser = msg.sender == "user"
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isUser) Icons.Default.Person else Icons.Default.Face,
                                            contentDescription = null,
                                            tint = if (isUser) colors.textMuted else colors.accentNeon,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = if (isUser) "You" else "AI Assistant",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textMuted
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isUser) colors.sidebarActiveBg else colors.sidebarBg)
                                            .padding(10.dp)
                                    ) {
                                        // Styled response blocks
                                        if (!isUser && msg.content.contains("```")) {
                                            AiMarkdownCodeBlock(msg.content, colors)
                                        } else {
                                            Text(
                                                text = msg.content,
                                                fontSize = 12.sp,
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            if (viewModel.isAiGenerating) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            color = colors.accentNeon,
                                            strokeWidth = 2.dp
                                        )
                                        Text("AI is writing code...", fontSize = 11.sp, color = colors.textMuted)
                                    }
                                }
                            }
                        }

                        // Input control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BasicTextField(
                                value = viewModel.aiChatInput,
                                onValueChange = { viewModel.aiChatInput = it },
                                textStyle = TextStyle(fontSize = 12.sp, color = colors.textPrimary),
                                cursorBrush = SolidColor(colors.accentNeon),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    viewModel.queryAiAssistant(viewModel.aiChatInput)
                                }),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.sidebarBg)
                                    .border(1.dp, colors.borderBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("ai_input_text")
                            )

                            IconButton(
                                onClick = { viewModel.queryAiAssistant(viewModel.aiChatInput) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(colors.accentNeon, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send prompt",
                                    tint = colors.accentOn,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                "Settings" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("IDE PREFERENCES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)

                        // Auto save
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Auto Save", fontSize = 13.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Write files directly to workspace", fontSize = 11.sp, color = colors.textMuted)
                            }
                            Switch(
                                checked = viewModel.isAutoSaveEnabled,
                                onCheckedChange = { viewModel.isAutoSaveEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = colors.accentNeon)
                            )
                        }

                        HorizontalDivider(color = colors.borderBorder)

                        // Themes selection picker
                        Text("THEME SELECTOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                        val themeList = listOf("Dark", "Light", "AMOLED")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            themeList.forEach { th ->
                                val isSelected = viewModel.selectedTheme == th
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) colors.accentNeon.copy(alpha = 0.15f) else colors.sidebarBg)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) colors.accentNeon else colors.borderBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.selectedTheme = th }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$th Theme",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.accentNeon else colors.textPrimary
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = colors.accentNeon,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = colors.borderBorder)

                        // Action utilities
                        Button(
                            onClick = { viewModel.deleteActiveProject() },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentBadge),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete current Project", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Modal Popup creator file / folder dialog helper
    if (showNewFilePrompt) {
        AlertDialog(
            onDismissRequest = { showNewFilePrompt = false },
            containerColor = colors.sidebarPaneBg,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Create New ${if (isFolderInput) "Folder" else "File"}",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (folderTargetForNewFile.isEmpty()) "Location: Root Workspace" else "Location: $folderTargetForNewFile",
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        placeholder = { Text("e.g. index.html or script.js", fontSize = 12.sp) },
                        colors = getVSOtdColors(colors),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_filename_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFileNameInput.trim().isNotEmpty()) {
                            viewModel.createNewFileInWorkspace(newFileNameInput.trim(), isFolderInput, folderTargetForNewFile)
                            newFileNameInput = ""
                            showNewFilePrompt = false
                        }
                    }
                ) {
                    Text("Create", color = colors.accentNeon, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFilePrompt = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}

// 3. Opened Tabs Bar Row
@Composable
fun TabsHeaderRow(
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    val tabs = viewModel.openTabs.value
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(colors.sidebarBg)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { file ->
            val isActive = viewModel.activeFile?.id == file.id
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(if (isActive) colors.editorBg else colors.sidebarBg)
                    .border(
                        width = 1.dp,
                        brush = SolidColor(colors.borderBorder),
                        shape = RoundedCornerShape(0.dp)
                    )
                    .clickable { viewModel.selectTab(file) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = getIconForFilename(file.name),
                        contentDescription = file.name,
                        tint = getIconColorForFilename(file.name, colors),
                        modifier = Modifier.size(14.dp)
                    )
                    
                    Text(
                        text = file.name,
                        fontSize = 12.sp,
                        color = if (isActive) colors.accentNeon else colors.textSecondary,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )

                    IconButton(
                        onClick = { viewModel.closeTab(file) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close file",
                            tint = colors.textMuted,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
                
                // Active underline neon bar
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(colors.accentNeon)
                    )
                }
            }
        }

        // Add visual help tooltip if tab is empty
        if (tabs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No open files",
                    fontSize = 11.sp,
                    color = colors.textMuted,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// Left visual lines gutter numbering
@Composable
fun LineNumbersGutter(
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    val text = viewModel.editorText
    val lineCount = maxOf(text.lines().size, 1)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(42.dp)
            .background(colors.sidebarBg.copy(alpha = 0.5f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (i in 1..lineCount) {
            Text(
                text = i.toString(),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.textMuted.copy(alpha = 0.6f),
                modifier = Modifier.height(20.dp)
            )
        }
    }
}

// 4. Floating local Live Server preview
@Composable
fun LiveBrowserPreviewPane(
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Address Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.sidebarBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { viewModel.isSplitScreen = false },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
            }

            // URL input address simulator
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.sidebarPaneBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Lock, null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
                    Text(viewModel.livePreviewUrl, fontSize = 11.sp, color = colors.textPrimary)
                }
                
                if (viewModel.isPreviewWebLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = colors.accentNeon, strokeWidth = 1.6.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, tint = colors.accentNeon, modifier = Modifier.size(12.dp).clickable {
                        viewModel.recompileHtmlPreview()
                    })
                }
            }
        }

        // WebView rendering
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            // Load compiled Sandbox HTML codes
            val html = viewModel.htmlCompiledContent
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        loadDataWithBaseURL("http://localhost", html, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL("http://localhost", html, "text/html", "UTF-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// 5. Shell Terminal Panel
@Composable
fun VConsoleArea(
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    val logs = viewModel.terminalLogs.value
    val listState = rememberLazyListState()

    // Auto scroll bottom output log
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(colors.sidebarBg)
            .border(1.dp, colors.borderBorder, RoundedCornerShape(0.dp))
    ) {
        // Console selection headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.sidebarBg)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf("PROBLEMS", "OUTPUT", "DEBUG CONSOLE", "TERMINAL")
                tabs.forEach { t ->
                    val isActive = viewModel.activeConsoleTab == t
                    Text(
                        text = t,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) colors.accentNeon else colors.textMuted,
                        modifier = Modifier
                            .clickable { viewModel.activeConsoleTab = t }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            IconButton(
                onClick = { viewModel.isTerminalVisible = false },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
            }
        }

        HorizontalDivider(color = colors.borderBorder)

        // Command executions screen area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.background)
                .padding(8.dp)
        ) {
            if (viewModel.activeConsoleTab == "TERMINAL") {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(logs) { line ->
                            Text(
                                text = line,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = if (line.startsWith("$") || line.contains("npm")) colors.accentNeon else colors.editorText
                                )
                            )
                        }
                    }

                    // Input CLI basic terminal row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RakibCodeStudio ~ /project $ ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = colors.textMuted
                        )
                        BasicTextField(
                            value = viewModel.terminalInput,
                            onValueChange = { viewModel.terminalInput = it },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = colors.editorText
                            ),
                            cursorBrush = SolidColor(colors.accentNeon),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                viewModel.executeTerminalCommand(viewModel.terminalInput)
                            }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("terminal_input_field")
                        )
                    }
                }
            } else {
                // Fake outputs for secondary lists
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No logs in ${viewModel.activeConsoleTab}. Everything is compiled cleanly.",
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}

// Status Bar at the bottom
@Composable
fun BottomStatusBar(
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(colors.sidebarActiveBg)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (viewModel.isLiveServerOn) Color(0xFF22C55E) else colors.textMuted, CircleShape)
                )
                Text(
                    text = if (viewModel.isLiveServerOn) "Live Server: 3000" else "Live Server: Off",
                    fontSize = 10.sp,
                    color = colors.textSecondary
                )
            }

            Text("Spaces: 4", fontSize = 10.sp, color = colors.textMuted)
            Text("UTF-8", fontSize = 10.sp, color = colors.textMuted)
            Text(viewModel.activeFile?.language?.uppercase() ?: "HTML", fontSize = 10.sp, color = colors.textMuted)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Status: Clean", fontSize = 10.sp, color = colors.textSecondary)
            Text("Ln 13, Col 28", fontSize = 10.sp, color = colors.textMuted)
        }
    }
}

@Composable
fun ExtensionCardRow(
    ext: ExtensionItem,
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.sidebarBg)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(ext.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text("By ${ext.author} • ${ext.downloads} downloads", fontSize = 10.sp, color = colors.textMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(ext.description, fontSize = 9.sp, color = colors.textSecondary, maxLines = 2)
        }

        Button(
            onClick = { viewModel.toggleExtension(ext) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (ext.isInstalled) colors.sidebarActiveBg else colors.accentNeon,
                contentColor = if (ext.isInstalled) colors.textPrimary else colors.accentOn
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.height(26.dp)
        ) {
            Text(
                text = if (ext.isInstalled) "Uninstall" else "Install",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AiMarkdownCodeBlock(
    text: String,
    colors: CustomThemeColors
) {
    // A simple beautiful code parser for chatbot response markdown
    val blocks = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEachIndexed { idx, content ->
            if (idx % 2 != 0) {
                // Inside block code
                val parts = content.trim().split("\n", limit = 2)
                val lang = parts.getOrNull(0) ?: ""
                val code = parts.getOrNull(1) ?: ""

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0C101B))
                        .border(1.dp, colors.accentNeon.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF05070D))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(lang.uppercase(), fontSize = 9.sp, color = colors.accentNeon, fontWeight = FontWeight.Bold)
                        Text("Copy", fontSize = 9.sp, color = colors.textMuted, modifier = Modifier.clickable {
                            // Copy to clipboard simulations
                        })
                    }
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                if (content.isNotEmpty()) {
                    Text(content, fontSize = 12.sp, color = colors.textPrimary)
                }
            }
        }
    }
}

@Composable
fun VoiceCodingSimulationOverlay(
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    var timerVal by remember { mutableStateOf(4) }
    var userVoiceInput by remember { mutableStateOf("run code") }

    LaunchedEffect(Unit) {
        while (timerVal > 0) {
            delay(1000)
            timerVal--
        }
        viewModel.executeVoiceCodingCommand(userVoiceInput)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { viewModel.isVoiceActive = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(30.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.sidebarPaneBg)
                .border(2.dp, colors.accentNeon, RoundedCornerShape(16.dp))
                .padding(26.dp)
        ) {
            Text("Voice Coding Engine Active", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            
            // Visual waveform simulation glow
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(40.dp)
            ) {
                val heights = listOf(12, 38, 20, 48, 16, 42, 28, 10, 36, 12)
                heights.forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(h.dp)
                            .clip(CircleShape)
                            .background(colors.accentNeon)
                    )
                }
            }

            Text("Try speaking standard keywords: \n • \"create file\" \n • \"run code\" \n • \"open preview\" \n • \"save project\"", fontSize = 11.sp, color = colors.textMuted)

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = userVoiceInput,
                onValueChange = { userVoiceInput = it },
                label = { Text("Simulate spoken word") },
                colors = getVSOtdColors(colors),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Auto triggering voice action in ${timerVal}s...", fontSize = 11.sp, color = colors.accentBadge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WorkspaceEmptyState(
    viewModel: MainViewModel,
    colors: CustomThemeColors
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.sidebarPaneBg.copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(colors.accentNeon, Color(0xFF38BDF8)))),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Glow logo wrapper mimicking the diamond template
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(colors.accentNeon, Color(0xFF38BDF8), colors.accentNeon)
                            )
                        )
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(colors.sidebarBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "</>",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentNeon
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Rakib Code Studio",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Mobile IDE for Developers",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF38BDF8), // Cyan glow
                        letterSpacing = 0.8.sp
                    )
                }

                // Custom separator line
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(1.dp)
                        .background(colors.borderBorder)
                )

                Text(
                    text = "Build. Preview. Deploy.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Create or open files from the Explorer sidebar list to start coding. Run live previews instantly via the Floating Run Button.",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 16.sp
                )

                // Quick buttons shortcut
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Button(
                        onClick = { viewModel.isSidebarExpanded = !viewModel.isSidebarExpanded },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentNeon,
                            contentColor = colors.accentOn
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.accentOn
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Toggle Sidebar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentOn
                        )
                    }
                }

                // Interactive Voice Feedback Console indicator if active
                if (viewModel.voiceFeedback.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accentBadge.copy(alpha = 0.15f))
                            .border(1.dp, colors.accentBadge.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = colors.accentBadge,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = viewModel.voiceFeedback,
                                fontSize = 11.sp,
                                color = colors.accentBadge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

// Icon selection mapping helper
fun getIconForFilename(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = name.substringAfterLast('.').lowercase()
    return when (ext) {
        "html", "htm" -> Icons.Default.Html
        "css" -> Icons.Default.Css
        "js", "javascript" -> Icons.Default.Javascript
        "json" -> Icons.Default.Description
        "py", "python" -> Icons.Default.Gamepad // fallback resembling
        "md", "markdown" -> Icons.Default.Article
        else -> Icons.Default.Code
    }
}

fun getIconColorForFilename(name: String, colors: CustomThemeColors): Color {
    val ext = name.substringAfterLast('.').lowercase()
    return when (ext) {
        "html", "htm" -> Color(0xFFFB923C) // Orange
        "css" -> Color(0xFF38BDF8) // Sky blue
        "js", "javascript" -> Color(0xFFFACC15) // Yellow
        "json" -> Color(0xFF34D399) // Mint green
        "md", "markdown" -> Color(0xFF818CF8) // Indigo purple
        else -> colors.accentNeon
    }
}

// Helpers for unified VS Editor style mapping OutlinedTextField colors
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun getVSOtdColors(colors: CustomThemeColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = colors.accentNeon,
    unfocusedBorderColor = colors.borderBorder,
    focusedTextColor = colors.textPrimary,
    unfocusedTextColor = colors.textSecondary,
    cursorColor = colors.accentNeon,
    focusedContainerColor = colors.sidebarBg,
    unfocusedContainerColor = colors.sidebarBg
)

// Define structural Theme custom colors
data class CustomThemeColors(
    val background: Color,
    val sidebarBg: Color,
    val sidebarPaneBg: Color,
    val sidebarActiveBg: Color,
    val editorBg: Color,
    val borderBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val editorText: Color,
    val accentNeon: Color,
    val accentOn: Color,
    val accentBadge: Color
)

object AppColors {
    val DarkTheme = CustomThemeColors(
        background = Color(0xFF070913),          // Deep space midnight background
        sidebarBg = Color(0xFF04060B),           // Solid midnight-dark LHS sidebar rail
        sidebarPaneBg = Color(0xFF0A0D18),       // Sleek workspace explorer drawer blue-slate
        sidebarActiveBg = Color(0xFF1E1F35),     // Distinct active/highlight indigo state
        editorBg = Color(0xFF0C0F1D),            // Dark slate editor layout background
        borderBorder = Color(0xFF161B30),        // Subtle deep accent borders
        textPrimary = Color(0xFFF8FAFC),         // Pristine bright text
        textSecondary = Color(0xFF94A3B8),       // Soft text descriptions
        textMuted = Color(0xFF475569),           // Non-intrusive muted placeholders
        editorText = Color(0xFFE2E8F0),          // Sleek code text
        accentNeon = Color(0xFF8B5CF6),          // Vibrant theme purple (primary)
        accentOn = Color.White,
        accentBadge = Color(0xFFA855F7)          // Neon violet highlight for badges and details
    )

    val AmoledTheme = CustomThemeColors(
        background = Color(0xFF000000),          // Absolute pure black
        sidebarBg = Color(0xFF000000),           // Absolute pure black sidebar
        sidebarPaneBg = Color(0xFF050508),       // Pitch black drawer
        sidebarActiveBg = Color(0xFF121218),     // Subtle active indicator
        editorBg = Color(0xFF000000),            // Black editor block
        borderBorder = Color(0xFF111116),        // Faint border lines
        textPrimary = Color(0xFFFFFFFF),         // Clean pure white text
        textSecondary = Color(0xFFCBD5E1),       // Soft secondary text
        textMuted = Color(0xFF5E6D82),           // Muted slate info
        editorText = Color(0xFFF1F5F9),          // Contrast editor keys
        accentNeon = Color(0xFF38BDF8),          // Glowing neon Sky Blue
        accentOn = Color.Black,
        accentBadge = Color(0xFF06B6D4)          // Glowing deep Cyan
    )

    val LightTheme = CustomThemeColors(
        background = Color(0xFFF1F5F9),
        sidebarBg = Color(0xFFCBD5E1),
        sidebarPaneBg = Color(0xFFE2E8F0),
        sidebarActiveBg = Color(0xFFF8FAFC),
        editorBg = Color(0xFFFFFFFF),
        borderBorder = Color(0xFFCBD5E1),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF334155),
        textMuted = Color(0xFF64748B),
        editorText = Color(0xFF0F172A),
        accentNeon = Color(0xFF6D28D9), // Rich Deep Purple
        accentOn = Color.White,
        accentBadge = Color(0xFFDC2626)
    )
}
