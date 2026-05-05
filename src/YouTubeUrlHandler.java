package com.mediascreen.client;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeUrlHandler {

	// Regex patterns for different YouTube URL formats
	private static final Pattern YT_WATCH_PATTERN = Pattern.compile(
		"(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})"
	);
	
	private static final Pattern YT_SHORT_PATTERN = Pattern.compile(
		"(?:https?://)?(?:www\\.)?youtu\\.be/([a-zA-Z0-9_-]{11})"
	);
	
	private static final Pattern YT_EMBED_PATTERN = Pattern.compile(
		"(?:https?://)?(?:www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})"
	);
	
	private static final Pattern YT_VIDEO_ID_PATTERN = Pattern.compile(
		"^([a-zA-Z0-9_-]{11})$"
	);

	/**
	 * Check if the given URL is a YouTube URL
	 */
	public static boolean isYouTubeUrl(String url) {
		if (url == null || url.trim().isEmpty()) return false;
		url = url.trim();
		return YT_WATCH_PATTERN.matcher(url).find() 
			|| YT_SHORT_PATTERN.matcher(url).find()
			|| YT_EMBED_PATTERN.matcher(url).find()
			|| YT_VIDEO_ID_PATTERN.matcher(url).find();
	}

	/**
	 * Extract video ID from various YouTube URL formats
	 */
	public static String extractVideoId(String url) {
		if (url == null || url.trim().isEmpty()) return null;
		url = url.trim();

		// Try watch URL format
		Matcher watchMatcher = YT_WATCH_PATTERN.matcher(url);
		if (watchMatcher.find()) {
			return watchMatcher.group(1);
		}

		// Try short URL format
		Matcher shortMatcher = YT_SHORT_PATTERN.matcher(url);
		if (shortMatcher.find()) {
			return shortMatcher.group(1);
		}

		// Try embed URL format
		Matcher embedMatcher = YT_EMBED_PATTERN.matcher(url);
		if (embedMatcher.find()) {
			return embedMatcher.group(1);
		}

		// Check if it's just the video ID
		Matcher idMatcher = YT_VIDEO_ID_PATTERN.matcher(url);
		if (idMatcher.find()) {
			return idMatcher.group(1);
		}

		return null;
	}

	/**
	 * Convert any YouTube URL to standard watch URL format
	 * Returns null if not a valid YouTube URL
	 */
	public static String normalizeYouTubeUrl(String url) {
		String videoId = extractVideoId(url);
		if (videoId == null) {
			return null;
		}
		return "https://www.youtube.com/watch?v=" + videoId;
	}

	/**
	 * Get a user-friendly title format for the YouTube URL
	 */
	public static String getYouTubeTitle(String url) {
		String videoId = extractVideoId(url);
		if (videoId != null) {
			return "YouTube: " + videoId;
		}
		return null;
	}
}
