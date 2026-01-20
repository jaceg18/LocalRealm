#Changelog
All notable changes in this project will be discussed here.
This will not include beta / early SNAPSHOT version changes.

The format is based on [Keep a Changelog](https://keepachangelog.com),
and this project adheres to Semantic Versioning.

## [1.1-SNAPSHOT] - 2026-01-14
### Added
 - Settings tab menu
 - Editable server files within the UI
 - Save function for modified files
### Fixed
  - The Controller is no longer a 'God Class', while I plan to further optimize, it's functionality was split up between 3 classes.
  - Improved server shutdown procedures
  - An issue with expanding children files in the settings table view.
  - Optimized imports.


## [1.2-SNAPSHOT] - 2026-01-15
### Added
  - Table can now view and allow modifications for .yml, .properties, .json, and .txt files. 
  - Added a open button, which will open selected file on default app.
  - You can now drag and drop files into the UI by hovering the file over the selected parent.
  - Added a text field where console inputs can be sent, to avoid console outputs during command sending
  - Some refactored code for speed and safety.
  - Added build options path, where users can add their own direct downloads links to different paper/spigot/forge versions.
### Fixed
  - The single paper version only download.
  - A bug where clicking a non-supported file would throw a unhandled exception.
  - An issue where no gui would show even if the option was un-selected.
  - Some annoying element spacing in the management tab

## [1.2.1-SNAPSHOT] - 2026-01-16
### Added
   - External Join, Automatically configure UPnP port forwarding to allow external players to join your server. 
   - Includes automatic router discovery, port mapping, public IP detection, and VPN fallback recommendations when direct join isn't possible.
### Fixed
   - Versioning label in UI title label
   - Some other redundant stuff

## [1.3-SNAPSHOT] - 2026-01-20
### Added
   - Live server stats for Windows and Unix.
   - Multiple controller classes to free the main controller some load.
   - Cleaner UI that support's full screen and window resizing.
### Fixed
   - The main controller's junk door resemblance.
   - Stat tracking speed and errors
   - Console input text field's tiny size.
