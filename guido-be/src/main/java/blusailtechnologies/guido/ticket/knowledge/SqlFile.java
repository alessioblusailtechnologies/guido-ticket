package blusailtechnologies.guido.ticket.knowledge;

import java.nio.file.Path;

public record SqlFile(String name, Path path, String content) {
}
