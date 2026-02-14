#Changelog
All notable changes in this project will be discussed here.
This will not include beta / early SNAPSHOT version changes.

The format is based on [Keep a Changelog](https://keepachangelog.com),
and this project adheres to Semantic Versioning.

## [1.0] - 2026-02-14

### Added
- Settings tab menu
- Editable server files within the UI
- Save function for modified files
- Table can now view and allow modifications for .yml, .properties, .json, and .txt files.
- Added a open button, which will open selected file on default app.
- You can now drag and drop files into the UI by hovering the file over the selected parent.
- Added a text field where console inputs can be sent, to avoid console outputs during command sending
- Added build options path, where users can add their own direct downloads links to different paper/spigot/forge versions.
- External Join, Automatically configure UPnP port forwarding to allow external players to join your server.
- Includes automatic router discovery, port mapping, public IP detection, and VPN fallback recommendations when direct join isn't possible.
- Live server stats for Windows and Unix.
- Multiple controller classes to free the main controller some load.
- Cleaner UI that supports full screen and window resizing.
- Plugin Marketplace where users can browse and install plugins to your local realm server.
- Added one more version of paper to default build options.

### Changed
- The Controller is no longer a 'God Class'. Functionality was split between multiple classes.
- Refactored code for speed and safety.
- Improved server shutdown procedures.
- Optimized imports.
- Cleaned FXML by removing redundant property tags.
- Improved stat tracking speed and reliability.
- Improved UI layout spacing.

### Fixed
- The single paper version only download.
- A bug where clicking a non-supported file would throw an unhandled exception.
- An issue where no GUI would show even if the option was un-selected.
- An issue with expanding children files in the settings table view.
- Issue where plugin icons were being incorrectly sized.
- Versioning label in UI title label.
- Console input text field size.
- Miscellaneous redundant issues and cleanup.

### Marketplace Limitations
- Auto installs the latest version of the plugin only.
- The search query may not fully function.
- Occasional UI icon update issues.
- Experimental implementation, not stable enough for reliable use.

