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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import android.net.Uri
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

    // File Picker & ZIP Destination SAF launchers
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val contentResolver = context.contentResolver
                val filename = getFileNameFromUri(context, it) ?: "imported_file.txt"
                val inputStream = contentResolver.openInputStream(it)
                val fileContent = inputStream?.bufferedReader()?.use { r -> r.readText() } ?: ""
                viewModel.importExternalFile(filename, fileContent)
                Toast.makeText(context, "$filename imported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val zipCreatorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val zipBytes = viewModel.getProjectZipBytes()
                if (zipBytes != null) {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(zipBytes)
                    }
                    Toast.makeText(context, "Zip project successfully exported!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Workspace empty or could not map files.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ZIP export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val writeG = perms[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        val readG = perms[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (writeG || readG) {
            Toast.makeText(context, "File Manager access granted successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
    
    // Theme mapping values
    val colors = when (viewModel.selectedTheme) {
        "Light" -> AppColors.LightTheme
        "AMOLED" -> AppColors.AmoledTheme
        else -> AppColors.DarkTheme
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                Box(modifier = Modifier.width(180.dp)) {
                    SidebarPanePanel(
                        viewModel = viewModel,
                        colors = colors,
                        onPickFile = { filePickerLauncher.launch("*/*") },
                        onExportZip = { zipCreatorLauncher.launch("RakibCodeStudio_${viewModel.activeProject?.name ?: "project"}.zip") }
                    )
                }
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
                    if (!viewModel.isPreviewFullScreen) {
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
                                        if (viewModel.isLineNumbersVisible) {
                                            LineNumbersGutter(viewModel, colors)
                                        }

                                        // Interactive typing view with custom Syntax Highlight
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .verticalScroll(rememberScrollState())
                                                .then(
                                                    if (viewModel.isWordWrapEnabled) Modifier else Modifier.horizontalScroll(rememberScrollState())
                                                )
                                                .pointerInput(Unit) {
                                                    detectTransformGestures { _, _, zoom, _ ->
                                                        if (zoom != 1f) {
                                                            val newSize = (viewModel.editorFontSize * zoom).toInt()
                                                            if (newSize in 8..40) {
                                                                viewModel.editorFontSize = newSize
                                                            }
                                                        }
                                                    }
                                                }
                                                .padding(6.dp)
                                        ) {
                                            BasicTextField(
                                                value = TextFieldValue(
                                                    text = viewModel.editorText,
                                                    selection = TextRange(viewModel.editorText.length) // Simplified tracking
                                                ),
                                                onValueChange = { 
                                                    viewModel.onEditorTextChange(it.text)
                                                    viewModel.updateCursorPosition(it.selection.start)
                                                },
                                                textStyle = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = viewModel.editorFontSize.sp,
                                                    color = colors.editorText,
                                                    lineHeight = (viewModel.editorFontSize * 1.4).sp
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
                    }

                    // Split-Screen side panel (renders Live WebView simulator!)
                    if (viewModel.isSplitScreen || viewModel.isPreviewFullScreen) {
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

    // Elegant and premium Splash Loading Screen Overlay
    androidx.compose.animation.AnimatedVisibility(
        visible = viewModel.isSplashVisible,
        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
        exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(800))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF04060B)), // Eye-friendly deep slate primary canvas
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                        .border(2.dp, Color(0xFF38BDF8), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Logo",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Rakib Code Studio",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Visual Cloud Git IDE",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(24.dp))

                CircularProgressIndicator(
                    color = Color(0xFF38BDF8),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Mounting workspace environment...",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
            }
        }
    }
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
            .width(52.dp)
            .background(colors.sidebarBg)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Visual Logo at top
            IconButton(
                onClick = { viewModel.isSidebarExpanded = !viewModel.isSidebarExpanded },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Toggle Sidebar Panel spacing",
                    tint = colors.accentNeon,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Top selection elements
            items.forEach { item ->
                val isActive = viewModel.activeSidebarTab == item.name && viewModel.isSidebarExpanded
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
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
                                .run { if (isActive) size(20.dp) else size(16.dp) }
                                .align(Alignment.Center)
                        )

                        // Special Badge for Git alterations
                        if (item.name == "Git" && (viewModel.gitChanges.value.isNotEmpty() || viewModel.stagedChanges.value.isNotEmpty())) {
                            val count = viewModel.gitChanges.value.size
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(16.dp)
                                        .background(colors.accentBadge, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = count.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
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
                    modifier = Modifier.size(32.dp)
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
                    fontSize = 16.sp,
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
    colors: CustomThemeColors,
    onPickFile: () -> Unit = {},
    onExportZip: () -> Unit = {}
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
            .width(180.dp)
            .background(colors.sidebarPaneBg)
            .border(
                width = 1.dp,
                brush = SolidColor(colors.borderBorder),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(vertical = 4.dp)
    ) {
        // Header Name tag of Pane
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = viewModel.activeSidebarTab.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary,
                letterSpacing = 0.5.sp
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
                                fontSize = 11.sp,
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
                                        modifier = Modifier.size(20.dp)
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
                                        modifier = Modifier.size(20.dp)
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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Workspaces,
                                        contentDescription = "Project",
                                        tint = colors.accentNeon,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = viewModel.activeProject?.name ?: "No project selected",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown list indicator",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(20.dp)
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
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                                contentDescription = "Folder ${folder.name}",
                                                tint = colors.accentNeon,
                                                modifier = Modifier.size(20.dp)
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
                                                modifier = Modifier.size(20.dp)
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
                                                    .padding(start = 14.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelectedTab) colors.sidebarActiveBg else Color.Transparent)
                                                    .combinedClickable(
                                                        onClick = { viewModel.selectTab(child) },
                                                        onLongClick = { viewModel.deleteFileFromWorkspace(child) }
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = getIconForFilename(child.name),
                                                        contentDescription = child.name,
                                                        tint = getIconColorForFilename(child.name, colors),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = child.name,
                                                        fontSize = 11.sp,
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
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = file.name,
                                            fontSize = 11.sp,
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

                        // Custom Import & Export Vault Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.sidebarBg.copy(alpha = 0.3f))
                                .border(1.dp, colors.borderBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Import Files",
                                tint = colors.accentNeon,
                                modifier = Modifier.size(24.dp)
                            )
                            
                            Text(
                                text = "File Import Studio",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            
                            Text(
                                text = "Pick files from active device directories",
                                fontSize = 9.sp,
                                color = colors.textMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 12.sp
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { onPickFile() },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeon),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FileOpen, null, tint = colors.accentOn, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pick File", fontSize = 9.sp, color = colors.accentOn, fontWeight = FontWeight.Bold)
                                }
                                
                                Button(
                                    onClick = { 
                                        viewModel.exportProjectAsZip()
                                        onExportZip()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.sidebarActiveBg),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FolderZip, null, tint = colors.textPrimary, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ZIP All", fontSize = 9.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                "Search" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        BasicTextField(
                            value = viewModel.globalSearchQuery,
                            onValueChange = { viewModel.globalSearchQuery = it },
                            textStyle = TextStyle(fontSize = 12.sp, color = colors.textPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.sidebarBg, RoundedCornerShape(4.dp))
                                .border(1.dp, colors.borderBorder, RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            cursorBrush = SolidColor(colors.accentNeon),
                            decorationBox = { innerTextField ->
                                if (viewModel.globalSearchQuery.isEmpty()) {
                                    Text("Search in workspace...", fontSize = 11.sp, color = colors.textMuted)
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("RESULTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                        
                        val searchResults = if (viewModel.globalSearchQuery.length > 1) {
                            viewModel.projectFiles.value.filter { 
                                it.content.contains(viewModel.globalSearchQuery, ignoreCase = true) 
                            }
                        } else emptyList()

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(searchResults) { file ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectTab(file) }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(file.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accentNeon)
                                    val lines = file.content.lines()
                                    val matchLine = lines.find { it.contains(viewModel.globalSearchQuery, ignoreCase = true) }?.trim() ?: ""
                                    Text(matchLine, fontSize = 10.sp, color = colors.textSecondary, maxLines = 1)
                                    Text(file.path, fontSize = 9.sp, color = colors.textMuted)
                                }
                            }
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
                                Icon(Icons.Default.Share, null, tint = colors.accentNeon, modifier = Modifier.size(36.dp))
                                Text(viewModel.activeBranch, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            }
                            IconButton(onClick = { /* Refresh */ }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Refresh, null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Changes lists
                        Text("Changes (${viewModel.gitChanges.value.size})", fontSize = 18.sp, color = colors.textMuted, fontWeight = FontWeight.Bold)
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
                                        Text(file.name, fontSize = 16.sp, color = colors.textPrimary)
                                        Text("M", fontSize = 18.sp, color = Color(0xFFFACC15), fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { viewModel.stageFile(file) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Add, null, tint = colors.accentNeon, modifier = Modifier.size(36.dp))
                                    }
                                }
                            }
                        }

                        // Staged zone
                        Text("Staged Changes (${viewModel.stagedChanges.value.size})", fontSize = 18.sp, color = colors.textMuted, fontWeight = FontWeight.Bold)
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
                                        Text(file.name, fontSize = 16.sp, color = colors.textPrimary)
                                        Text("A", fontSize = 18.sp, color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { viewModel.unstageFile(file) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Remove, null, tint = colors.accentBadge, modifier = Modifier.size(36.dp))
                                    }
                                }
                            }
                        }

                        // Commit input
                        OutlinedTextField(
                            value = viewModel.gitCommitMessage,
                            onValueChange = { viewModel.gitCommitMessage = it },
                            placeholder = { Text("Update UI and layouts", fontSize = 16.sp, color = colors.textMuted) },
                            textStyle = TextStyle(fontSize = 16.sp),
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
                            Text("Commit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                                Text("Marketplace", fontSize = 18.sp, color = if (isMarketplaceTab) colors.accentNeon else colors.textMuted, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isMarketplaceTab = false }
                                    .background(if (!isMarketplaceTab) colors.accentNeon.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Installed", fontSize = 18.sp, color = if (!isMarketplaceTab) colors.accentNeon else colors.textMuted, fontWeight = FontWeight.Bold)
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
                            fontSize = 18.sp,
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
                                            fontSize = 18.sp,
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
                                                fontSize = 16.sp,
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
                                        Text("AI is writing code...", fontSize = 18.sp, color = colors.textMuted)
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
                                textStyle = TextStyle(fontSize = 16.sp, color = colors.textPrimary),
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
                                    modifier = Modifier.size(36.dp)
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
                                Text("Auto Save", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Write files directly to workspace", fontSize = 10.sp, color = colors.textMuted)
                            }
                            Switch(
                                checked = viewModel.isAutoSaveEnabled,
                                onCheckedChange = { viewModel.isAutoSaveEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = colors.accentNeon)
                            )
                        }

                        HorizontalDivider(color = colors.borderBorder)

                        // Font size
                        Text("FONT SIZE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Size: ${viewModel.editorFontSize}sp", fontSize = 11.sp, color = colors.textPrimary)
                            Slider(
                                value = viewModel.editorFontSize.toFloat(),
                                onValueChange = { viewModel.editorFontSize = it.toInt() },
                                valueRange = 8f..28f,
                                steps = 20,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accentNeon,
                                    activeTrackColor = colors.accentNeon
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = colors.borderBorder)

                        // Themes selection picker
                        Text("THEME SELECTOR", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
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
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.accentNeon else colors.textPrimary
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = colors.accentNeon,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = colors.borderBorder)

                        // Word Wrapping
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Word Wrapping", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Wrap long lines inside editor borders", fontSize = 10.sp, color = colors.textMuted)
                            }
                            Switch(
                                checked = viewModel.isWordWrapEnabled,
                                onCheckedChange = { viewModel.isWordWrapEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = colors.accentNeon)
                            )
                        }

                        HorizontalDivider(color = colors.borderBorder)

                        // Line Numbers toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Line Numbers", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Show source line count indicator", fontSize = 10.sp, color = colors.textMuted)
                            }
                            Switch(
                                checked = viewModel.isLineNumbersVisible,
                                onCheckedChange = { viewModel.isLineNumbersVisible = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = colors.accentNeon)
                            )
                        }

                        HorizontalDivider(color = colors.borderBorder)

                        // Action utilities
                        Button(
                            onClick = { 
                                viewModel.exportProjectAsZip()
                                onExportZip()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeon),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export ZIP", tint = colors.accentOn)
                            Spacer(Modifier.width(8.dp))
                            Text("Export Project as ZIP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.accentOn)
                        }

                        Button(
                            onClick = { viewModel.deleteActiveProject() },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentBadge),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete current Project", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                        fontSize = 18.sp,
                        color = colors.textMuted
                    )
                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        placeholder = { Text("e.g. index.html or script.js", fontSize = 16.sp) },
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
            .height(30.dp)
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
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = getIconForFilename(file.name),
                        contentDescription = file.name,
                        tint = getIconColorForFilename(file.name, colors),
                        modifier = Modifier.size(14.dp)
                    )
                    
                    Text(
                        text = file.name,
                        fontSize = 10.sp,
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
                    fontSize = 18.sp,
                    color = colors.textMuted,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Tool Actions (Format & Save)
        if (tabs.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { viewModel.formatCode() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.FormatAlignLeft,
                        contentDescription = "Format Code",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
            .width(28.dp)
            .background(colors.sidebarBg.copy(alpha = 0.5f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (i in 1..lineCount) {
            Text(
                text = i.toString(),
                fontSize = (viewModel.editorFontSize * 0.8).sp,
                fontFamily = FontFamily.Monospace,
                color = colors.textMuted.copy(alpha = 0.6f),
                modifier = Modifier.height((viewModel.editorFontSize * 1.4).dp)
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
                onClick = {
                    viewModel.isSplitScreen = false
                    viewModel.isPreviewFullScreen = false
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
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
                    Icon(Icons.Default.Lock, null, tint = colors.textMuted, modifier = Modifier.size(10.dp))
                    Text(viewModel.livePreviewUrl, fontSize = 12.sp, color = colors.textPrimary)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (viewModel.isPreviewWebLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.accentNeon, strokeWidth = 1.6.dp)
                    } else {
                        Icon(Icons.Default.Refresh, null, tint = colors.accentNeon, modifier = Modifier.size(16.dp).clickable {
                            viewModel.recompileHtmlPreview()
                        })
                    }
                    
                    Icon(
                        imageVector = if (viewModel.isPreviewFullScreen) Icons.Default.CloseFullscreen else Icons.Default.Fullscreen,
                        contentDescription = "Toggle Fullscreen",
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp).clickable {
                            viewModel.isPreviewFullScreen = !viewModel.isPreviewFullScreen
                        }
                    )
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
                        
                        // Bridge to catch error messages
                        addJavascriptInterface(object {
                            @android.webkit.JavascriptInterface
                            fun postMessage(json: String) {
                                // This is for older android or custom handling, 
                                // but we injected postMessage to window.parent
                            }
                        }, "Android")

                        // Note: To catch postMessage from iframe/webview, we might need a custom interface
                        // or evaluate javascript regularly. 
                        // For this IDE, let's use a standard console logger.
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    if (it.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                                        viewModel.problems.value = viewModel.problems.value + it.message()
                                    }
                                }
                                return true
                            }
                        }

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
            .height(140.dp)
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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) colors.accentNeon else colors.textMuted,
                        modifier = Modifier
                            .clickable { 
                                viewModel.activeConsoleTab = t
                                if (t == "TERMINAL" && !viewModel.terminalLogs.value.any { it.contains("Ubuntu 22.04 LTS booted") }) {
                                    viewModel.executeTerminalCommand("ubuntu")
                                }
                            }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = { viewModel.isTerminalVisible = false },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
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
                                    fontSize = 10.sp,
                                    color = if (line.startsWith("$") || line.contains("npm")) colors.accentNeon else colors.editorText
                                )
                            )
                        }
                    }

                    // Input CLI basic terminal row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$ ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                        BasicTextField(
                            value = viewModel.terminalInput,
                            onValueChange = { viewModel.terminalInput = it },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
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
                        fontSize = 18.sp,
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
            .height(28.dp)
            .background(colors.sidebarBg)
            .padding(horizontal = 8.dp)
            .clickable { viewModel.isTerminalVisible = !viewModel.isTerminalVisible },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LHS Statuses
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Server status
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { viewModel.toggleLiveServer() }
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (viewModel.isLiveServerOn) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                )
                Text(
                    text = "Live Server: " + (if (viewModel.isLiveServerOn) "Port 3000 (On)" else "Off"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (viewModel.isLiveServerOn) Color(0xFF10B981) else colors.textSecondary
                )
            }
            
            // Errors counter
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error, 
                    contentDescription = "Problems", 
                    tint = if (viewModel.problems.value.isNotEmpty()) Color(0xFFEF4444) else colors.textMuted, 
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "Errors: ${viewModel.problems.value.size}", 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Bold,
                    color = if (viewModel.problems.value.isNotEmpty()) Color(0xFFEF4444) else colors.textSecondary
                )
            }
        }
        
        // RHS Statuses
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pos: " + viewModel.editorCursorPosition, 
                fontSize = 10.sp, 
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
            Text(
                text = "UTF-8", 
                fontSize = 10.sp, 
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
            Text(
                text = "Tab Spaces: 4", 
                fontSize = 10.sp, 
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
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
            Text(ext.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text("By ${ext.author} • ${ext.downloads} downloads", fontSize = 11.sp, color = colors.textMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(ext.description, fontSize = 10.sp, color = colors.textSecondary, maxLines = 2)
        }

        Button(
            onClick = { viewModel.toggleExtension(ext) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (ext.isInstalled) colors.sidebarActiveBg else colors.accentNeon,
                contentColor = if (ext.isInstalled) colors.textPrimary else colors.accentOn
            ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.height(24.dp)
        ) {
            Text(
                text = if (ext.isInstalled) "Uninstall" else "Install",
                fontSize = 11.sp,
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
                        Text(lang.uppercase(), fontSize = 12.sp, color = colors.accentNeon, fontWeight = FontWeight.Bold)
                        Text("Copy", fontSize = 12.sp, color = colors.textMuted, modifier = Modifier.clickable {
                            // Copy to clipboard simulations
                        })
                    }
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                if (content.isNotEmpty()) {
                    Text(content, fontSize = 16.sp, color = colors.textPrimary)
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

            Text("Try speaking standard keywords: \n • \"create file\" \n • \"run code\" \n • \"open preview\" \n • \"save project\"", fontSize = 18.sp, color = colors.textMuted)

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = userVoiceInput,
                onValueChange = { userVoiceInput = it },
                label = { Text("Simulate spoken word") },
                colors = getVSOtdColors(colors),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Auto triggering voice action in ${timerVal}s...", fontSize = 18.sp, color = colors.accentBadge, fontWeight = FontWeight.Bold)
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
                        fontSize = 16.sp,
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
                    fontSize = 18.sp,
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
                            modifier = Modifier.size(36.dp),
                            tint = colors.accentOn
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Toggle Sidebar",
                            fontSize = 18.sp,
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
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = viewModel.voiceFeedback,
                                fontSize = 18.sp,
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
        background = Color(0xFF0F111A),          // Premium deep cyber slate background
        sidebarBg = Color(0xFF08090F),           // Low contrast LHS rail
        sidebarPaneBg = Color(0xFF121421),       // Deep space explorer drawer
        sidebarActiveBg = Color(0xFF1E2136),     // Highlight element states
        editorBg = Color(0xFF131625),            // Soft coding surface to reduce optical fatigue
        borderBorder = Color(0xFF1B1E32),        // Seamless deep spacer lines
        textPrimary = Color(0xFFF1F5F9),         // Eye-safe soft slate white text
        textSecondary = Color(0xFF94A3B8),       // Description gray text
        textMuted = Color(0xFF64748B),           // Clean comment style placeholder
        editorText = Color(0xFFE2E8F0),          // High readability text for syntax
        accentNeon = Color(0xFFA855F7),          // Glowing neon lavender purple
        accentOn = Color.White,
        accentBadge = Color(0xFFEC4899)          // Electric pink alert/status highlights
    )

    val AmoledTheme = CustomThemeColors(
        background = Color(0xFF000000),          // Absolute battery saver black
        sidebarBg = Color(0xFF000000),           // Dark rail backdrop
        sidebarPaneBg = Color(0xFF070709),       // Extremely subtle charcoal list background
        sidebarActiveBg = Color(0xFF15151F),     // Selected accent indicator backdrop
        editorBg = Color(0xFF020203),            // True ink black coding pad
        borderBorder = Color(0xFF14141A),        // Dark border highlights
        textPrimary = Color(0xFFFFFFFF),         // Clean pure white keys
        textSecondary = Color(0xFF94A3B8),       // Soft description silver text
        textMuted = Color(0xFF64748B),           // Neutral label colors
        editorText = Color(0xFFF1F5F9),          // Contrast editor text
        accentNeon = Color(0xFF00F0FF),          // Ultra fluorescent electric cyan highlight
        accentOn = Color.Black,
        accentBadge = Color(0xFF00A2FF)          // Sky high contrast light cyan
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

fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
