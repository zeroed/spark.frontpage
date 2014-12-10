package org.code;

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.post;
import static spark.SparkBase.port;

import java.time.LocalDateTime;
import java.util.HashMap;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class App {
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

	@SuppressWarnings("serial")
	public static void main(String[] args) {

		JedisPool pool = new JedisPool(new JedisPoolConfig(), "localhost");
		Jedis jedis = pool.getResource();

		jedis.set("foo", "bar");
		String foobar = jedis.get("foo");
		assert foobar.equals("bar");

		pool.returnResource(jedis);
		pool.destroy();

		port(8080);

		before("/*", (request, response) -> {
			response.header("Foo", "Set by second before filter");
		});

		get("/", (request, response) -> {
			response.body("Hello Redis");
			response.header("Powerd-By", "Redis");
			// response.raw();
			// response.redirect("/example");
			response.status((Status.OK.getStatusCode()));
			response.type(MediaType.TEXT_PLAIN);
			return response;

		});

		get("/protected", (request, response) -> {
			halt(403, "I don't think so!!!");
			return null;
		});

		// e.g. /redis?key=Foo&value=Bar
		post("/redis/", (request, response) -> {

			String key = request.queryParams("key");
			String value = request.queryParams("value");

			jedis.set(key, value);
			jedis.expire(key, 10);
			String there = jedis.get(key);

			response.body(String.format("created %s: %s", key, there));
			response.type(MediaType.TEXT_PLAIN);
			response.status(Status.CREATED.getStatusCode());
			return response;
		});

		post("/redis/", (request, response) -> {
			// TODO: meh? implement me!
			return null;
		});

		get("/redis/:key", (request, response) -> {
			//TODO: un-uglify me!
			return (new HashMap<String,String>(){{
				put(request.params(":key"), jedis.get(request.params(":key")));
			}});
		}, new JsonTransformer());

			get("/test/info/", (request, response) -> {
				System.out.println(request.body());
				System.out.println(request.cookies().toString());
				System.out.println(String.valueOf(request.contentLength()));
				System.out.println(request.contentType());
				System.out.println(request.headers().toString());
				System.out.println(request.headers("BAR"));
				System.out.println(request.attributes().toString());
				System.out.println(request.attribute("foo"));
				System.out.println(request.host());
				System.out.println(request.ip());
				System.out.println(request.pathInfo());
				System.out.println(request.params("foo"));
				System.out.println(request.params());
				System.out.println(request.port());
				System.out.println(request.queryMap());
				System.out.println(request.queryMap("foo"));
				System.out.println(request.queryParams("FOO"));
				System.out.println(request.queryParams());
				System.out.println(request.raw());
				System.out.println(request.requestMethod());
				System.out.println(request.scheme());
				System.out.println(request.session());
				System.out.println(request.splat());
				System.out.println(request.url());
				System.out.println(request.userAgent());
				return String.format("%s", LocalDateTime.now());
			});
		get("/url/go/:key", (request, response) -> {
			logger.info(String.format("ready to redirect! %s", request));
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
