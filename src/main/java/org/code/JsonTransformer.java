/**
 * 
 */
package org.code;

import spark.ResponseTransformer;
import com.google.gson.Gson;

/**
 * @author eddie
 *
 */
public class JsonTransformer implements ResponseTransformer {

	private Gson gson = new Gson();

	/* (non-Javadoc)
	 * @see spark.ResponseTransformer#render(java.lang.Object)
	 */
	@Override
	public String render(Object model) throws Exception {
		return gson.toJson(model);
	}

}
