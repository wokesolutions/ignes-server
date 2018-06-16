package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.Secrets;
import com.wokesolutions.ignes.util.UserLevel;

public class UserFilter implements Filter {

	public static final Logger LOG = Logger.getLogger(UserFilter.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {
		
		LOG.info(this.getClass().getSimpleName() + Message.FILTER_VERIFYING + req.toString());
		
		try {
			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL1, UserLevel.LEVEL1)
					.build();

			String token = ((HttpServletRequest) req).getHeader(CustomHeader.AUTHORIZATION);
			
			if(token == null)
				throw new Exception();
			
			verifier.verify(token);
			
			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();
			
			Query query = new Query(DSUtils.TOKEN)
					.setAncestor(KeyFactory.createKey(DSUtils.USER, username));
			
			List<Entity> allTokens = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
			
			boolean has = false;
			for(Entity oneToken : allTokens)
				if(oneToken.getProperty(DSUtils.TOKEN_STRING).equals(token)) {
					has = true;
					break;
				}
			
			if(!has) {
				throw new Exception();
			}
			
			req.setAttribute(CustomHeader.USERNAME_ATT, username);
			req.setAttribute(CustomHeader.LEVEL_ATT, JWTUtils.LEVEL1);

			chain.doFilter(req, resp);
		} catch (Exception e){
			byte[] responseToSend = Message.INVALID_TOKEN.getBytes();
			((HttpServletResponse) resp).setHeader("Content-Type", CustomHeader.JSON_CHARSET_UTF8);
			((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
			resp.getOutputStream().write(responseToSend);
			return;
		}
	}

	public void destroy() {}  

}  