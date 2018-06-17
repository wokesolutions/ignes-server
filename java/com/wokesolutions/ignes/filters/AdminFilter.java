package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.Secrets;
import com.wokesolutions.ignes.util.UserLevel;

public class AdminFilter implements Filter {

	public static final Logger LOG = Logger.getLogger(UserFilter.class.getName());

	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {  

		LOG.info(this.getClass().getSimpleName() + Message.FILTER_VERIFYING + req.toString());

		try {
			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.ADMIN, UserLevel.ADMIN)
					.build();

			String token = ((HttpServletRequest) req).getHeader(CustomHeader.AUTHORIZATION);
			verifier.verify(token);
			
			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();
			req.setAttribute(CustomHeader.USERNAME_ATT, username);
			req.setAttribute(CustomHeader.LEVEL_ATT, JWTUtils.ADMIN);

			chain.doFilter(req, resp);
		} catch (JWTVerificationException e){
			String responseToSend = Message.INVALID_TOKEN;
			((HttpServletResponse) resp).setHeader("Content-Type", CustomHeader.JSON_CHARSET_UTF8);
			((HttpServletResponse) resp).setStatus(Status.FORBIDDEN.getStatusCode());
			resp.getWriter().println(responseToSend);
			return;
		}
	}

	public void destroy() {}  

}  