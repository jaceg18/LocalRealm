# LocalRealm <img width="24" height="24" alt="localrealm" src="https://github.com/user-attachments/assets/1cec9124-4093-4e47-9350-b3b644322e32" />


A modern JavaFX application for building, managing, and running Minecraft servers locally.

**Version:** 1.3-SNAPSHOT

## Features

- **Server Building**: Download and set up Minecraft server jars with a single click
- **Server Management**: Add, remove, and organize multiple server instances
- **Server Console**: Built-in console for interacting with running servers
- **Memory Configuration**: Configure min/max memory allocation per server
- **Auto EULA**: Automatically accept the Minecraft EULA during setup
- **Modern UI**: Clean, dark-themed interface built with JavaFX
- **Server Stats**: Live server stats (RAM usage, CPU usage, player count)
- **File Modification**: Modify and add server files directly through UI.

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


## Known Limitations

- **Server Detection**: The "Add Server" feature currently only looks for `server.jar` files. This will be improved in v1.2.0.
- **Provisional Features**: Some features are marked as provisional and may change in future releases.

## Planned Features

- Periodic backups of selectable files and data
- External server running (non-hidden)
- UI improvements and icons

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

**Jace Grant**

## Disclaimer

This software is provided "as is" without warranty of any kind. Use at your own risk. This tool is not affiliated with Mojang Studios or Microsoft.

