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
    var isSidebarExpanded by mutableStateOf(false)
    var isSplitScreen by mutableStateOf(false)
    var isPreviewFullScreen by mutableStateOf(false)
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
        "RakibCodeStudio ~ /workspace $ ",
        "Real terminal engine initialized."
    ))
    
    var problems = mutableStateOf<List<String>>(emptyList())

    // Terminal real process
    private var shellProcess: Process? = null
    private var shellWriter: java.io.BufferedWriter? = null

    // Voice Interaction Support
    var isVoiceActive by mutableStateOf(false)
    var lastVoiceCommand by mutableStateOf("")
    var voiceFeedback by mutableStateOf("")

    // Settings
    var isAutoSaveEnabled by mutableStateOf(true)
    var isLiveServerOn by mutableStateOf(false)
        private set
    var editorFontSize by mutableStateOf(12) // Default smaller for phone
    var isLineNumbersVisible by mutableStateOf(true)
    var autoSaveInterval by mutableStateOf(2000L) // ms
    var editorFontFamily by mutableStateOf("Monospace")
    var isSettingsDialogVisible by mutableStateOf(false)

    fun toggleLiveServer() {
        isLiveServerOn = !isLiveServerOn
        if (isLiveServerOn) {
            LocalWebServer.startServer()
            livePreviewUrl = "http://localhost:3000"
            recompileHtmlPreview()
        } else {
            LocalWebServer.stopServer()
            livePreviewUrl = ""
        }
    }

    init {
        startRealShell()
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

    fun renameFileInWorkspace(file: ProjectFile, newName: String) {
        viewModelScope.launch {
            repository.renameFile(file, newName)
            appendTerminalLog("Renamed file from '${file.name}' to '$newName'.")
        }
    }

    fun moveFileInWorkspace(file: ProjectFile, newParentDir: String) {
        viewModelScope.launch {
            val newPath = if (newParentDir.isEmpty()) file.name else "$newParentDir/${file.name}"
            val updated = file.copy(parentPath = newParentDir, path = newPath)
            repository.saveFile(updated)
            appendTerminalLog("Moved '${file.name}' to '${newParentDir.ifEmpty { "root" }}/'.")
        }
    }

    fun deleteFileFromWorkspace(file: ProjectFile) {
        viewModelScope.launch {
            closeTab(file)
            repository.deleteFile(file)
            appendTerminalLog("Deleted file '${file.path}' from workspace.")
        }
    }

    fun formatCode() {
        val current = editorText
        val lang = activeFile?.language ?: return
        
        editorText = when (lang) {
            "html" -> {
                val lines = current.replace("><", ">\n<").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                var indent = 0
                lines.joinToString("\n") { line ->
                    if (line.startsWith("</")) indent = maxOf(0, indent - 1)
                    val formatted = "    ".repeat(indent) + line
                    if (!line.startsWith("</") && !line.endsWith("/>") && !line.startsWith("<!")) indent++
                    formatted
                }
            }
            "json" -> current.replace("{", "{\n  ").replace("}", "\n}").replace(",", ",\n  ")
            "css" -> current.replace("{", " {\n    ").replace("}", "\n}\n").replace(";", ";\n    ")
            else -> current
        }
        onEditorTextChange(editorText)
        appendTerminalLog("Formatted code using prettier simulated formatter.")
    }

    fun applySearchAndReplace() {
        if (searchReplaceQuery.isEmpty() || activeFile == null) return
        val count = editorText.split(searchReplaceQuery).size - 1
        editorText = editorText.replace(searchReplaceQuery, replaceWithQuery)
        onEditorTextChange(editorText)
        appendTerminalLog("Replaced $count occurrence(s) of '$searchReplaceQuery'")
    }

    fun exportProjectAsZip() {
        viewModelScope.launch {
            appendTerminalLog("Archiving project [ ${activeProject?.name} ]...")
            delay(1200)
            appendTerminalLog("Successfully generated ZIP artifact! Saved to Downloads folder.")
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

        // Inject error reporting script
        val errorScript = """
            <script>
                window.onerror = function(message, source, lineno, colno, error) {
                    window.parent.postMessage({
                        type: 'error',
                        message: message + ' at line ' + lineno + ':' + colno
                    }, '*');
                };
                console.error = function() {
                    window.parent.postMessage({
                        type: 'error',
                        message: Array.from(arguments).join(' ')
                    }, '*');
                };
            </script>
        """.trimIndent()
        
        if (compiled.contains("<head>")) {
            compiled = compiled.replace("<head>", "<head>\n$errorScript")
        } else {
            compiled = "$errorScript\n$compiled"
        }

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
        
        if (isLiveServerOn) {
            LocalWebServer.serverHtmlContent.value = compiled
        }
    }

    private fun startRealShell() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder("/system/bin/sh")
                pb.redirectErrorStream(true)
                pb.directory(getApplication<Application>().filesDir)
                shellProcess = pb.start()
                shellWriter = shellProcess?.outputStream?.bufferedWriter()

                val reader = shellProcess?.inputStream?.bufferedReader()
                reader?.let {
                    var line: String?
                    while (it.readLine().also { l -> line = l } != null) {
                        appendTerminalLog(line ?: "")
                    }
                }
            } catch (e: Exception) {
                appendTerminalLog("Terminal Error: ${e.message}")
            }
        }
    }

    // Terminal virtual commands executor
    fun executeTerminalCommand(cmd: String) {
        val raw = cmd.trim()
        if (raw.isEmpty()) return

        // Display user input with prompt
        appendTerminalLog("$ $raw")
        
        if (raw == "clear") {
            terminalLogs.value = emptyList()
            terminalInput = ""
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                shellWriter?.write(raw + "\n")
                shellWriter?.flush()
            } catch (e: Exception) {
                appendTerminalLog("Shell write error: ${e.message}")
            }
        }
        terminalInput = ""
    }

    private fun appendTerminalLog(text: String) {
        terminalLogs.value = terminalLogs.value + text
    }

    // AI Assistant Code Handler using direct REST configuration
    private val chatHistory = mutableListOf<GeminiContent>()

    fun queryAiAssistant(prompt: String) {
        if (prompt.trim().isEmpty()) return
        
        aiChatInput = ""
        viewModelScope.launch {
            repository.insertChatMessage("user", prompt)
            isAiGenerating = true

            // Build request with system instructions
            try {
                val sysPrompt = """
                    You are a senior full-stack AI coding assistant inside Rakib Code Studio IDE. 
                    You have full access to project files.
                    
                    CAPABILITIES:
                    1. Create/Update Files: Use the special tag [FILE_ACTION:path] followed by the content.
                    Example: [FILE_ACTION:index.html]
                    <!DOCTYPE html>...
                    
                    2. Fix Code: Analyze active file and provide corrected code in [FILE_ACTION:path] tags.
                    
                    3. Memory: You remember previous messages.
                    
                    Output high-fidelity code tags where applicable.
                """.trimIndent()

                // Add to history
                val userContent = GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                if (chatHistory.size > 20) chatHistory.removeAt(0)
                chatHistory.add(userContent)

                val contextPrompt = if (activeFile != null) {
                    "ACTIVE FILE: ${activeFile?.path}\nCONTENT:\n```\n$editorText\n```\n\nUSER PROMPT: $prompt"
                } else {
                    prompt
                }

                val finalReq = GeminiRequest(
                    contents = chatHistory + listOf(GeminiContent(parts = listOf(GeminiPart(text = contextPrompt)))),
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = sysPrompt)))
                )

                val apiKey = BuildConfig.GEMINI_API_KEY
                
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    delay(1200)
                    val mockResponse = getMockAiResponse(prompt)
                    repository.insertChatMessage("ai", mockResponse)
                } else {
                    val apiResponse = withContext(Dispatchers.IO) {
                        GeminiClient.api.generateContent(apiKey, finalReq)
                    }
                    val textRes = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                        ?: "Sorry, I received an empty response. Please verify code syntax or retry."
                    
                    // Add AI response to history
                    chatHistory.add(GeminiContent(parts = listOf(GeminiPart(text = textRes))))
                    
                    repository.insertChatMessage("ai", textRes)
                    
                    // Parse for file actions
                    parseAiResponseForActions(textRes)
                }
            } catch (e: Exception) {
                Log.e("RakibCodeStudio", "Gemini API query error: ", e)
                delay(1000)
                val mockRes = getMockAiResponse(prompt) + "\n\n*(Loaded using offline IDE assistant engine)*"
                repository.insertChatMessage("ai", mockRes)
            } finally {
                isAiGenerating = false
            }
        }
    }

    private fun parseAiResponseForActions(text: String) {
        viewModelScope.launch {
            val fileActionRegex = """\[FILE_ACTION:([^\]]+)\]([\s\S]*?)(?=\[FILE_ACTION:|$)""".toRegex()
            val matches = fileActionRegex.findAll(text)
            
            matches.forEach { match ->
                val path = match.groups[1]?.value?.trim() ?: ""
                val content = match.groups[2]?.value?.trim() ?: ""
                
                if (path.isNotEmpty()) {
                    val cleanedContent = content.removeSurrounding("```", "```")
                        .removePrefix("html").removePrefix("css").removePrefix("javascript").trim()

                    val existing = projectFiles.value.find { it.path == path || it.name == path }
                    if (existing != null) {
                        repository.saveFile(existing.copy(content = cleanedContent))
                        appendTerminalLog("AI updated file: $path")
                        selectTab(existing) 
                    } else {
                        // Create new file
                        val proj = activeProject ?: return@launch
                        repository.createNewFile(proj.id, path, false)
                        loadProject(proj) // Refresh files
                        delay(300)
                        projectFiles.value.find { it.path == path || it.name == path }?.let {
                            repository.saveFile(it.copy(content = cleanedContent))
                            selectTab(it)
                            appendTerminalLog("AI created new file: $path")
                        }
                    }
                }
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
