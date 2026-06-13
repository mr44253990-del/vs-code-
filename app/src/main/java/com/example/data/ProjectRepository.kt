package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val chatDao: ChatDao,
    private val extensionDao: ExtensionDao
) {
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()
    val chatMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()
    val allExtensions: Flow<List<ExtensionItem>> = extensionDao.getAllExtensions()

    fun getFilesForProject(projectId: Long): Flow<List<ProjectFile>> {
        return projectDao.getFilesForProject(projectId)
    }

    suspend fun createProject(name: String): Long {
        val projectId = projectDao.insertProject(Project(name = name))
        // Create baseline files for a realistic workspace match
        preloadBaselineFiles(projectId)
        return projectId
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProjectFiles(id)
        projectDao.deleteProject(id)
    }

    suspend fun saveFile(file: ProjectFile) {
        if (file.id == 0L) {
            projectDao.insertFile(file)
        } else {
            projectDao.updateFile(file)
        }
    }

    suspend fun createNewFile(projectId: Long, path: String, isFolder: Boolean, parentPath: String = ""): Long {
        val name = path.substringAfterLast('/')
        val lang = when (name.substringAfterLast('.', "").lowercase()) {
            "html" -> "html"
            "css" -> "css"
            "js" -> "javascript"
            "json" -> "json"
            "py" -> "python"
            "java" -> "java"
            "cpp" -> "cpp"
            "md" -> "markdown"
            else -> "text"
        }
        val newFile = ProjectFile(
            projectId = projectId,
            path = path,
            name = name,
            content = if (isFolder) "" else getTemplateForLanguage(lang, name),
            language = lang,
            isFolder = isFolder,
            parentPath = parentPath
        )
        return projectDao.insertFile(newFile)
    }

    suspend fun deleteFile(file: ProjectFile) {
        projectDao.deleteFile(file)
    }

    suspend fun renameFile(file: ProjectFile, newName: String) {
        val newPath = if (file.parentPath.isEmpty()) newName else "${file.parentPath}/$newName"
        val updated = file.copy(name = newName, path = newPath)
        projectDao.updateFile(updated)
    }

    suspend fun insertChatMessage(sender: String, content: String) {
        chatDao.insertMessage(ChatMessage(sender = sender, content = content))
    }

    suspend fun clearChatHistory() {
        chatDao.clearHistory()
    }

    suspend fun toggleExtensionInstalled(id: String, isInstalled: Boolean) {
        extensionDao.updateInstalledStatus(id, isInstalled)
    }

    suspend fun populateExtensionsIfEmpty() {
        val current = extensionDao.getAllExtensionsSync()
        if (current.isEmpty()) {
            val list = listOf(
                ExtensionItem(
                    id = "live-server",
                    name = "Live Server",
                    description = "Launch a development local Server with live reload feature for static & dynamic pages.",
                    author = "Ritwick Dey",
                    isInstalled = true,
                    downloads = "48.2M",
                    rating = 4.8
                ),
                ExtensionItem(
                    id = "prettier",
                    name = "Prettier - Code formatter",
                    description = "Opinionated code formatter using robust native prettier layouts.",
                    author = "Prettier",
                    isInstalled = true,
                    downloads = "51.1M",
                    rating = 4.7
                ),
                ExtensionItem(
                    id = "python",
                    name = "Python",
                    description = "Rich IntelliSense, linting, debugging, code navigation for Python scripts.",
                    author = "Microsoft",
                    isInstalled = false,
                    downloads = "120M",
                    rating = 4.6
                ),
                ExtensionItem(
                    id = "gitlens",
                    name = "GitLens",
                    description = "Supercharge Git within Rakib Code Studio to visualize code authorship at a glance.",
                    author = "GitKraken",
                    isInstalled = false,
                    downloads = "29.3M",
                    rating = 4.9
                ),
                ExtensionItem(
                    id = "material-icons",
                    name = "Material Icon Theme",
                    description = "Material Design Icons for Visual Web projects and file explorer.",
                    author = "Philipp Kief",
                    isInstalled = true,
                    downloads = "22.5M",
                    rating = 4.8
                )
            )
            extensionDao.insertExtensions(list)
        }
    }

    private suspend fun preloadBaselineFiles(projectId: Long) {
        val files = listOf(
            ProjectFile(
                projectId = projectId,
                path = "index.html",
                name = "index.html",
                content = """<!DOCTYPE html>
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
</html>""",
                language = "html",
                isFolder = false,
                parentPath = ""
            ),
            ProjectFile(
                projectId = projectId,
                path = "css",
                name = "css",
                content = "",
                language = "",
                isFolder = true,
                parentPath = ""
            ),
            ProjectFile(
                projectId = projectId,
                path = "css/style.css",
                name = "style.css",
                content = """body {
    background: radial-gradient(circle, #0e111a 20%, #05060b 80%);
    color: #ffffff;
    font-family: 'Segoe UI', system-ui, sans-serif;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    margin: 0;
}

.container {
    text-align: center;
    background: rgba(255, 255, 255, 0.05);
    padding: 30px;
    border-radius: 16px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
    backdrop-filter: blur(8px);
}

h1 {
    font-size: 2.5rem;
    background: linear-gradient(45deg, #a855f7, #3b82f6);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    margin-bottom: 10px;
}

p {
    color: #94a3b8;
    font-size: 1.1rem;
    margin-bottom: 25px;
}

button {
    background: linear-gradient(135deg, #a855f7 0%, #3b82f6 100%);
    border: none;
    padding: 12px 30px;
    color: white;
    font-size: 1rem;
    font-weight: bold;
    border-radius: 8px;
    cursor: pointer;
    box-shadow: 0 4px 15px rgba(168, 85, 247, 0.4);
    transition: transform 0.2s, box-shadow 0.2s;
}

button:active {
    transform: scale(0.95);
    box-shadow: 0 2px 8px rgba(168, 85, 247, 0.4);
}

#status {
    margin-top: 20px;
    font-size: 0.9em;
    color: #64748b;
}""",
                language = "css",
                isFolder = false,
                parentPath = "css"
            ),
            ProjectFile(
                projectId = projectId,
                path = "js",
                name = "js",
                content = "",
                language = "",
                isFolder = true,
                parentPath = ""
            ),
            ProjectFile(
                projectId = projectId,
                path = "js/script.js",
                name = "script.js",
                content = """// Dynamic interactive script
document.addEventListener('DOMContentLoaded', () => {
    const btn = document.getElementById('btn');
    const status = document.getElementById('status');
    let count = 0;

    btn.addEventListener('click', () => {
        count++;
        status.textContent = `Clicked ${'$'}{count} times`;
        
        // Spawn some colorful floating elements (celebration particle trick!)
        const bubble = document.createElement('div');
        bubble.innerText = '✨';
        bubble.style.position = 'absolute';
        bubble.style.left = Math.random() * 80 + 10 + '%';
        bubble.style.top = '80%';
        bubble.style.fontSize = '24px';
        bubble.style.transition = 'all 1s ease-out';
        bubble.style.opacity = '1';
        document.body.appendChild(bubble);

        setTimeout(() => {
            bubble.style.top = '10%';
            bubble.style.opacity = '0';
        }, 50);

        setTimeout(() => {
            bubble.remove();
        }, 1100);
    });
});""",
                language = "javascript",
                isFolder = false,
                parentPath = "js"
            ),
            ProjectFile(
                projectId = projectId,
                path = "package.json",
                name = "package.json",
                content = """{
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
}""",
                language = "json",
                isFolder = false,
                parentPath = ""
            ),
            ProjectFile(
                projectId = projectId,
                path = "README.md",
                name = "README.md",
                content = """# Rakib Code Studio IDE 🚀

Welcome to your visual cloud-integrated project workspace! This space functions exactly like lightweight desktop developer workflow.

## Included Features

1. **Syntax Highlighting**: HTML, CSS, JavaScript, Markdown, JSON, and more.
2. **Terminal Emulator**: Built-in shell context execution simulated environment.
3. **Extensions Panel**: Install and configure live development plugins.
4. **Interactive Local Server & Live Preview**: Split browser visualization.
5. **Smart AI Assistant**: Ask questions, refactor codes, and auto-complete logic directly.

*Developed specifically with Material Design 3 and Flutter/Compose visuals.*""",
                language = "markdown",
                isFolder = false,
                parentPath = ""
            )
        )
        for (f in files) {
            projectDao.insertFile(f)
        }
    }

    private fun getTemplateForLanguage(lang: String, filename: String): String {
        return when (lang) {
            "html" -> """<!DOCTYPE html>
<html>
<head>
    <title>$filename</title>
</head>
<body>
    <h1>New HTML Page</h1>
</body>
</html>"""
            "css" -> "/* Under style variables */\nbody {\n    background-color: #121212;\n    color: #ffffff;\n}"
            "javascript", "js" -> "// JavaScript for $filename\nconsole.log('Running scripts!');"
            "python" -> "# Python source file\ndef main():\n    print(\"Hello from $filename!\")\n\nif __name__ == '__main__':\n    main()"
            "java" -> "public class ${filename.substringBeforeLast(".")} {\n    public static void main(String[] args) {\n        System.out.println(\"Hello World\");\n    }\n}"
            "cpp" -> "#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << \"Hello from C++!\" << endl;\n    return 0;\n}"
            "json" -> "{\n  \"name\": \"$filename\",\n  \"active\": true\n}"
            "markdown" -> "# $filename\nWrite your document guidelines here."
            else -> "// Text file content for $filename"
        }
    }
}
