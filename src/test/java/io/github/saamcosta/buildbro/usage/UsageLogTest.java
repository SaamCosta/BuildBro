package io.github.saamcosta.buildbro.usage;

import io.github.saamcosta.buildbro.agent.AgentResult;
import io.github.saamcosta.buildbro.agent.DetailLevel;
import io.github.saamcosta.buildbro.agent.Usage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageLogTest {
	@TempDir
	Path dir;

	private static BuildReport report(LocalDateTime when, String request, Usage usage) {
		AgentResult result = AgentResult.done("ok").withUsage(usage);
		return new BuildReport(when, request, "sonnet", DetailLevel.DETAILED, result, 134_000, 18, 1234);
	}

	@Test
	void writesHeaderOnceAndOneLinePerBuild() throws IOException {
		UsageLog log = new UsageLog(dir.resolve("buildbro").resolve("usage.csv"));
		LocalDateTime now = LocalDateTime.of(2026, 10, 12, 15, 4, 5);
		log.append(report(now, "uma casa", new Usage("claude-sonnet-5-5", 100, 40_000, 3000, 2000, 0.42, 9)));
		log.append(report(now, "outra", null));

		List<String> lines = Files.readAllLines(log.file(), StandardCharsets.UTF_8);
		assertEquals(3, lines.size());
		assertTrue(lines.get(0).endsWith(UsageLog.HEADER));
		assertTrue(lines.get(1).startsWith("2026-10-12;15:04:05;claude-sonnet-5-5;detailed;done;"), lines.get(1));
		assertTrue(lines.get(1).endsWith(";\"uma casa\""), lines.get(1));
		assertTrue(lines.get(2).contains(";sonnet;"), "falls back to the model setting: " + lines.get(2));
	}

	@Test
	void summarisesTodayAndAllTime() throws IOException {
		UsageLog log = new UsageLog(dir.resolve("usage.csv"));
		Usage usage = new Usage("m", 100, 900, 0, 50, 0.25, 3);
		log.append(report(LocalDateTime.of(2026, 10, 11, 9, 0), "ontem", usage));
		log.append(report(LocalDateTime.of(2026, 10, 12, 9, 0), "hoje", usage));
		log.append(report(LocalDateTime.of(2026, 10, 12, 10, 0), "hoje de novo", usage));

		UsageLog.Summary summary = log.summary(LocalDate.of(2026, 10, 12));
		assertEquals(2, summary.today().builds());
		assertEquals(2000, summary.today().inputTokens());
		assertEquals(100, summary.today().outputTokens());
		assertEquals(0.5, summary.today().costUsd(), 1e-6);
		assertEquals(3, summary.all().builds());
		assertEquals(0.75, summary.all().costUsd(), 1e-6);
	}

	@Test
	void readsDecimalCommasToo() throws IOException {
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("pt-BR"));
			UsageLog log = new UsageLog(dir.resolve("usage.csv"));
			log.append(report(LocalDateTime.of(2026, 10, 12, 9, 0), "casa", new Usage("m", 1, 0, 0, 1, 1.5, 1)));
			assertTrue(Files.readString(log.file()).contains(";1,5000;"));
			assertEquals(1.5, log.summary(LocalDate.of(2026, 10, 12)).all().costUsd(), 1e-6);
		} finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	void keepsRequestsOnOneLineAndHarmlessInSpreadsheets() {
		assertEquals("\"a b c\"", UsageLog.text("a\nb;c"));
		assertEquals("\"diz \"\"oi\"\"\"", UsageLog.text("diz \"oi\""));
		assertEquals("\"'=HYPERLINK(1)\"", UsageLog.text("=HYPERLINK(1)"));
	}
}
