package com.example.timer;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
	 * TimerManager: schedule appId-based tasks to run after a delay.
	 *
	 * Public static methods are intended to be invoked from Mule's Java module.
	 *
	 * - schedule(String appId, String payloadJson)  -> schedules with default 53s
	 * - cancel(String appId)        -> cancel scheduled task
	 * - shutdown()         -> gracefully stop the executor
	 *
	 * NOTE: business processing happens inside processApp().
	 *    Optionally processApp() can make an HTTP call back to a Mule flow (Variant B).
 */
public class TimerManager {
	
	private static final AtomicInteger threadCounter = new AtomicInteger(0);
	
	// pool with daemon threads so JVM can exit if only these running
	private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
		4,
		r -> {
			Thread t = new Thread(r, "TimerManager-worker-" + threadCounter.incrementAndGet());
			t.setDaemon(true);
			return t;
		}
	);
	
	// track scheduled futures and payloads by appId
	private static final ConcurrentHashMap<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, String> payloads = new ConcurrentHashMap<>();
	
	// default schedule entrypoint used by Mule (match signature: schedule(String,String))
	public static String schedule(String appId, String payloadJson) {
		return schedule(appId, payloadJson, 53L);
	}
	
	// overload if you want to pass different delay
	public static String schedule(String appId, String payloadJson, long delaySeconds) {
		if (appId == null || appId.trim().isEmpty()) {
			throw new IllegalArgumentException("appId is required");
		}
		
		// store payload
		payloads.put(appId, payloadJson);
		
		// cancel previous scheduled task for same appId (optional semantics)
		ScheduledFuture<?> previous = tasks.get(appId);
		if (previous != null && !previous.isDone()) {
			previous.cancel(false);
		}
		
		Runnable task = () -> {
			try {
				// This is the processing call AFTER the delay.
				processApp(appId, payloadJson);
				} catch (Exception ex) {
				ex.printStackTrace();
				} finally {
				// cleanup
				tasks.remove(appId);
				payloads.remove(appId);
			}
		};
		
		ScheduledFuture<?> future = executor.schedule(task, delaySeconds, TimeUnit.SECONDS);
		tasks.put(appId, future);
		
		return "scheduled";
	}
	
	/**
		 * Retrieves the JSON payload for a currently scheduled task.
		 * Throws an exception if the appId is missing.
		 *
		 * @param appId The unique identifier for the task.
		 * @return The JSON payload as a String, or null if no task is found.
		 * @throws IllegalArgumentException if appId is null or empty.
	 */
	public static String getPayload(String appId) {
		// 1. Validate the input parameter
		if (appId == null || appId.trim().isEmpty()) {
			throw new IllegalArgumentException("appId is required");
		}
		// 2. Proceed with the lookup if validation passes
		return payloads.get(appId);
	}
	
	// Cancel a scheduled job for an appId
	public static String cancel(String appId) {
		ScheduledFuture<?> f = tasks.remove(appId);
		payloads.remove(appId);
		if (f != null) {
			boolean cancelled = f.cancel(false);
			return cancelled ? "cancelled" : "not_cancelled";
		}
		return "not_found";
	}
	
	// Gracefully shutdown the executor (call from app shutdown if desired)
	public static void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
			} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
	
	/**
		 * processApp: put your processing logic here.
		 *
		 * Two example modes:
		 *  - Pure Java processing (preferred if you want no additional Mule listener)
		 *  - HTTP callback to a Mule flow (if you need the downstream logic inside Mule)
		 *
		 * This example shows a simple log + (optionally) an HTTP POST to /internal/process.
	 */
	public static void processApp(String appId, String payloadJson) {
		Instant now = Instant.now();
		System.out.println("TimerManager: processing appId=" + appId + " at " + now + " payload=" + payloadJson);
		
		// -------- OPTION A: do processing here in Java ----------
		// Put whatever business logic you need (DB calls, REST calls, transformation).
		// Example: just print/log. If you need to call an external service, do it here.
		
		// -------- OPTION B: (optional) call a Mule internal endpoint to trigger a Mule flow ----------
		// If you prefer the processing logic inside Mule, uncomment the call below and
		// expose a Mule HTTP listener on /internal/process (example provided in Mule config).
		//
		try {
			callInternalMuleEndpoint(appId, payloadJson);
			} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	// Optional helper: simple HTTP POST to Mule internal endpoint (if you want Variant B)
	public static void callInternalMuleEndpoint(String appId, String payloadJson) throws Exception {
		// Dynamic endpoint population using PropertyConfig, which loads 'config.properties'
		String endpoint = PropertyConfig.getMuleEndpointUrl(); 
		System.out.println("callInternalMuleEndpoint: Using dynamic endpoint: " + endpoint);
		
		URL url = new URL(endpoint + "?appid=" + java.net.URLEncoder.encode(appId, "UTF-8"));
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // --- DYNAMIC TIMEOUTS START ---
        // Default values: 5000ms for connection and 10000ms for read
        int connectTimeout = PropertyConfig.getIntProperty("http.connectTimeout", 5000);
        int readTimeout = PropertyConfig.getIntProperty("http.readTimeout", 10000);
		
		conn.setConnectTimeout(connectTimeout);
		conn.setReadTimeout(readTimeout);
        System.out.println("callInternalMuleEndpoint: Using Connect Timeout=" + connectTimeout + "ms, Read Timeout=" + readTimeout + "ms");
        // --- DYNAMIC TIMEOUTS END ---
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/json");
		byte[] out = payloadJson == null ? "{}".getBytes(StandardCharsets.UTF_8) : payloadJson.getBytes(StandardCharsets.UTF_8);
		try (OutputStream os = conn.getOutputStream()) {
			os.write(out);
		}
		int responseCode = conn.getResponseCode();
		System.out.println("callInternalMuleEndpoint responseCode=" + responseCode);
		conn.disconnect();
	}
}