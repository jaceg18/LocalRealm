# LocalRealm

A modern JavaFX application for building, managing, and running Minecraft servers locally.

**Version:** SNAPSHOT v1.0.1

## Features

- **Server Building**: Download and set up Minecraft server jars with a single click
- **Server Management**: Add, remove, and organize multiple server instances
- **Server Console**: Built-in console for interacting with running servers
- **Memory Configuration**: Configure min/max memory allocation per server
- **Auto EULA**: Automatically accept the Minecraft EULA during setup
- **Modern UI**: Clean, dark-themed interface built with JavaFX

## Requirements

- **Java 21** or higher
- **Maven** (for building from source)
- Internet connection (for downloading server jars)

## Installation

### Building from Source

1. Clone this repository:
   ```bash
   git clone <repository-url>
   cd LocalRealm
   ```

2. Build the project:
   ```bash
   mvn clean package
   ```

3. Run the application:
   ```bash
   mvn javafx:run
   ```

   Or use the Maven wrapper:
   ```bash
   ./mvnw javafx:run
   ```

### Running Pre-built JAR

If you have a pre-built JAR file, run it with:
```bash
java --module-path <path-to-javafx-libs> --add-modules javafx.controls,javafx.fxml -jar LocalRealm-1.0-SNAPSHOT.jar
```

## Usage

### Building a Server

1. Open the **Build Server** tab
2. Select a build type from the dropdown (currently supports Paper 1.21.8)
3. Configure memory allocation (optional, defaults to 2GB min, 4GB max)
4. Check "Auto EULA" if you want to automatically accept the Minecraft EULA
5. Click **Build Server** and select a folder where you want the server files
6. Wait for the download and setup to complete

### Managing Servers

1. Open the **Manage Servers** tab
2. View all saved servers in the list
3. Use **Add Server** to manually add an existing server folder
4. Use **Remove** to remove a server from the list
5. Click **Refresh** to reload the server list

### Running a Server

1. Go to the **Manage Servers** tab
2. Select a server from the list
3. Configure memory allocation (Min/Max in GB)
4. Click **Start** to launch the server
5. Use the server console to interact with the running server
6. Type commands in the console and press Enter
7. Click **Stop** to gracefully shut down the server

## Project Structure

```
LocalRealm/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/jaceg18/localrealm/
│   │   │       ├── App.java              # Main application entry point
│   │   │       ├── Controller.java       # UI controller and business logic
│   │   │       ├── core/
│   │   │       │   ├── Server.java       # Server data model
│   │   │       │   ├── ServerManager.java # Server persistence
│   │   │       │   └── Build/
│   │   │       │       └── Util.java     # Build utilities
│   │   │       └── annotation/
│   │   │           └── Provisional.java # Annotation for provisional features
│   │   └── resources/
│   │       ├── view.fxml                 # UI layout
│   │       └── theme.css                 # Application styling
│   └── test/                             # (Tests to be added)
└── pom.xml                                # Maven configuration
```

## Known Limitations

- **Hardcoded Build Options**: Currently only supports Paper 1.21.8. More build options will be configurable in future versions.
- **Server Detection**: The "Add Server" feature currently only looks for `server.jar` files. This will be improved in v1.2.0.
- **Provisional Features**: Some features are marked as provisional and may change in future releases.

## Planned Features

- Version management (add, remove, select other build options)
- Plugin and mod-list management
- Live server stats (RAM usage, CPU usage, player count)
- Server properties and file editing within the UI
- Server restart button
- Periodic backups of selectable files and data
- External server running (non-hidden)
- UI improvements and icons
- Social links, dev-log links, and donation links

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

**Jace Grant**

## Disclaimer

This software is provided "as is" without warranty of any kind. Use at your own risk. This tool is not affiliated with Mojang Studios or Microsoft.

