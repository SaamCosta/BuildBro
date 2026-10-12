package io.github.saamcosta.buildbro.mcp;

import io.github.saamcosta.buildbro.tool.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpHttpServerTest {
	private static final String LIST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
	private static final String CRLF = "\r\n";

	private McpHttpServer server;

	@BeforeEach
	void start() throws IOException {
		server = McpHttpServer.start(new McpProtocol<>(new ToolRegistry<String>().register(new EchoTool()), "ctx", "test", tool -> {
		}));
	}

	@AfterEach
	void close() {
		server.close();
	}

	@Test
	void listensOnLoopbackOnly() {
		assertTrue(server.url().startsWith("http://127.0.0.1:"));
	}

	@Test
	void answersWithTheToken() throws IOException {
		HttpURLConnection connection = post(LIST, "Bearer " + server.token());
		assertEquals(200, connection.getResponseCode());
		try (InputStream in = connection.getInputStream()) {
			assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"echo\""));
		}
	}

	@Test
	void refusesMissingOrWrongToken() throws IOException {
		assertEquals(401, post(LIST, null).getResponseCode());
		assertEquals(401, post(LIST, "Bearer wrong").getResponseCode());
	}

	@Test
	void rawRequestWithTokenWorks() throws IOException {
		assertEquals(200, rawStatus("127.0.0.1:" + server.port(), null));
	}

	@Test
	void refusesBrowsers() throws IOException {
		// HttpURLConnection silently drops Origin, so this goes over a raw socket.
		assertEquals(403, rawStatus("127.0.0.1:" + server.port(), "http://evil.example"));
	}

	@Test
	void refusesForeignHostHeader() throws IOException {
		// What a DNS rebinding attack looks like: right address, someone else's host name.
		assertEquals(403, rawStatus("evil.example", null));
	}

	@Test
	void acceptsNotifications() throws IOException {
		assertEquals(202, post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", "Bearer " + server.token()).getResponseCode());
	}

	private int rawStatus(String host, String origin) throws IOException {
		StringBuilder request = new StringBuilder()
				.append("POST /mcp HTTP/1.1").append(CRLF)
				.append("Host: ").append(host).append(CRLF)
				.append("Authorization: Bearer ").append(server.token()).append(CRLF);
		if (origin != null) {
			request.append("Origin: ").append(origin).append(CRLF);
		}
		request.append("Content-Type: application/json").append(CRLF)
				.append("Content-Length: ").append(LIST.length()).append(CRLF)
				.append("Connection: close").append(CRLF)
				.append(CRLF)
				.append(LIST);
		try (Socket socket = new Socket("127.0.0.1", server.port())) {
			OutputStream out = socket.getOutputStream();
			out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
			out.flush();
			String statusLine = new String(socket.getInputStream().readNBytes(12), StandardCharsets.US_ASCII);
			return Integer.parseInt(statusLine.substring(9, 12));
		}
	}

	private HttpURLConnection post(String body, String authorization) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(server.url()).toURL().openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/json");
		if (authorization != null) {
			connection.setRequestProperty("Authorization", authorization);
		}
		try (OutputStream out = connection.getOutputStream()) {
			out.write(body.getBytes(StandardCharsets.UTF_8));
		}
		return connection;
	}
}
