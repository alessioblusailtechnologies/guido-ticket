package blusailtechnologies.guido.ticket.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public class SqlFileRepository {

	private final Path baseDir;

	public SqlFileRepository(String baseDir) {
		this.baseDir = Paths.get(baseDir);
	}

	public Path baseDir() {
		return baseDir;
	}

	public List<String> listNames(String nameFilter) {
		String needle = nameFilter == null ? null : nameFilter.trim().toLowerCase(Locale.ROOT);
		try (Stream<Path> stream = Files.list(baseDir)) {
			return stream
					.filter(Files::isRegularFile)
					.map(p -> stripExtension(p.getFileName().toString()))
					.filter(n -> needle == null || needle.isEmpty() || n.toLowerCase(Locale.ROOT).contains(needle))
					.sorted()
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Impossibile elencare i file in " + baseDir, e);
		}
	}

	public Optional<SqlFile> read(String name) {
		String normalized = name.endsWith(".sql") ? name : name + ".sql";
		Path candidate = baseDir.resolve(normalized);
		if (!Files.isRegularFile(candidate)) {
			Optional<Path> caseInsensitive = findCaseInsensitive(normalized);
			if (caseInsensitive.isEmpty()) {
				return Optional.empty();
			}
			candidate = caseInsensitive.get();
		}
		try {
			String content = Files.readString(candidate, StandardCharsets.UTF_8);
			return Optional.of(new SqlFile(stripExtension(candidate.getFileName().toString()), candidate, content));
		} catch (IOException e) {
			throw new UncheckedIOException("Impossibile leggere " + candidate, e);
		}
	}

	public List<SqlFile> readAll() {
		try (Stream<Path> stream = Files.list(baseDir)) {
			return stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
					.sorted(Comparator.comparing(Path::getFileName))
					.map(this::toSqlFile)
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Impossibile leggere i file in " + baseDir, e);
		}
	}

	private SqlFile toSqlFile(Path p) {
		try {
			return new SqlFile(stripExtension(p.getFileName().toString()), p, Files.readString(p, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("Impossibile leggere " + p, e);
		}
	}

	private Optional<Path> findCaseInsensitive(String filename) {
		try (Stream<Path> stream = Files.list(baseDir)) {
			return stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
					.findFirst();
		} catch (IOException e) {
			throw new UncheckedIOException("Impossibile cercare " + filename + " in " + baseDir, e);
		}
	}

	private static String stripExtension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? filename : filename.substring(0, dot);
	}
}
