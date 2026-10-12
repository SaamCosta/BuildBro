package io.github.saamcosta.buildbro.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.saamcosta.buildbro.BuildBro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP "streamable HTTP" transport, reduced to what Claude Code needs: JSON responses to POSTs.
 * Locked down so that only the process we start can use it:
 * loopback only, random port, a fresh bearer token per server, and no browser requests
 * (any Origin header is refused, and the Host header must be the loopback address).
 */
public final class McpHttpServer implements AutoCloseable {
	public static final String PATH = "/mcp";
	private static final int MAX_BODY = 1 << 20;

	private final McpProtocol<?> protocol;
	private final byte[] expectedAuthorization;
	private final String token;
	private final HttpServer server;
	private final ExecutorService executor;

	private McpHttpServer(McpProtocol<?> protocol) throws IOException {
		this.protocol = protocol;
		this.token = newToken();
		this.expectedAuthorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
		this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16);
		this.executor = Executors.newFixedThreadPool(4, runnable -> {
			Thread thread = new Thread(runnable, "BuildBro MCP");
			thread.setDaemon(true);
			return thread;
		});
		server.setExecutor(executor);
		server.createContext(PATH, this::handle);
	}

	public static McpHttpServer start(McpProtocol<?> protocol) throws IOException {
		McpHttpServer server = new McpHttpServer(protocol);
		server.server.start();
		return server;
	}

	public int port() {
		return server.getAddress().getPort();
	}

	public String url() {
		return "http://127.0.0.1:" + port() + PATH;
	}

	/** Secret shared only with the Claude Code process we start. Never log it. */
	public String token() {
		return token;
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (exchange.getRequestHeaders().containsKey("Origin") || !isLoopbackHost(exchange.getRequestHeaders().getFirst("Host"))) {
				send(exchange, 403, null);
				return;
			}
			if (!isAuthorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
				send(exchange, 401, null);
				return;
			}
			if (!PATH.equals(exchange.getRequestURI().getPath())) {
				send(exchange, 404, null);
				return;
			}
			if (!"POST".equals(exchange.getRequestMethod())) {
				exchange.getResponseHeaders().add("Allow", "POST");
				send(exchange, 405, null);
				return;
			}
			byte[] body = readBody(exchange.getRequestBody());
			if (body == null) {
				send(exchange, 413, null);
				return;
			}
			JsonElement message;
			try {
				message = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			} catch (JsonParseException e) {
				send(exchange, 400, McpProtocol.error(null, -32700, "Parse error"));
				return;
			}
			JsonObject response = protocol.handle(message);
			if (response == null) {
				send(exchange, 202, null);
			} else {
				send(exchange, 200, response);
			}
		} catch (RuntimeException e) {
			BuildBro.LOGGER.error("MCP request failed", e);
		}
	}

	private boolean isAuthorized(String header) {
		return header != null && MessageDigest.isEqual(expectedAuthorization, header.getBytes(StandardCharsets.UTF_8));
	}

	private boolean isLoopbackHost(String host) {
		int port = port();
		return host != null && (host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port));
	}

	private static byte[] readBody(InputStream in) throws IOException {
		byte[] body = in.readNBytes(MAX_BODY + 1);
		return body.length > MAX_BODY ? null : body;
	}

	private static void send(HttpExchange exchange, int status, JsonObject body) throws IOException {
		if (body == null) {
			exchange.sendResponseHeaders(status, -1);
			return;
		}
		byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static String newToken() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
