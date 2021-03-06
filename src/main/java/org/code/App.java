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

	public static String generateKey() {
		return "";
	}

	//	@Override
	//	public void init() {


	public static void main(String[] args) {
		JedisPool pool = new JedisPool(new JedisPoolConfig(), "localhost");
		Jedis jedis = pool.getResource();

		port(8080);
		staticFileLocation("public");

		//stop();

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

		get("/blog/users/", (request, response) -> {
			logger.info(String.format("request of user list into REDIS_BLOG: %s", request.body().toString()));

			String users = jedis.smembers("users").stream().collect(Collectors.joining(", "));
			
			response.body(String.format("user list: [%s]: %s", jedis.smembers("users").size(), users));
			response.type(MediaType.TEXT_PLAIN);
			response.status(Status.OK.getStatusCode());

			return response.body();
		});

		// e.g. /blog/user/new?username=adam&password=eden
		get("/blog/user/new", (request, response) -> {
			logger.info(String.format("request of insert user into REDIS_BLOG: %s", request.body().toString()));

			String username = request.queryParams("username");
			String password = request.queryParams("password");

			if(jedis.sadd("users", username) > 0) {
				if(username != null && password != null) {

					logger.info(String.format("Incr: %s", String.valueOf(jedis.incr("keys"))));
					String key = "blog:users:" + username;
					logger.info(String.format("%s:%s:%s", key, username, password));

					jedis.hset(key, "username", username);
					jedis.hset(key, "password", password);
					
					request.session().attribute("login", username);

					response.body(String.format("created and logged [%s]: %s", key, jedis.hget(key, "username")));
					response.type(MediaType.TEXT_PLAIN);
					response.status(Status.CREATED.getStatusCode());

				} else {
					response.body(String.format("Your URL, Sir, is damn wrong! This %s is what you gave me?", request.params(":url")));
					response.type(MediaType.TEXT_PLAIN);
					response.status(Status.NOT_ACCEPTABLE.getStatusCode());
				}
			} else {
				response.body(String.format("Your username, Sir, is existing! This %s is what you gave me?", username));
				response.type(MediaType.TEXT_PLAIN);
				response.status(Status.NOT_ACCEPTABLE.getStatusCode());
			}
			return response.body();
		});
		
		// e.g. /blog/user/login?username=adam&password=eden
		get("/blog/user/login", (request, response) -> {
			logger.info(String.format("request of login user into REDIS_BLOG: %s", request.body().toString()));
			
			String username = request.queryParams("username");
			String password = request.queryParams("password");
			
				if(username != null && password != null) {
					
					String key = "blog:users:" + username;
					logger.info(String.format("%s:%s", username, password));
					
					if(jedis.hget(key, "password") != null && jedis.hget(key, "password").equals(password)) {
						
						response.body(String.format("login! [%s]: %s", key, username));
						request.session().attribute("login", username);
						
						response.type(MediaType.TEXT_PLAIN);
						response.status(Status.OK.getStatusCode());
					} else {
						
						response.body(String.format("Your password, Sir, is damn wrong! This %s is what you gave me?", password));
						response.type(MediaType.TEXT_PLAIN);
						response.status(Status.UNAUTHORIZED.getStatusCode());
					}
					
				} else {
					response.body(String.format("Your username, Sir, is damn wrong! This %s is what you gave me?", username));
					response.type(MediaType.TEXT_PLAIN);
					response.status(Status.NOT_ACCEPTABLE.getStatusCode());
				}

			return response.body();
		});
		
		get("/blog/post/new", (request, response) -> {
			logger.info(String.format("add a new URL to REDIS: %s", request.params(":url")));
			logger.info(request.session().attribute("login"));
			response.body("<html>"
					+ "<head><title>Redis - Add url</title></head>"
					+ "<body>"
					+ "<form id=\"add_post\" action=\"/blog/post/new\" method=\"POST\">"
					+ "<p><label>subject</label><input type=\"text\" name=\"subject\"/></p>"
					+ "<p><label>post</label><textarea name=\"post\"></textarea></p>"
					+ "<p><input type=submit value=\"post me!\"></input></p>"
					+ "</form></body></html>");
			response.type(MediaType.TEXT_HTML);
			response.status((Status.OK.getStatusCode()));
			return response.body();
		});		
		
		post("/blog/post/new", (request, response) -> {
			logger.info(String.format("add a new post to REDIS: %s", request.queryParams("post")));

				String subject = request.queryParams("subject");
				String post = request.queryParams("post");
				String login = request.session().attribute("login");
				
				String postKey = "blog:users:" + login + ":posts:" + jedis.incr("postsIndex").toString();
				String userKey = "blog:users:" + login;
				
				logger.info(post);
				logger.info(subject);
				logger.info(login);
				logger.info(postKey);
				logger.info(userKey);
				
				jedis.hset(postKey, "author", userKey);
				jedis.hset(postKey, "subject", subject);
				jedis.hset(postKey, "content", post);
				jedis.rpush(userKey, postKey);

				response.body(String.format("Your post, Sir %s, is here <a href=/blog/post/%s>/blog/post/%s</a>", login, postKey, postKey));
				response.type(MediaType.TEXT_HTML);
				response.status(Status.CREATED.getStatusCode());

			return response.body();
		});
		
		get("/blog/post/:key", (request, response) -> {

			if(jedis.hget(request.params(":key"), "content") != null) {
				response.body(jedis.hget(request.params(":key"), "content"));
				response.status((Status.OK.getStatusCode()));
			} else {
				response.status((Status.NOT_FOUND.getStatusCode()));
			}
			return response.body();

		});
		

		get("/blog/user/posts/", (request, response) -> {
			logger.info(String.format("request of user list into REDIS_BLOG: %s", request.body().toString()));
			// ...
			response.type(MediaType.TEXT_PLAIN);
			response.status(Status.OK.getStatusCode());

			return response.body();
		});
	}
}

/**
 * 
 * http://localhost:8080/blog/user/new?username=ushhausa&password=eden
 * http://localhost:8080/blog/user/login?username=usausa&password=eden
 * http://localhost:8080/blog/users/
 * 
 */
