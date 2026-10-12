package io.github.saamcosta.buildbro.usage;

import io.github.saamcosta.buildbro.agent.Usage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * One line per request in {@code buildbro/usage.csv}, in the game folder.
 * Semicolon-separated with the system's decimal mark, so a double click opens it in columns in Excel or LibreOffice.
 */
public final class UsageLog {
	static final String HEADER = "date;time;model;detail;outcome;duration_s;turns;tool_calls;blocks_changed;"
			+ "input_tokens;cache_read_tokens;cache_write_tokens;output_tokens;cost_usd_api_price;request";
	private static final int COLUMNS = 15;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	private final Path file;

	public UsageLog(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	public synchronized void append(BuildReport report) throws IOException {
		Files.createDirectories(file.getParent());
		boolean isNew = !Files.exists(file) || Files.size(file) == 0;
		// A byte order mark lets Excel read the accents in the requests correctly.
		String line = (isNew ? "﻿" + HEADER + "\n" : "") + format(report) + "\n";
		Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	/** Totals for today and for the whole file. Unreadable lines are skipped. */
	public synchronized Summary summary(LocalDate today) throws IOException {
		Totals day = Totals.EMPTY;
		Totals all = Totals.EMPTY;
		if (!Files.exists(file)) {
			return new Summary(day, all);
		}
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		for (String line : lines) {
			String[] fields = line.split(";", COLUMNS);
			if (fields.length < COLUMNS || fields[0].contains("date")) {
				continue;
			}
			try {
				Totals row = new Totals(1,
						Long.parseLong(fields[9]) + Long.parseLong(fields[10]) + Long.parseLong(fields[11]),
						Long.parseLong(fields[12]),
						Double.parseDouble(fields[13].replace(',', '.')));
				all = all.plus(row);
				if (LocalDate.parse(fields[0]).equals(today)) {
					day = day.plus(row);
				}
			} catch (RuntimeException ignored) {
				// edited by hand or from an older format
			}
		}
		return new Summary(day, all);
	}

	static String format(BuildReport report) {
		Usage usage = report.result().usage();
		Locale locale = Locale.getDefault();
		return String.join(";",
				report.startedAt().toLocalDate().toString(),
				report.startedAt().format(TIME),
				usage != null && !usage.model().isEmpty() ? usage.model() : report.model(),
				report.detail().id(),
				report.result().outcome().name().toLowerCase(Locale.ROOT),
				String.format(locale, "%.1f", report.durationMs() / 1000.0),
				usage == null ? "" : Integer.toString(usage.turns()),
				Integer.toString(report.toolCalls()),
				Integer.toString(report.blocksChanged()),
				usage == null ? "0" : Long.toString(usage.inputTokens()),
				usage == null ? "0" : Long.toString(usage.cacheReadTokens()),
				usage == null ? "0" : Long.toString(usage.cacheWriteTokens()),
				usage == null ? "0" : Long.toString(usage.outputTokens()),
				String.format(locale, "%.4f", usage == null ? 0 : usage.costUsd()),
				text(report.request()));
	}

	/** Keeps the request on one line and stops a spreadsheet from running it as a formula. */
	static String text(String request) {
		String clean = request.replaceAll("[\\r\\n;]+", " ").replace("\"", "\"\"").strip();
		if (!clean.isEmpty() && "=+-@".indexOf(clean.charAt(0)) >= 0) {
			clean = "'" + clean;
		}
		return "\"" + clean + "\"";
	}

	public record Totals(int builds, long inputTokens, long outputTokens, double costUsd) {
		static final Totals EMPTY = new Totals(0, 0, 0, 0);

		Totals plus(Totals other) {
			return new Totals(builds + other.builds, inputTokens + other.inputTokens, outputTokens + other.outputTokens, costUsd + other.costUsd);
		}
	}

	public record Summary(Totals today, Totals all) {
	}
}
