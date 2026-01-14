package com.jaceg18.localrealm.core;

import java.nio.file.Path;

public record Server(String name, Path path) {@Override public String toString() {return name + " (" + path.getFileName() + ")";}
}
