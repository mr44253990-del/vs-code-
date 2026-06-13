package com.example

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = ProjectRepository(db.projectDao(), db.chatDao(), db.extensionDao())

    // UI Configuration States
    var selectedTheme by mutableStateOf("Dark") // "Dark", "Light", "AMOLED"
    var activeSidebarTab by mutableStateOf("Explorer") // Explorer, Search, Git, Extensions, AI, Settings
    var isSidebarExpanded by mutableStateOf(true)
    var isSplitScreen by mutableStateOf(false)
    var isTerminalVisible by mutableStateOf(true)
    var activeConsoleTab by mutableStateOf("TERMINAL") // PROBLEMS, OUTPUT, DEBUG CONSOLE, TERMINAL

    // Project & Workspace Storage
    val allProjects: StateFlow<List<Project>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var activeProject by mutableStateOf<Project?>(null)
    private val _projectFiles = MutableStateFlow<List<ProjectFile>>(emptyList())
    val projectFiles: StateFlow<List<ProjectFile>> = _projectFiles.asStateFlow()

    // Editor States
    var openTabs = mutableStateOf<List<ProjectFile>>(emptyList())
    var activeFile by mutableStateOf<ProjectFile?>(null)
    var editorText by mutableStateOf("")
    var searchReplaceQuery by mutableStateOf("")
    var replaceWithQuery by mutableStateOf("")
    var isSearchEnabled by mutableStateOf(false)

    // Git integration states
    var activeBranch by mutableStateOf("main")
    var gitChanges = mutableStateOf<List<ProjectFile>>(emptyList())
    var stagedChanges = mutableStateOf<List<ProjectFile>>(emptyList())
    var gitCommitMessage by mutableStateOf("")
    var gitHistory = mutableStateOf<List<String>>(listOf("Initial Commit", "Created project setup"))

    // Extensions List
    val extensions: StateFlow<List<ExtensionItem>> = repository.allExtensions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat AI bot states
    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var isAiGenerating by mutableStateOf(false)
    var aiChatInput by mutableStateOf("")

    // WebView Live Preview Control
    var livePreviewUrl by mutableStateOf("http://localhost:3000")
    var isPreviewWebLoading by mutableStateOf(false)
    var htmlCompiledContent by mutableStateOf("") // Secure sandbox compilation

    // Terminal Commands logs
    var terminalInput by mutableStateOf("")
    var terminalLogs = mutableStateOf<List<String>>(listOf(
        "RakibCodeStudio ~ /project",
        "Type 'help' for interactive guides or 'npm run dev' to spin server."
    ))

    // Voice Interaction Support
    var isVoiceActive by mutableStateOf(false)
    var lastVoiceCommand by mutableStateOf("")
    var voiceFeedback by mutableStateOf("")

    // Settings
    var isAutoSaveEnabled by mutableStateOf(true)
    var isLiveServerOn by mutableStateOf(false)

    init {
        viewModelScope.launch {
            repository.populateExtensionsIfEmpty()
            // Observe project files change to update Git changes automatically
            repository.allProjects.collectLatest { list ->
                if (list.isNotEmpty() && activeProject == null) {
                    loadProject(list.first())
                } else if (list.isEmpty()) {
                    val defaultId = repository.createProject("My Project")
                    val freshList = repository.allProjects.first()
                    freshList.firstOrNull { it.id == defaultId }?.let { loadProject(it) }
                }
            }
        }
    }

    fun loadProject(project: Project) {
        activeProject = project
        viewModelScope.launch {
            repository.getFilesForProject(project.id).collectLatest { files ->
                _projectFiles.value = files
                
                // Set default open files
                if (openTabs.value.isEmpty() && files.isNotEmpty()) {
                    val defaultFiles = files.filter { !it.isFolder && (it.name == "index.html" || it.name == "style.css" || it.name == "script.js") }
                    openTabs.value = defaultFiles
                    if (defaultFiles.isNotEmpty()) {
                        selectTab(defaultFiles.first())
                    }
                } else {
                    // Update current file values in tabs
                    openTabs.value = openTabs.value.map { tab ->
                        files.find { it.id == tab.id } ?: tab
                    }
                    activeFile?.let { af ->
                        files.find { it.id == af.id }?.let { updated ->
                            if (updated.content != editorText && !isAutoSaveEnabled) {
                                // Keep unsaved changes or apply
                            } else {
                                if (updated.id != activeFile?.id) {
                                    activeFile = updated
                                    editorText = updated.content
                                }
                            }
                        }
                    }
                }
                
                // Track modified Git changes
                val originalFiles = files.filter { !it.isFolder }
                gitChanges.value = originalFiles.filter { f ->
                    // A simple git modifications delta detector
                    f.name == "index.html" && f.content.contains("Modified in Code Studio") || 
                    f.name == "style.css" && f.content.contains("/* custom body background color */") ||
                    f.content.trim() != getOriginalPresetContent(f.name).trim() && !stagedChanges.value.any { it.id == f.id }
                }
            }
        }
    }

    private fun getOriginalPresetContent(name: String): String {
        return when (name) {
            "index.html" -> """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Rakib Code Studio</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <h1>Hello, Developer! 👋</h1>
        <p>Build. Preview. Deploy.</p>
        <button id="btn">Click Me</button>
        <div id="status">Clicked 0 times</div>
    </div>
    <script src="js/script.js"></script>
</body>
</html>"""
            "package.json" -> """{
  "name": "rakib-code-studio-project",
  "version": "1.0.0",
  "description": "A web design template created in Rakib Code Studio",
  "main": "index.html",
  "scripts": {
    "start": "serve .",
    "dev": "npm run start"
  },
  "dependencies": {},
  "devDependencies": {}
}"""
            "README.md" -> """# Rakib Code Studio IDE 🚀

Welcome to your visual cloud-integrated project workspace! This space functions exactly like lightweight desktop developer workflow.

## Included Features

1. **Syntax Highlighting**: HTML, CSS, JavaScript, Markdown, JSON, and more.
2. **Terminal Emulator**: Built-in shell context execution simulated environment.
3. **Extensions Panel**: Install and configure live development plugins.
4. **Interactive Local Server & Live Preview**: Split browser visualization.
5. **Smart AI Assistant**: Ask questions, refactor codes, and auto-complete logic directly.

*Developed specifically with Material Design 3 and Flutter/Compose visuals.*"""
            else -> ""
        }
    }

    fun selectTab(file: ProjectFile) {
        if (!openTabs.value.any { it.id == file.id }) {
            openTabs.value = openTabs.value + file
        }
        activeFile = file
        editorText = file.content
        recompileHtmlPreview()
    }

    fun closeTab(file: ProjectFile) {
        val tabs = openTabs.value.filter { it.id != file.id }
        openTabs.value = tabs
        if (activeFile?.id == file.id) {
            if (tabs.isNotEmpty()) {
                selectTab(tabs.last())
            } else {
                activeFile = null
                editorText = ""
            }
        }
    }

    fun onEditorTextChange(newText: String) {
        editorText = newText
        val af = activeFile ?: return
        
        if (isAutoSaveEnabled) {
            viewModelScope.launch {
                val updated = af.copy(content = newText)
                repository.saveFile(updated)
                recompileHtmlPreview()
            }
        }
    }

    fun manualSave() {
        val af = activeFile ?: return
        viewModelScope.launch {
            val updated = af.copy(content = editorText)
            repository.saveFile(updated)
            recompileHtmlPreview()
            appendTerminalLog("Auto-saved ${af.name} successfully.")
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            val id = repository.createProject(name)
            val list = repository.allProjects.first()
            list.find { it.id == id }?.let { loadProject(it) }
            appendTerminalLog("Created workspace project '$name' with baseline templates.")
        }
    }

    fun deleteActiveProject() {
        val proj = activeProject ?: return
        viewModelScope.launch {
            repository.deleteProject(proj.id)
            activeProject = null
            openTabs.value = emptyList()
            activeFile = null
            editorText = ""
            val list = repository.allProjects.first()
            if (list.isNotEmpty()) {
                loadProject(list.first())
            }
        }
    }

    fun createNewFileInWorkspace(filename: String, isFolder: Boolean, parentDir: String = "") {
        val proj = activeProject ?: return
        viewModelScope.launch {
            val fullPath = if (parentDir.isEmpty()) filename else "$parentDir/$filename"
            repository.createNewFile(proj.id, fullPath, isFolder, parentDir)
            appendTerminalLog("Successfully created ${if (isFolder) "folder" else "file"} '$fullPath' in workspace explorer.")
        }
    }

    fun deleteFileFromWorkspace(file: ProjectFile) {
        viewModelScope.launch {
            closeTab(file)
            repository.deleteFile(file)
            appendTerminalLog("Deleted file '${file.path}' from workspace.")
        }
    }

    // Git Commands
    fun stageFile(file: ProjectFile) {
        if (!stagedChanges.value.any { it.id == file.id }) {
            stagedChanges.value = stagedChanges.value + file
            gitChanges.value = gitChanges.value.filter { it.id != file.id }
        }
    }

    fun unstageFile(file: ProjectFile) {
        if (stagedChanges.value.any { it.id == file.id }) {
            stagedChanges.value = stagedChanges.value.filter { it.id != file.id }
            gitChanges.value = gitChanges.value + file
        }
    }

    fun commitGitChanges() {
        if (gitCommitMessage.isBlank()) return
        val count = stagedChanges.value.size
        viewModelScope.launch {
            gitHistory.value = listOf("Commit: $gitCommitMessage - staged $count files updated") + gitHistory.value
            stagedChanges.value = emptyList()
            gitCommitMessage = ""
            appendTerminalLog("pushed changes on main branch matching remote origin. Git clean successfully.")
        }
    }

    // Extensions Toggles
    fun toggleExtension(ext: ExtensionItem) {
        viewModelScope.launch {
            repository.toggleExtensionInstalled(ext.id, !ext.isInstalled)
            delay(300)
            appendTerminalLog("Plugin ${ext.name} installed state: ${!ext.isInstalled}")
        }
    }

    // Local sandbox code preview engine compilation
    fun recompileHtmlPreview() {
        val files = _projectFiles.value
        val indexHtml = files.find { it.name == "index.html" && !it.isFolder } ?: return
        
        // Inline CSS and script JS for safe sandboxed WebView running
        var compiled = indexHtml.content

        // Resolve css/style.css
        val cssFile = files.find { it.path == "css/style.css" || it.name == "style.css" }
        if (cssFile != null) {
            compiled = compiled.replace(
                "<link rel=\"stylesheet\" href=\"css/style.css\">",
                "<style>${cssFile.content}</style>"
            ).replace(
                "<link rel=\"stylesheet\" href=\"style.css\">",
                "<style>${cssFile.content}</style>"
            )
        }

        // Resolve js/script.js
        val jsFile = files.find { it.path == "js/script.js" || it.name == "script.js" }
        if (jsFile != null) {
            compiled = compiled.replace(
                "<script src=\"js/script.js\"></script>",
                "<script>${jsFile.content}</script>"
            ).replace(
                "<script src=\"script.js\"></script>",
                "<script>${jsFile.content}</script>"
            )
        }

        htmlCompiledContent = compiled
    }

    // Terminal virtual commands executor
    fun executeTerminalCommand(cmd: String) {
        val raw = cmd.trim()
        if (raw.isEmpty()) return

        terminalLogs.value = terminalLogs.value + "$ RakibCodeStudio ~ /project -> $raw"
        
        when (raw.lowercase()) {
            "help" -> {
                appendTerminalLog("Available simulated CLI tools:")
                appendTerminalLog("  npm install        - Install simulated dependencies")
                appendTerminalLog("  npm run dev        - Spin local build live reload server")
                appendTerminalLog("  git status         - Print git branch configurations")
                appendTerminalLog("  git add .          - Stage all dirty code modifications")
                appendTerminalLog("  clear              - Wipe terminal logs")
                appendTerminalLog("  ai \"[prompt]\"     - Query the integrated chat engine via CLI")
            }
            "npm install" -> {
                viewModelScope.launch {
                    appendTerminalLog("Installing node packages from package.json...")
                    delay(800)
                    appendTerminalLog("added 120 packages in 5.2s successfully.")
                }
            }
            "npm run dev", "run", "run code" -> {
                viewModelScope.launch {
                    isLiveServerOn = true
                    appendTerminalLog("Launching local node environment...")
                    delay(600)
                    livePreviewUrl = "http://localhost:3000"
                    appendTerminalLog("Live Server successfully spun up!")
                    appendTerminalLog("Server running at: $livePreviewUrl")
                    recompileHtmlPreview()
                }
            }
            "git status" -> {
                appendTerminalLog("On branch $activeBranch")
                if (gitChanges.value.isEmpty() && stagedChanges.value.isEmpty()) {
                    appendTerminalLog("nothing to commit, working tree clean")
                } else {
                    appendTerminalLog("Changes not staged for commit:")
                    gitChanges.value.forEach { appendTerminalLog("   modified:   ${it.path}") }
                    appendTerminalLog("Changes staged for commit:")
                    stagedChanges.value.forEach { appendTerminalLog("   ready:      ${it.path}") }
                }
            }
            "git add ." -> {
                gitChanges.value.forEach { stageFile(it) }
                appendTerminalLog("staged ${gitChanges.value.size} items for remote checkin.")
            }
            "clear" -> {
                terminalLogs.value = listOf("RakibCodeStudio Terminal initialized.")
            }
            else -> {
                if (raw.startsWith("ai ")) {
                    val prompt = raw.removePrefix("ai ")
                    queryAiAssistant(prompt)
                } else {
                    appendTerminalLog("bash: command not found: $raw. Type 'help' for instructions.")
                }
            }
        }
        terminalInput = ""
    }

    private fun appendTerminalLog(text: String) {
        terminalLogs.value = terminalLogs.value + text
    }

    // AI Assistant Code Handler using direct REST configuration
    fun queryAiAssistant(prompt: String) {
        if (prompt.trim().isEmpty()) return
        
        aiChatInput = ""
        viewModelScope.launch {
            repository.insertChatMessage("user", prompt)
            isAiGenerating = true

            // Build request with system instructions
            try {
                // Incorporate editor active file code to support real debugging
                val sysPrompt = "You are a senior full-stack AI coding assistant inside Rakib Code Studio IDE. Answer professional developer-focused syntax prompts precisely. Output high-fidelity code tags where applicable."
                val reqBody = if (activeFile != null) {
                    "User is targeting file: ${activeFile?.path}\n" +
                    "Active Editor File content:\n```\n$editorText\n```\n\nQuery:\n$prompt"
                } else {
                    prompt
                }

                val finalReq = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = reqBody)))),
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = sysPrompt)))
                )

                val apiKey = BuildConfig.GEMINI_API_KEY
                
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    // Pre-coded smart matching for fully functional offline backup responses!
                    delay(1200)
                    val mockResponse = getMockAiResponse(prompt)
                    repository.insertChatMessage("ai", mockResponse)
                } else {
                    val apiResponse = withContext(Dispatchers.IO) {
                        GeminiClient.api.generateContent(apiKey, finalReq)
                    }
                    val textRes = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                        ?: "Sorry, I received an empty response. Please verify code syntax or retry."
                    repository.insertChatMessage("ai", textRes)
                }
            } catch (e: Exception) {
                Log.e("RakibCodeStudio", "Gemini API query error: ", e)
                delay(1000)
                // Fallback offline responses to ensure seamless UI experience
                val mockRes = getMockAiResponse(prompt) + "\n\n*(Loaded using offline IDE assistant engine)*"
                repository.insertChatMessage("ai", mockRes)
            } finally {
                isAiGenerating = false
            }
        }
    }

    private fun getMockAiResponse(prompt: String): String {
        val q = prompt.lowercase()
        return when {
            q.contains("navbar") -> """Here is a responsive modern navbar styled in dark cosmic vibes for your CSS and HTML:

```html
<nav class="navbar">
    <div class="logo">Studio</div>
    <ul class="nav-links">
        <li><a href="#home">Home</a></li>
        <li><a href="#editor">Editor</a></li>
        <li><a href="#services">Services</a></li>
    </ul>
</nav>
```

```css
.navbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #090d16;
    padding: 15px 30px;
    border-bottom: 2px solid #a855f7;
}
.logo {
    font-weight: bold;
    color: #a855f7;
}
.nav-links {
    list-style: none;
    display: flex;
    gap: 20px;
}
.nav-links a {
    color: #94a3b8;
    text-decoration: none;
}
```"""
            q.contains("animation") || q.contains("neon") -> """Here is an ultra-modern glowing neon button animation styled in CSS:

```css
@keyframes pulseGlow {
    0% { box-shadow: 0 0 5px #3b82f6; }
    50% { box-shadow: 0 0 20px #a855f7; }
    100% { box-shadow: 0 0 5px #3b82f6; }
}

.neon-btn {
    background: #090d16;
    border: 2px solid #a855f7;
    color: white;
    cursor: pointer;
    font-family: inherit;
    animation: pulseGlow 2s infinite;
}
```"""
            q.contains("help") || q.contains("features") -> """How can I assist today? Here are some tasks I can help you with inside Rakib Code Studio:
- **Write code**: Ask me to write a responsive navbar, grids, or custom scripts.
- **Refactor code**: I can check your actively-opened files for bugs or cleanups.
- **Explain tools**: Ask about terminal features or local browser hosting modules.
- **Git help**: I can write git guides for branching or checking staging zones."""
            else -> """I can help you build and refine your coding project! Here is a clean boilerplate helper structure for your work:

```javascript
// Smart auto code suggestion
function sumNumbers(a, b) {
    return a + b;
}
console.log(sumNumbers(12, 13));
```

Would you like me to insert this sequence directly or refactor some existing codes inside your active file?"""
        }
    }

    // Voice coding action executor
    fun executeVoiceCodingCommand(phrase: String) {
        val normalized = phrase.lowercase().trim()
        lastVoiceCommand = phrase
        isVoiceActive = false

        when {
            normalized.contains("create file") || normalized.contains("make file") -> {
                val fName = "index_new.html"
                createNewFileInWorkspace(fName, false)
                voiceFeedback = "Voice Command Success: Created file '$fName'"
            }
            normalized.contains("run code") || normalized.contains("start server") -> {
                executeTerminalCommand("npm run dev")
                voiceFeedback = "Voice Command Success: Spun up local server."
            }
            normalized.contains("open preview") || normalized.contains("show preview") -> {
                isSplitScreen = true
                voiceFeedback = "Voice Command Success: Toggled side preview pane."
            }
            normalized.contains("save project") || normalized.contains("save file") -> {
                manualSave()
                voiceFeedback = "Voice Command Success: File changes committed safely."
            }
            else -> {
                voiceFeedback = "Voice recognized '$phrase', but matched no system code. Try saying: 'run code' or 'open preview'."
            }
        }
        viewModelScope.launch {
            appendTerminalLog("Voice command executed: \"$phrase\"")
            delay(5000)
            voiceFeedback = "" // Clear after timeout
        }
    }
}
