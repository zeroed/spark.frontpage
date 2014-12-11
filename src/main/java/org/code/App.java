package org.code;

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.post;
import static spark.SparkBase.port;
import static spark.SparkBase.staticFileLocation;
import static spark.SparkBase.stop;

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.StringJoiner;

import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import com.google.gson.Gson;
import com.google.gson.JsonObject;



public class App { //implements SparkApplication {

	static final Logger logger = LogManager.getLogger(App.class.getName());
	static final int SECONDS_TO_LIVE = 60000;
	static final int KEY_LENGTH = 8;

	/**
	 * TODO: before closing... release and destroy!
	 * pool.returnResource(jedis);
	 * pool.destroy();
	 */

	public static String md5(String input) {
		String md5 = null;
		if(null == input) return null;
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			digest.update(input.getBytes(), 0, input.length());
			md5 = new BigInteger(1, digest.digest()).toString(16);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return md5;
	}

//	@Override
//	public void init() {
		

	public static void main(String[] args) {
		JedisPool pool = new JedisPool(new JedisPoolConfig(), "localhost");
		Jedis jedis = pool.getResource();

		port(8080);
		staticFileLocation("public");
		
		stop();
		
		before((request, response) -> {
			logger.info("adding header");
			response.header("Powered-By", "Redis");
		});

		get("/", (request, response) -> {
			logger.info("root requested");
			response.body("Hello Redis");
			response.status((Status.OK.getStatusCode()));
			response.type(MediaType.TEXT_PLAIN);
			return response.body();

		});

		get("/protected", (request, response) -> {
			logger.info("LOL, protected?");
			halt(403, "I don't think so!!!");
			return null;
		});

		// e.g. /redis?key=Foo&value=Bar
		get("/redis", (request, response) -> {
			logger.info(String.format("request of insert into REDIS: %s", request.splat().toString()));
			String key = request.queryParams("key");
			String value = request.queryParams("value");
			
			if(key != null && value != null) {
				jedis.set(key, value);
				jedis.expire(key, SECONDS_TO_LIVE);
	
				response.body(String.format("created %s: %s", key, jedis.get(key)));
				response.type(MediaType.TEXT_PLAIN);
				response.status(Status.CREATED.getStatusCode());
			} else {
				response.body(String.format("Your URL, Sir, is damn wrong! This %s is what you gave me?", request.params(":url")));
				response.type(MediaType.TEXT_PLAIN);
				response.status(Status.NOT_ACCEPTABLE.getStatusCode());
			}
			return response.body();
		});

		get("/redis/:key", (request, response) -> {
			logger.info(String.format("retrieve a key:value from REDIS: %s", request.params(":key")));
			
			if(jedis.get(request.params(":key")) != null) {
				JsonObject jsonObject = new JsonObject();
				jsonObject.addProperty(request.params(":key"), jedis.get(request.params(":key")));
				response.body(jsonObject.toString());
				response.status((Status.OK.getStatusCode()));
			} else {
				response.status((Status.NOT_FOUND.getStatusCode()));
			}
			return response.body();
			
		});
		
		get("/url/", (request, response) -> {
			logger.info(String.format("add a new URL to REDIS: %s", request.params(":url")));
			response.body("<html>"
					+ "<head><title>Redis - Add url</title></head>"
					+ "<body>"
					+ "<form id=\"add_url\" action=\"/url/new/\" method=\"POST\">"
					+ "<p><label>Url</label><input type=\"text\" name=\"url\"/></p>"
					+ "<p><input type=submit value=\"short me!\"></input></p>"
					+ "</form></body></html>");
			response.type(MediaType.TEXT_HTML);
			response.status((Status.OK.getStatusCode()));
			return response.body();
		});		
		
		post("/url/new/", (request, response) -> {
			logger.info(String.format("add a new URL to REDIS: %s", request.queryParams("url")));
			try {
				new URL(request.queryParams("url"));
				String shortened = md5(request.queryParams("url")).substring(0, KEY_LENGTH);
				
				jedis.set(shortened, request.queryParams("url"));
				jedis.expire(shortened, SECONDS_TO_LIVE);
				
				response.body(String.format("Your URL, Sir, is <a href=/url/go/%s>/url/go/%s</a>", shortened, shortened));
				response.type(MediaType.TEXT_HTML);
				response.status(Status.CREATED.getStatusCode());
				
			} catch (MalformedURLException malformedURLException) {
				response.body(String.format("Your URL, Sir, is damn wrong! %s", request.queryParams("url")));
				response.type(MediaType.TEXT_PLAIN);
				response.status(Status.NOT_ACCEPTABLE.getStatusCode());
			}
			return response.body();
		});

		get("/url/go/:key", (request, response) -> {
			logger.info(String.format("ready to redirect! %s", request.body()));
			if(jedis.get(request.params(":key")) != null) {
				response.redirect(jedis.get(request.params(":key")));
				response.status((Status.MOVED_PERMANENTLY.getStatusCode()));
			} else {
				response.status((Status.NOT_FOUND.getStatusCode()));
			}
			return response.body();
		});
	}
}
