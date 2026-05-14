package blusailtechnologies.guido.ticket.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import blusailtechnologies.guido.ticket.config.KnowledgeProperties;

@Component
public class SourceFileRepository {

	private static final Logger log = LoggerFactory.getLogger(SourceFileRepository.class);

	private static final List<String> PROJECTS = List.of(
			"JobRunner", "dataloader", "ecm", "prefattura", "ssetl", "wcf", "wcfauth");

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			".cs", ".ts", ".html", ".js", ".jsx", ".tsx", ".razor", ".cshtml",
			".css", ".scss",
			".json", ".xml", ".xsd", ".yml", ".yaml", ".config", ".properties",
			".sql", ".md", ".txt");

	private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
			"node_modules", "bin", "obj", "dist", "build", ".git", ".vs", ".idea",
			"packages", "target");

	private static final long MAX_FILE_BYTES = 1_500_000L;

	private final KnowledgeProperties props;
	private final Map<String, List<SourceFile>> byProject = new LinkedHashMap<>();

	public SourceFileRepository(KnowledgeProperties props) {
		this.props = props;
	}

	@PostConstruct
	void load() {
		if (props.sourcesDir() == null || props.sourcesDir().isBlank()) {
			log.warn("sources-dir non configurato, SourceFileRepository disabilitato");
			return;
		}
		Path base = Paths.get(props.sourcesDir());
		if (!Files.isDirectory(base)) {
			log.warn("Sources dir {} non esiste, salto il caricamento", base);
			return;
		}
		long totalBytes = 0;
		int totalFiles = 0;
		for (String project : PROJECTS) {
			Path projectDir = base.resolve(project);
			if (!Files.isDirectory(projectDir)) {
				log.info("Progetto {} assente, salto", project);
				continue;
			}
			List<SourceFile> files = new ArrayList<>();
			try (Stream<Path> walk = Files.walk(projectDir)) {
				walk
						.filter(Files::isRegularFile)
						.filter(p -> isAllowedPath(projectDir, p))
						.sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
						.forEach(p -> {
							try {
								long size = Files.size(p);
								if (size > MAX_FILE_BYTES) {
									return;
								}
								String content = Files.readString(p, StandardCharsets.UTF_8);
								String relative = projectDir.relativize(p).toString().replace('\\', '/');
								files.add(new SourceFile(project, relative, content));
							} catch (IOException e) {
								log.debug("Impossibile leggere {}: {}", p, e.getMessage());
							}
						});
			} catch (IOException e) {
				log.warn("Errore scansionando {}: {}", projectDir, e.getMessage());
			}
			long projectBytes = files.stream().mapToLong(f -> f.content().length()).sum();
			byProject.put(project, files);
			totalFiles += files.size();
			totalBytes += projectBytes;
			log.info("Caricato {}: {} file ({} KB)", project, files.size(), projectBytes / 1024);
		}
		log.info("SourceFileRepository pronto: {} file totali ({} MB)", totalFiles, totalBytes / (1024 * 1024));
	}

	public List<String> listProjects() {
		return List.copyOf(byProject.keySet());
	}

	public List<String> listFiles(String project, String pathFilter) {
		List<SourceFile> files = byProject.getOrDefault(project, List.of());
		String needle = pathFilter == null ? null : pathFilter.toLowerCase(Locale.ROOT);
		return files.stream()
				.map(SourceFile::relativePath)
				.filter(p -> needle == null || needle.isEmpty()
						|| p.toLowerCase(Locale.ROOT).contains(needle))
				.toList();
	}

	public Optional<SourceFile> read(String project, String relativePath) {
		List<SourceFile> files = byProject.getOrDefault(project, List.of());
		String target = relativePath.replace('\\', '/');
		return files.stream()
				.filter(f -> f.relativePath().equalsIgnoreCase(target))
				.findFirst();
	}

	public List<SourceHit> search(String pattern, String projectFilter, int maxHits, int snippetChars) {
		if (pattern == null || pattern.isBlank()) {
			return List.of();
		}
		String needle = pattern.toLowerCase(Locale.ROOT);
		List<SourceHit> hits = new ArrayList<>();
		for (Map.Entry<String, List<SourceFile>> e : byProject.entrySet()) {
			if (projectFilter != null && !projectFilter.isBlank()
					&& !e.getKey().equalsIgnoreCase(projectFilter)) {
				continue;
			}
			for (SourceFile f : e.getValue()) {
				String lower = f.content().toLowerCase(Locale.ROOT);
				int idx = lower.indexOf(needle);
				if (idx < 0) continue;
				int start = Math.max(0, idx - snippetChars / 3);
				int end = Math.min(f.content().length(), idx + needle.length() + (2 * snippetChars) / 3);
				String snippet = f.content().substring(start, end).replaceAll("\\s+", " ").trim();
				if (start > 0) snippet = "…" + snippet;
				if (end < f.content().length()) snippet = snippet + "…";
				hits.add(new SourceHit(f.project(), f.relativePath(), snippet));
				if (hits.size() >= maxHits) {
					return hits;
				}
			}
		}
		return hits;
	}

	private boolean isAllowedPath(Path root, Path file) {
		Path relative = root.relativize(file);
		for (Path part : relative) {
			if (SKIPPED_DIRECTORIES.contains(part.toString())) {
				return false;
			}
		}
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		if (dot < 0) return false;
		String ext = name.substring(dot);
		return ALLOWED_EXTENSIONS.contains(ext);
	}

	public record SourceHit(String project, String path, String snippet) {}
}
