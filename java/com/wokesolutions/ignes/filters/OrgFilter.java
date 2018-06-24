package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.KeyFactory;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.Secrets;
import com.wokesolutions.ignes.util.UserLevel;

public class OrgFilter implements Filter {
	
	public static final Logger LOG = Logger.getLogger(OrgFilter.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {
		
		LOG.info(this.getClass().getSimpleName() + Message.FILTER_VERIFYING + req.toString());
		
		try {
			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.ORG, UserLevel.ORG)
					.build();

			String token = ((HttpServletRequest) req).getHeader(CustomHeader.AUTHORIZATION);
			
			if(token == null)
				throw new Exception();
			
			verifier.verify(token);
			
			String nif = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();
			
			Entity org = datastore.get(KeyFactory.createKey(DSUtils.ORG, nif));
			
			if(!((boolean) org.getProperty(DSUtils.ORG_CONFIRMED)))
				throw new Exception();
			
			req.setAttribute(CustomHeader.USERNAME_ATT, nif);
			
			req.setAttribute(CustomHeader.LEVEL_ATT, JWTUtils.ORG);

			chain.doFilter(req, resp);
		} catch (Exception e){
			String responseToSend = Message.INVALID_TOKEN;
			((HttpServletResponse) resp).setHeader("Content-Type", "application/json");
			((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
			resp.getWriter().println(responseToSend);
			return;
		}
	}

	public void destroy() {}  
}
