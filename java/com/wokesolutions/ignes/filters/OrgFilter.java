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
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.Secrets;
import com.wokesolutions.ignes.util.UserLevel;

public class OrgFilter implements Filter {
	
	public static final Logger LOG = Logger.getLogger(OrgFilter.class.getName());

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
			
			LOG.info("hlvflyvl");
			
			if(token == null)
				throw new Exception();
			
			LOG.info("hlvflyvl");
			
			verifier.verify(token);
			
			LOG.info("hlvflyvl");
			
			String nif = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();
			
			LOG.info("hlvflyvl");
			req.setAttribute(CustomHeader.USERNAME_ATT, nif);
			
			LOG.info("hlvflyvl");
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
